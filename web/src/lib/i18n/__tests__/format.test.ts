import { afterEach, describe, expect, it } from "vitest";

import { BUSINESS_TIME_ZONE, LOCALES } from "@/lib/i18n/config";
import {
  formatBusinessDate,
  formatBusinessDateTime,
  formatMoneyFromCents,
  formatNumber,
} from "@/lib/i18n/format";

/**
 * Simule un poste réglé sur un autre fuseau. Node applique `process.env.TZ` aux
 * `Intl.*` construits ensuite, ce qui reproduit fidèlement le cas du chauffeur en
 * déplacement — ou du navigateur mal réglé.
 */
function withHostTimeZone<T>(timeZone: string, body: () => T): T {
  const previous = process.env.TZ;
  process.env.TZ = timeZone;
  try {
    return body();
  } finally {
    if (previous === undefined) {
      delete process.env.TZ;
    } else {
      process.env.TZ = previous;
    }
  }
}

const DATE_ONLY: Intl.DateTimeFormatOptions = { year: "numeric", month: "long", day: "numeric" };

afterEach(() => {
  delete process.env.TZ;
});

describe("formatMoneyFromCents", () => {
  it("stays in euros in every locale — the locale governs the format, never the currency", () => {
    for (const locale of LOCALES) {
      const formatted = formatMoneyFromCents(locale, 123_450);
      expect(formatted, locale).toContain("€");
      expect(formatted, locale).not.toContain("$");
    }
  });

  it("applies the separators and symbol position of each locale", () => {
    // Espaces insécables : on décrit la forme, on ne fige pas l'octet exact d'ICU.
    expect(formatMoneyFromCents("fr", 123_450)).toMatch(/^1\s234,50\s€$/u);
    expect(formatMoneyFromCents("en", 123_450)).toMatch(/^€1,234\.50$/u);
  });

  it("renders the two decimals of the cents, zeros included", () => {
    expect(formatMoneyFromCents("fr", 0)).toMatch(/^0,00\s€$/u);
    expect(formatMoneyFromCents("fr", 5)).toMatch(/^0,05\s€$/u);
    expect(formatMoneyFromCents("en", 100)).toBe("€1.00");
  });

  it("carries the sign of a negative amount — a loss is not an absolute value", () => {
    expect(formatMoneyFromCents("fr", -8_650)).toMatch(/^-86,50\s€$/u);
    expect(formatMoneyFromCents("en", -8_650)).toBe("-€86.50");
  });

  it("refuses a non-integer amount, because money travels in whole cents", () => {
    expect(() => formatMoneyFromCents("fr", 12.5)).toThrow(/centimes entiers/);
  });

  it("keeps large amounts exact — no rounding drift at the Intl boundary", () => {
    expect(formatMoneyFromCents("en", 123_456_789_012_345)).toBe("€1,234,567,890,123.45");
  });
});

describe("formatNumber", () => {
  it("applies the separators of each locale to a non-monetary value", () => {
    expect(formatNumber("fr", 1234.5)).toMatch(/^1\s234,5$/u);
    expect(formatNumber("en", 1234.5)).toBe("1,234.5");
  });

  it("carries formatting options through, such as a distance unit", () => {
    const options = { style: "unit", unit: "kilometer" } as const;
    expect(formatNumber("fr", 130.7, options)).toMatch(/130,7\s?km/u);
    expect(formatNumber("en", 130.7, options)).toMatch(/130\.7\s?km/u);
  });
});

describe("business dates", () => {
  // 15 mars 2026, 00:30 à Paris — donc encore le 14 sur la côte ouest américaine.
  const JUST_AFTER_MIDNIGHT_IN_PARIS = new Date("2026-03-14T23:30:00Z");
  // 14 mars 2026, 13:00 à Paris — mais déjà le 15 à Kiritimati (UTC+14).
  const MIDDAY_IN_PARIS = new Date("2026-03-14T12:00:00Z");

  it("proves the harness really moves the host time zone", () => {
    withHostTimeZone("America/Los_Angeles", () => {
      expect(new Intl.DateTimeFormat("fr").resolvedOptions().timeZone).toBe(
        "America/Los_Angeles",
      );
      // Sans fuseau épinglé, la même instant tombe la veille : c'est le défaut à empêcher.
      expect(
        new Intl.DateTimeFormat("fr", DATE_ONLY).format(JUST_AFTER_MIDNIGHT_IN_PARIS),
      ).toBe("14 mars 2026");
    });
  });

  it("stays on the Paris business day when the host is behind Paris", () => {
    withHostTimeZone("America/Los_Angeles", () => {
      expect(formatBusinessDate("fr", JUST_AFTER_MIDNIGHT_IN_PARIS)).toBe("15 mars 2026");
      expect(formatBusinessDate("en", JUST_AFTER_MIDNIGHT_IN_PARIS)).toBe("15 March 2026");
    });
  });

  it("stays on the Paris business day when the host is ahead of Paris", () => {
    withHostTimeZone("Pacific/Kiritimati", () => {
      expect(new Intl.DateTimeFormat("fr", DATE_ONLY).format(MIDDAY_IN_PARIS)).toBe(
        "15 mars 2026",
      );
      expect(formatBusinessDate("fr", MIDDAY_IN_PARIS)).toBe("14 mars 2026");
      expect(formatBusinessDate("en", MIDDAY_IN_PARIS)).toBe("14 March 2026");
    });
  });

  it("shows the Paris wall-clock time, not the host one", () => {
    withHostTimeZone("Pacific/Kiritimati", () => {
      expect(formatBusinessDateTime("fr", MIDDAY_IN_PARIS)).toMatch(/14 mars 2026.*13:00/u);
    });
  });

  it("pins Europe/Paris regardless of the language read", () => {
    for (const locale of LOCALES) {
      withHostTimeZone("America/Los_Angeles", () => {
        expect(formatBusinessDate(locale, JUST_AFTER_MIDNIGHT_IN_PARIS), locale).toMatch(
          /15/u,
        );
      });
    }
    expect(BUSINESS_TIME_ZONE).toBe("Europe/Paris");
  });
});
