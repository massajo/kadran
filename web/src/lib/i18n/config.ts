import { hasLocale } from "use-intl";

/**
 * Périmètre v1 : français et anglais. Le français est la langue par défaut, le produit
 * s'adressant à des chauffeurs VTC français (KDN-132).
 */
export const LOCALES = ["fr", "en"] as const;

export type AppLocale = (typeof LOCALES)[number];

/**
 * Étiquette régionale utilisée pour le **formatage** — et elle seule.
 *
 * `LOCALES` porte l'identifiant interne : valeur du cookie, clé de catalogue, résultat de
 * la négociation. Le formatage demande davantage de précision. `en` nu résout en usage
 * américain (`March 15, 2026`), alors que les anglophones de ce produit lisent des dates
 * françaises par ailleurs : `en-GB` rend `15 March 2026`, sans l'inversion jour/mois qui
 * fait douter d'un chiffre. La devise reste l'euro dans les deux cas (§BUSINESS_CURRENCY).
 *
 * Séparer les deux évite de casser la négociation, qui compare sur la sous-étiquette
 * primaire : `en-GB` dans `LOCALES` ne matcherait plus un `Accept-Language: en`.
 */
export const FORMATTING_LOCALES = {
  fr: "fr-FR",
  en: "en-GB",
} as const satisfies Record<AppLocale, string>;

/** Les seules étiquettes que `Intl` reçoit. Le typage interdit d'en inventer une autre. */
export type FormattingLocale = (typeof FORMATTING_LOCALES)[AppLocale];

export const DEFAULT_LOCALE: AppLocale = "fr";

/**
 * La langue vit dans un cookie, pas dans l'URL — voir la justification en tête de
 * `locale.ts`. Le cookie n'est pas `httpOnly` : il ne porte aucun secret et le client
 * doit pouvoir le lire pour éviter un aller-retour serveur inutile.
 */
export const LOCALE_COOKIE = "kadran-locale";

/** Un an : la langue est une préférence durable, pas une donnée de session. */
export const LOCALE_COOKIE_MAX_AGE = 60 * 60 * 24 * 365;

/**
 * Fuseau de la journée d'exploitation (ADR-006, seuil 04:00 `Europe/Paris`).
 *
 * Il est **épinglé** et ne se déduit ni de la langue ni du poste : `Intl.DateTimeFormat`
 * prendrait sinon le fuseau du navigateur, et un chauffeur en déplacement verrait ses
 * journées se décaler silencieusement d'un jour. Un anglophone à Londres et un
 * francophone à Paris doivent lire la même date pour la même sortie.
 */
export const BUSINESS_TIME_ZONE = "Europe/Paris";

/**
 * La devise est une propriété du métier, jamais de la langue : l'activité est facturée
 * en euros. La locale gouverne le *format* (séparateurs, position du symbole), jamais la
 * *devise*. Un `$` affiché à un utilisateur anglophone serait un chiffre faux.
 */
export const BUSINESS_CURRENCY = "EUR";

export function isAppLocale(candidate: unknown): candidate is AppLocale {
  return typeof candidate === "string" && hasLocale(LOCALES, candidate);
}

/**
 * Choisit la meilleure langue supportée à partir d'un en-tête `Accept-Language`.
 *
 * On compare sur la sous-étiquette primaire : `en-GB` et `en-US` valent `en`. Les
 * qualités `q` sont respectées, une valeur absente valant 1 comme le veut la RFC 9110.
 * Aucune correspondance ne renvoie jamais `null` : la langue par défaut est un repli sûr.
 */
export function negotiateLocale(acceptLanguage: string | null | undefined): AppLocale {
  if (!acceptLanguage) return DEFAULT_LOCALE;

  const ranked = acceptLanguage
    .split(",")
    .map((part, index) => {
      const [tag, ...parameters] = part.trim().split(";");
      const quality = parameters
        .map((parameter) => /^\s*q\s*=\s*([\d.]+)\s*$/.exec(parameter))
        .find((match) => match !== null);
      return {
        // La sous-étiquette primaire, en minuscules : `fr-CA` → `fr`.
        primary: tag.trim().toLowerCase().split("-")[0],
        quality: quality ? Number.parseFloat(quality[1]) : 1,
        // L'ordre d'apparition départage deux langues de qualité égale.
        index,
      };
    })
    .filter(({ quality }) => Number.isFinite(quality) && quality > 0)
    .sort((left, right) => right.quality - left.quality || left.index - right.index);

  return ranked.map(({ primary }) => primary).find(isAppLocale) ?? DEFAULT_LOCALE;
}
