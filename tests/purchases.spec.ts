import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    const state = window as unknown as { copiedText: string; sharedData: ShareData; shareMode: string };
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: {
      writeText: async (text: string) => { state.copiedText = text; }
    } });
    Object.defineProperty(navigator, "share", { configurable: true, value: async (data: ShareData) => {
      if (state.shareMode === "cancel") throw new DOMException("Cancelado", "AbortError");
      if (state.shareMode === "unavailable") throw new DOMException("Indisponível", "NotAllowedError");
      state.sharedData = data;
    } });
  });
  await page.goto("/tests/fixtures/purchases.html");
});

const orderText = "Lista de compras — Neves\n\nFarinha — 10 — KG\nAçúcar — 5,5 — KG";

test("digitação seleciona, Enter avança para produto desmarcado, copiar e compartilhar preservam o texto", async ({ page }) => {
  const flour = page.getByRole("textbox", { name: "Quantidade de Farinha" });
  const sugar = page.getByRole("textbox", { name: "Quantidade de Açúcar" });
  await expect(page.getByRole("checkbox", { name: "Selecionar Farinha" })).not.toBeChecked();
  await flour.fill("10");
  await expect(page.getByRole("checkbox", { name: "Selecionar Farinha" })).toBeChecked();
  await flour.press("Enter");
  await expect(sugar).toBeFocused();
  await expect(page.getByRole("checkbox", { name: "Selecionar Açúcar" })).not.toBeChecked();
  await sugar.fill("5,5");
  await page.getByRole("button", { name: "Copiar texto", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Texto copiado.");
  expect(await page.evaluate(() => (window as unknown as { copiedText: string }).copiedText)).toBe(orderText);
  await page.getByRole("button", { name: "Compartilhar texto", exact: true }).click();
  expect(await page.evaluate(() => (window as unknown as { sharedData: ShareData }).sharedData)).toEqual({ title: "Lista de compras — Neves", text: orderText });
});

test("limpar, zerar e valores inválidos desmarcam; checkbox e sugestão continuam manuais", async ({ page }) => {
  const flour = page.getByRole("textbox", { name: "Quantidade de Farinha" });
  const checkbox = page.getByRole("checkbox", { name: "Selecionar Farinha" });
  for (const invalid of ["", "0", "-1", "abc", "Infinity"]) {
    await flour.fill("2,5");
    await expect(checkbox).toBeChecked();
    await flour.fill(invalid);
    await expect(checkbox).not.toBeChecked();
  }
  await flour.fill("2,5");
  await checkbox.uncheck();
  await expect(flour).toHaveValue("2,5");
  await page.getByRole("checkbox", { name: "Selecionar Óleo" }).check();
  await expect(page.getByRole("textbox", { name: "Quantidade de Óleo" })).toHaveValue("3");
  await flour.fill("");
  await checkbox.check();
  await page.getByRole("button", { name: "Copiar texto", exact: true }).click();
  await expect(page.getByText("Informe uma quantidade maior que zero para todos os itens selecionados.")).toBeVisible();
  await flour.fill("1");
  await expect(flour).toHaveAttribute("aria-invalid", "false");
});

test("incrementar produto desmarcado seleciona e compartilhar usa cópia compatível ou respeita cancelamento", async ({ page }) => {
  await page.getByRole("button", { name: "Aumentar quantidade de Farinha" }).click();
  await expect(page.getByRole("checkbox", { name: "Selecionar Farinha" })).toBeChecked();
  await page.evaluate(() => { (window as unknown as { shareMode: string }).shareMode = "unavailable"; });
  await page.getByRole("button", { name: "Compartilhar texto", exact: true }).click();
  await expect(page.getByRole("status")).toHaveText("Compartilhamento indisponível · texto copiado.");
  expect(await page.evaluate(() => (window as unknown as { copiedText: string }).copiedText)).toContain("Farinha — 1 — KG");
  await page.evaluate(() => {
    const state = window as unknown as { shareMode: string; copiedText: string };
    state.shareMode = "cancel"; state.copiedText = "unchanged";
  });
  await page.getByRole("button", { name: "Compartilhar texto", exact: true }).click();
  await expect(page.getByRole("button", { name: "Compartilhar texto", exact: true })).toBeEnabled();
  expect(await page.evaluate(() => (window as unknown as { copiedText: string }).copiedText)).toBe("unchanged");
});
