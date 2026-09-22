import { spawn, spawnSync } from "node:child_process";

const npmCommand = process.platform === "win32" ? "npm.cmd" : "npm";

function runCaptured(command, args) {
  return spawnSync(command, args, {
    cwd: process.cwd(),
    encoding: "utf8",
    shell: false
  });
}

function outputOf(result) {
  return [result.stdout, result.stderr].filter(Boolean).join("\n").trim();
}

function extractUsefulError(output) {
  const lines = output
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);

  const patterns = [
    /error TS\d+:/i,
    /\berror\b.*react-hooks\//i,
    /\berror\b/i,
    /^FAIL\b/i,
    /failed/i
  ];

  for (const pattern of patterns) {
    const match = lines.find((line) => pattern.test(line));
    if (match) return match;
  }

  return lines.slice(-3).join(" | ") || "Falha sem mensagem de erro identificável.";
}

function fail(stage, result) {
  const diagnostic = extractUsefulError(outputOf(result));
  console.error("\nCOPIE E MANDE NO CHAT:");
  console.error(`NEVES_${stage}_FAIL | ${diagnostic}`);
  process.exit(result.status ?? 1);
}

console.log("\n[1/3] Atualizando o projeto com git pull...");
const pull = runCaptured("git", ["pull"]);
if (pull.status !== 0) fail("GIT_PULL", pull);
console.log("✓ Projeto atualizado.");

console.log("\n[2/3] Executando verificação completa...");
const check = runCaptured(npmCommand, ["run", "check"]);
if (check.status !== 0) fail("CHECK", check);
console.log("✓ Typecheck, lint, testes e build aprovados.");

console.log("\n[3/3] Iniciando o aplicativo local...");
console.log("✓ Tudo aprovado. Abrindo o Vite. Use Ctrl+C para encerrar.\n");

const dev = spawn(npmCommand, ["run", "dev"], {
  cwd: process.cwd(),
  stdio: "inherit",
  shell: false
});

dev.on("error", (error) => {
  console.error("\nCOPIE E MANDE NO CHAT:");
  console.error(`NEVES_DEV_FAIL | ${error.message}`);
  process.exit(1);
});

dev.on("exit", (code, signal) => {
  if (signal) {
    process.exit(0);
  }
  process.exit(code ?? 0);
});
