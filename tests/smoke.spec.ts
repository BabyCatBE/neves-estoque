import { expect, test } from "@playwright/test";

test("carrega a estrutura inicial do Neves Estoque", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveTitle("Neves Estoque");
  await expect(page.getByRole("heading", { name: "Controle de estoque" })).toBeVisible();
});
