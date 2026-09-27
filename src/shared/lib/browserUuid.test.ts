import { describe, expect, it } from "vitest";
import { createBrowserUuid } from "./browserUuid";

describe("browserUuid", () => {
  it("usa randomUUID quando o navegador oferece a API", () => {
    const cryptoObject = {
      randomUUID: () => "33333333-3333-4333-8333-333333333333",
      getRandomValues: <T extends ArrayBufferView | null>(array: T) => array
    };

    expect(createBrowserUuid(cryptoObject)).toBe(
      "33333333-3333-4333-8333-333333333333"
    );
  });

  it("gera UUID v4 com getRandomValues em contexto HTTP/LAN", () => {
    const cryptoObject = {
      getRandomValues<T extends ArrayBufferView | null>(array: T) {
        const bytes = array as Uint8Array;
        bytes.fill(0xab);
        return array;
      }
    };

    expect(createBrowserUuid(cryptoObject)).toBe(
      "abababab-abab-4bab-abab-abababababab"
    );
  });
});
