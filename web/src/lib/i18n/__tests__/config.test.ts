import { describe, expect, it } from "vitest";

import { DEFAULT_LOCALE, isAppLocale, negotiateLocale } from "@/lib/i18n/config";

describe("negotiateLocale", () => {
  it("falls back to French when no header is sent", () => {
    expect(negotiateLocale(null)).toBe("fr");
    expect(negotiateLocale("")).toBe(DEFAULT_LOCALE);
  });

  it("matches on the primary subtag, so en-GB counts as English", () => {
    expect(negotiateLocale("en-GB,en;q=0.9")).toBe("en");
    expect(negotiateLocale("fr-CA")).toBe("fr");
  });

  it("honours the quality ranking rather than the written order", () => {
    expect(negotiateLocale("en;q=0.3, fr;q=0.9")).toBe("fr");
    expect(negotiateLocale("fr;q=0.2, en;q=0.8")).toBe("en");
  });

  it("treats an absent quality as 1, as RFC 9110 requires", () => {
    expect(negotiateLocale("en, fr;q=0.9")).toBe("en");
  });

  it("skips languages the product does not carry", () => {
    expect(negotiateLocale("de-DE,de;q=0.9,en;q=0.5")).toBe("en");
    expect(negotiateLocale("de,es,it")).toBe(DEFAULT_LOCALE);
  });

  it("ignores a language explicitly refused with q=0", () => {
    expect(negotiateLocale("en;q=0, fr;q=0.1")).toBe("fr");
  });
});

describe("isAppLocale", () => {
  it("accepts the two supported locales and nothing else", () => {
    expect(isAppLocale("fr")).toBe(true);
    expect(isAppLocale("en")).toBe(true);
    expect(isAppLocale("de")).toBe(false);
    expect(isAppLocale(undefined)).toBe(false);
    expect(isAppLocale(42)).toBe(false);
  });
});
