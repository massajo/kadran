import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { useTranslations } from "use-intl";

import { AppIntlProvider } from "@/lib/i18n/intl-provider";
import { createAppTranslator, failOnMessageFallback } from "@/lib/i18n/intl";

/**
 * Une clé absente doit **échouer bruyamment**. Le défaut de `use-intl` est de rendre
 * `namespace.key` après un `console.error` : un identifiant nu s'afficherait alors à
 * l'utilisateur, ce que ce dépôt refuse aussi bien pour un libellé que pour une valeur
 * métier (règle 2.1). Deux barrières le garantissent, et les deux sont vérifiées ici.
 */
describe("missing message key", () => {
  it("throws instead of returning the key path", () => {
    const t = createAppTranslator("fr", "nav");

    // @ts-expect-error — clé volontairement absente : c'est le comportement d'exécution
    // qu'on vérifie ici, le typage la refusant déjà à la compilation.
    expect(() => t("dashboard")).toThrow(/Message i18n indisponible \(MISSING_MESSAGE\)/);
  });

  it("throws in every locale, not only in the reference one", () => {
    for (const locale of ["fr", "en"] as const) {
      const t = createAppTranslator(locale);
      // @ts-expect-error — clé volontairement absente.
      expect(() => t("nav.platforms"), locale).toThrow(/MISSING_MESSAGE/);
    }
  });

  it("never renders a bare key even if the error handler were softened", () => {
    expect(() => failOnMessageFallback({ key: "overview", namespace: "nav" })).toThrow(
      /Clé i18n manquante : nav\.overview/,
    );
  });

  it("throws when an ICU argument is missing, rather than printing the placeholder", () => {
    const t = createAppTranslator("fr", "screen");

    // Les feuilles du catalogue sont typées `string`, donc le typage ne peut pas exiger
    // `{issue}` : c'est exactement pour ce trou que la barrière d'exécution existe.
    expect(() => t("notImplemented")).toThrow(/FORMATTING_ERROR/);
  });

  it("applies the same policy inside client components", () => {
    function BrokenLabel() {
      const t = useTranslations("nav");
      // @ts-expect-error — clé volontairement absente.
      return <span>{t("dashboard")}</span>;
    }

    // React journalise l'erreur de rendu : on tait la sortie pour garder le rapport lisible.
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});
    try {
      expect(() =>
        render(
          <AppIntlProvider locale="fr">
            <BrokenLabel />
          </AppIntlProvider>,
        ),
      ).toThrow(/MISSING_MESSAGE/);
    } finally {
      consoleError.mockRestore();
    }
  });
});
