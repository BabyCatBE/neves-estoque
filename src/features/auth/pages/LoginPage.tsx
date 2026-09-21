import { AppShell } from "../../../shared/components/AppShell";
import { hasSupabaseConfig } from "../../../shared/lib/env";

export function LoginPage() {
  return (
    <AppShell title="Acesso">
      <section className="mx-auto max-w-md rounded-2xl border border-zinc-200 bg-white p-6 shadow-sm">
        <h2 className="text-xl font-semibold">Entrar no Neves Estoque</h2>
        <p className="mt-2 text-sm leading-6 text-zinc-600">O V1 usará somente Google via Supabase Auth, com autorização interna obrigatória no banco.</p>
        <button type="button" disabled className="mt-6 w-full rounded-xl bg-zinc-200 px-4 py-3 text-sm font-semibold text-zinc-500">Entrar com Google</button>
        <p className="mt-3 text-xs text-zinc-500">{hasSupabaseConfig ? "Supabase DEV configurado; autenticação ainda será implementada." : "Supabase DEV ainda não foi configurado."}</p>
      </section>
    </AppShell>
  );
}
