import { describe, expect, it } from "vitest";

import { LOCALES } from "@/lib/i18n/config";
import { CATALOGUES } from "@/lib/i18n/messages";
import { createAppTranslator } from "@/lib/i18n/intl";

type Catalogue = Record<string, unknown>;

/** Aplatit un catalogue en `a.b.c` → message, pour comparer les langues clé à clé. */
function flatten(catalogue: Catalogue, prefix = ""): Map<string, string> {
  const flat = new Map<string, string>();

  for (const [key, value] of Object.entries(catalogue)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === "string") {
      flat.set(path, value);
    } else {
      for (const [nested, message] of flatten(value as Catalogue, path)) {
        flat.set(nested, message);
      }
    }
  }

  return flat;
}

/** Les arguments ICU d'un message : `« … — {issue}. »` → `["issue"]`. */
function icuArguments(message: string): string[] {
  return [...message.matchAll(/\{\s*(\w+)/g)].map(([, name]) => name).sort();
}

describe("message catalogues", () => {
  const flattened = new Map(LOCALES.map((locale) => [locale, flatten(CATALOGUES[locale])]));
  const reference = flattened.get("fr")!;

  it("carries a single source per locale, so no key is defined twice", () => {
    for (const locale of LOCALES) {
      const flat = flattened.get(locale)!;
      expect(flat.size).toBeGreaterThan(0);
      expect(new Set(flat.keys()).size).toBe(flat.size);
    }
  });

  it("defines exactly the same keys in every locale", () => {
    for (const locale of LOCALES) {
      expect([...flattened.get(locale)!.keys()].sort()).toEqual([...reference.keys()].sort());
    }
  });

  it("never leaves a message empty", () => {
    for (const locale of LOCALES) {
      for (const [key, message] of flattened.get(locale)!) {
        expect(message.trim(), `${locale}.${key}`).not.toBe("");
      }
    }
  });

  it("keeps the same ICU arguments across locales, so a translation cannot drop one", () => {
    for (const locale of LOCALES) {
      for (const [key, message] of flattened.get(locale)!) {
        expect(icuArguments(message), `${locale}.${key}`).toEqual(
          icuArguments(reference.get(key)!),
        );
      }
    }
  });
});

/**
 * ADR-009 : `Observed` est une mesure, `Floor` une borne inférieure. Traduire les deux
 * par le même mot commode — « durée », « recorded time » — effacerait la seule chose que
 * le type somme sert à porter. Le test verrouille la distinction dans les deux langues.
 */
describe("confidence wording", () => {
  it("keeps observed and floor spans distinct in both locales", () => {
    for (const locale of LOCALES) {
      const t = createAppTranslator(locale, "amplitude");
      expect(t("observed")).not.toBe(t("floor"));
      expect(t("observedHint")).not.toBe(t("floorHint"));
    }
  });

  it("states that a floor span is a lower bound, not a measurement", () => {
    const fr = createAppTranslator("fr", "amplitude");
    const en = createAppTranslator("en", "amplitude");

    expect(fr("floorHint")).toMatch(/borne inférieure/i);
    expect(en("floorHint")).toMatch(/lower bound/i);
    expect(fr("floorValue", { duration: "4 h 12" })).toMatch(/au moins/i);
    expect(en("floorValue", { duration: "4h 12m" })).toMatch(/at least/i);
  });

  it("keeps the three completeness levels distinct in both locales", () => {
    for (const locale of LOCALES) {
      const t = createAppTranslator(locale, "completeness");
      const levels = [t("reliable"), t("indicative"), t("insufficient")];
      expect(new Set(levels).size).toBe(3);
    }
  });
});
