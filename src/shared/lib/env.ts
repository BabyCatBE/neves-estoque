import { z } from "zod";

const publicEnvSchema = z.object({
  VITE_SUPABASE_URL: z.union([z.string().url(), z.literal("")]).optional(),
  VITE_SUPABASE_PUBLISHABLE_KEY: z.union([z.string().min(1), z.literal("")]).optional()
});

const parsed = publicEnvSchema.safeParse(import.meta.env);
if (!parsed.success) throw new Error("Variáveis públicas do ambiente DEV estão inválidas.");

export const publicEnv = parsed.data;
export const hasSupabaseConfig = Boolean(publicEnv.VITE_SUPABASE_URL && publicEnv.VITE_SUPABASE_PUBLISHABLE_KEY);
