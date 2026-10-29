import { describe, expect, it } from "vitest";
import packageJson from "../../../package.json";
import { APP_ENVIRONMENT_LABEL, APP_VERSION } from "./appVersion";

describe("versão e ambiente exibidos na Web", () => {
  it("usa a versão oficial do package.json sem número duplicado no código", () => {
    expect(APP_VERSION).toBe(packageJson.version);
  });

  it("mostra PROD em build de produção e DEV no desenvolvimento", () => {
    expect(APP_ENVIRONMENT_LABEL).toBe(import.meta.env.PROD ? "PROD" : "DEV");
  });
});
