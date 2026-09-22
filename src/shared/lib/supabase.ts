import { createClient } from "@supabase/supabase-js";
import type { Database } from "../types/database.types";
import { hasSupabaseConfig, publicEnv } from "./env";

export const supabase = hasSupabaseConfig
  ? createClient<Database>(
      publicEnv.VITE_SUPABASE_URL!,
      publicEnv.VITE_SUPABASE_PUBLISHABLE_KEY!,
      {
        auth: {
          flowType: "pkce",
          persistSession: true,
          autoRefreshToken: true,
          detectSessionInUrl: true
        }
      }
    )
  : null;
