import type { MessageCatalog } from "./fr";

/**
 * Catalogue anglais.
 *
 * Le `satisfies` en fin de déclaration fait d'une clé manquante — ou d'une clé en trop —
 * une erreur de compilation, pas une chaîne absente découverte en production.
 * Les arguments ICU, eux, sont comparés catalogue par catalogue dans les tests : le type
 * ne peut pas vérifier que `{issue}` survit à la traduction.
 */
const en = {
  app: {
    title: "Kadran",
    description:
      "What a private-hire driver actually earns: margin per outing, cost per kilometre, " +
      "effective platform commission, income truly available after charges.",
  },
  nav: {
    label: "Main navigation",
    overview: "Overview",
    outings: "Outings",
    costs: "Costs",
    imports: "Imports",
    fiscal: "Tax",
    audit: "Audit log",
  },
  auth: {
    login: { title: "Sign in" },
    register: { title: "Sign up" },
  },
  screen: {
    notImplemented: "Route in place, screen not implemented yet — {issue}.",
  },
  localeSwitcher: {
    label: "Language",
    fr: "Français",
    en: "English",
  },
  completeness: {
    reliable: "Reliable",
    reliableHint: "The data covering this period is complete.",
    indicative: "Indicative",
    indicativeHint: "Partial data: read this as an order of magnitude, not as a measurement.",
    insufficient: "Insufficient",
    insufficientHint: "Too much data is missing to compute this metric.",
  },
  // « Borne inférieure » n'est pas « mesure » : `floor` reste un plancher en anglais aussi.
  // Traduire les deux par « recorded time » aplatirait la nuance que porte ADR-009.
  amplitude: {
    observed: "Measured span",
    observedHint: "The actual window of the outing, as recorded by Driversnote.",
    floor: "Minimum span",
    floorHint:
      "A lower bound: first ride to last ride. Waiting time outside rides is not counted, " +
      "so the real span is longer.",
    floorValue: "at least {duration}",
  },
} satisfies MessageCatalog;

export default en;
