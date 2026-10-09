import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./app/App";
import "./styles/index.css";
import { readBrowserTheme } from "./shared/theme/appearance";

// Aplica a preferência antes do primeiro render para evitar clarão do tema claro.
document.documentElement.dataset.nevesTheme = readBrowserTheme();

const root = document.getElementById("root");
if (!root) throw new Error("Elemento #root não encontrado.");

createRoot(root).render(
  <StrictMode>
    <App />
  </StrictMode>
);
