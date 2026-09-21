import tailwindcss from "@tailwindcss/vite";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vite";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    tailwindcss(),
    VitePWA({
      registerType: "prompt",
      manifest: {
        name: "Neves Estoque",
        short_name: "Neves Estoque",
        display: "standalone",
        start_url: "/",
        background_color: "#ffffff",
        theme_color: "#b91c1c",
        lang: "pt-BR"
      }
    })
  ]
});
