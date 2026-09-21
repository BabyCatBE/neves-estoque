import { createClient } from "@supabase/supabase-js";
import { hasSupabaseConfig, publicEnv } from "./env";

export const supabase = hasSupabaseConfig
  ? createClient(publicEnv.VITE_SUPABASE_URL!, publicEnv.VITE_SUPABASE_PUBLISHABLE_KEY!)
  : null;
