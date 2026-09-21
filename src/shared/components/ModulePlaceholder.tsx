import { AppShell } from "./AppShell";

export function ModulePlaceholder({ title }: { title: string }) {
  return (
    <AppShell title={title} showBack>
      <section className="rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm">
        <span className="inline-flex rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-800">EM IMPLEMENTAÇÃO</span>
        <h2 className="mt-4 text-xl font-semibold">{title}</h2>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-zinc-600">A rota e a estrutura base deste módulo já existem. A regra funcional será implementada e validada no DEV.</p>
      </section>
    </AppShell>
  );
}
