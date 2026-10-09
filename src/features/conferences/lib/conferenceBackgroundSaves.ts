import { useSyncExternalStore } from "react";
import type { QueryClient } from "@tanstack/react-query";
import {
  createCategoryConference,
  findCategoryConferenceByIdempotencyKey,
  type CategoryConferenceWriteInput
} from "../api/conferences";
import {
  deleteOfflinePendingOperation,
  savePendingConference,
  type SavePendingConferenceMetadata
} from "../../offline/lib/pendingOperations";
import { getConferenceErrorMessage, localDateKey } from "./conferenceValidation";

/**
 * Envio expressamente iniciado pelo usuário, desacoplado da rota. Não é um serviço
 * de sync: a reconexão não dispara NENHUMA pendência. Em falha/aba encerrada a
 * cópia IndexedDB continua em Alertas > Pendências locais para revisão manual.
 */
export type ConferenceSavePhase = "saving" | "saved" | "failed";
export type ConferenceSaveStatus = {
  phase: ConferenceSavePhase;
  effectiveDate: string;
  message?: string;
};

type Staged = {
  input: CategoryConferenceWriteInput;
  localId: string;
  ownerId: string;
};

type Dependencies = {
  stage: (input: CategoryConferenceWriteInput, metadata: SavePendingConferenceMetadata) => Promise<string>;
  send: (input: CategoryConferenceWriteInput) => Promise<unknown>;
  reconcile: (idempotencyKey: string) => Promise<string | null>;
  remove: (localId: string) => Promise<void>;
};

const keyOf = (ownerId: string, categoryId: string) => ownerId + ":" + categoryId;

export class ConferenceBackgroundSaves {
  private state: Readonly<Record<string, ConferenceSaveStatus>> = {};
  private listeners = new Set<() => void>();
  private staged = new Map<string, Staged>();
  private preparing = new Set<string>();
  private sending = new Set<string>();
  private client: QueryClient | null = null;

  constructor(private readonly deps: Dependencies) {}

  subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => { this.listeners.delete(listener); };
  };
  getSnapshot = (): Readonly<Record<string, ConferenceSaveStatus>> => this.state;
  getServerSnapshot = (): Readonly<Record<string, ConferenceSaveStatus>> => this.state;

  status(ownerId: string, categoryId: string): ConferenceSaveStatus | undefined {
    return this.state[keyOf(ownerId, categoryId)];
  }

  isBlocked(ownerId: string, categoryId: string): boolean {
    const key = keyOf(ownerId, categoryId);
    const phase = this.state[key]?.phase;
    return this.preparing.has(key) || phase === "saving" || phase === "failed";
  }

  private setStatus(key: string, status: ConferenceSaveStatus | null) {
    const next = { ...this.state };
    if (status === null) delete next[key];
    else next[key] = status;
    this.state = next;
    for (const listener of this.listeners) listener();
  }

  async submit(
    input: CategoryConferenceWriteInput,
    metadata: SavePendingConferenceMetadata,
    queryClient: QueryClient
  ): Promise<void> {
    if (!metadata.authUserId || !metadata.deviceId) {
      throw new Error("Este acesso ainda não está pronto para guardar a Conferência.");
    }
    const key = keyOf(metadata.authUserId, input.categoryId);
    if (this.isBlocked(metadata.authUserId, input.categoryId)) {
      throw new Error("Já existe uma tentativa para esta categoria. Consulte seu status na lista.");
    }
    this.preparing.add(key);
    try {
      // Navegação somente APÓS o IndexedDB confirmar o registro protegido.
      const localId = await this.deps.stage(input, metadata);
      const staged: Staged = { input, localId, ownerId: metadata.authUserId };
      this.client = queryClient;
      this.staged.set(key, staged);
      this.setStatus(key, {
        phase: "saving",
        effectiveDate: localDateKey(input.effectiveAt)
      });
      void this.dispatch(key, staged);
    } finally {
      this.preparing.delete(key);
    }
  }

  retry(ownerId: string, categoryId: string): void {
    const key = keyOf(ownerId, categoryId);
    const staged = this.staged.get(key);
    if (!staged || staged.ownerId !== ownerId || this.state[key]?.phase !== "failed") return;
    this.setStatus(key, {
      phase: "saving",
      effectiveDate: localDateKey(staged.input.effectiveAt)
    });
    void this.dispatch(key, staged);
  }

  async discard(ownerId: string, categoryId: string): Promise<void> {
    const key = keyOf(ownerId, categoryId);
    const staged = this.staged.get(key);
    if (!staged || staged.ownerId !== ownerId || this.state[key]?.phase !== "failed") return;
    this.setStatus(key, {
      phase: "saving",
      effectiveDate: localDateKey(staged.input.effectiveAt),
      message: "Descartando cópia local…"
    });
    try {
      await this.deps.remove(staged.localId);
      this.staged.delete(key);
      this.setStatus(key, null);
    } catch (cause) {
      this.setStatus(key, {
        phase: "failed",
        effectiveDate: localDateKey(staged.input.effectiveAt),
        message: getConferenceErrorMessage(cause)
      });
      throw cause;
    }
  }

  private async dispatch(key: string, staged: Staged) {
    if (this.sending.has(key)) return;
    this.sending.add(key);
    try {
      let confirmed = false;
      try {
        await this.deps.send(staged.input);
        confirmed = true;
      } catch (cause) {
        // Erro ambíguo de rede: o banco pode ter confirmado a gravação.
        try {
          confirmed = Boolean(await this.deps.reconcile(staged.input.idempotencyKey));
        } catch {
          // Falha ao consultar também: preservar a tentativa até ação manual.
        }
        if (!confirmed) throw cause;
      }
      let cleanupWarning: string | undefined;
      try {
        await this.deps.remove(staged.localId);
      } catch {
        cleanupWarning = "Salva no servidor, mas a cópia local não pôde ser removida. Confira Alertas → Pendências locais.";
      }
      this.staged.delete(key);
      this.setStatus(key, {
        phase: "saved",
        effectiveDate: localDateKey(staged.input.effectiveAt),
        message: cleanupWarning
      });
      if (this.client) {
        void Promise.allSettled([
          this.client.invalidateQueries({ queryKey: ["conferences"] }),
          this.client.invalidateQueries({ queryKey: ["products"] }),
          this.client.invalidateQueries({ queryKey: ["purchases"] }),
          this.client.invalidateQueries({ queryKey: ["stock"] })
        ]);
      }
    } catch (cause) {
      // Nunca apagar a cópia e nunca reenviar só porque a conexão voltou.
      this.setStatus(key, {
        phase: "failed",
        effectiveDate: localDateKey(staged.input.effectiveAt),
        message: getConferenceErrorMessage(cause)
      });
    } finally {
      this.sending.delete(key);
    }
  }
}

export const conferenceBackgroundSaves = new ConferenceBackgroundSaves({
  stage: savePendingConference,
  send: createCategoryConference,
  reconcile: findCategoryConferenceByIdempotencyKey,
  remove: deleteOfflinePendingOperation
});

export function useConferenceSaveStatuses() {
  return useSyncExternalStore(
    conferenceBackgroundSaves.subscribe,
    conferenceBackgroundSaves.getSnapshot,
    conferenceBackgroundSaves.getServerSnapshot
  );
}
