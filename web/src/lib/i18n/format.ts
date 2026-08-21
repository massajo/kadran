import type { DateTimeFormatOptions, NumberFormatOptions } from "use-intl/core";

import { BUSINESS_CURRENCY, type AppLocale } from "./config";
import { createAppFormatter } from "./intl";

/**
 * Formate un montant **exprimé en centimes** (règle 2.2 : la monnaie ne circule qu'en
 * entiers de centimes, jamais en `Double`).
 *
 * La devise est fixée à l'euro, quelle que soit la langue : la locale gouverne le format
 * — séparateurs, position du symbole — et rien d'autre. `1 234,50 €` en français,
 * `€1,234.50` en anglais, jamais `$1,234.50`.
 *
 * La division par 100 se fait ici et nulle part ailleurs, au dernier moment, pour entrer
 * dans `Intl` qui n'accepte pas les centimes entiers. Aucun calcul n'est fait sur le
 * résultat : il part directement au rendu, et `Intl` arrondit à deux décimales.
 */
export function formatMoneyFromCents(locale: AppLocale, cents: number): string {
  if (!Number.isInteger(cents)) {
    throw new Error(`Montant non entier : ${cents}. Un montant s'exprime en centimes entiers.`);
  }

  return createAppFormatter(locale).number(cents / 100, {
    style: "currency",
    currency: BUSINESS_CURRENCY,
  });
}

/** Nombre non monétaire — kilomètres, ratios, compteurs. */
export function formatNumber(
  locale: AppLocale,
  value: number,
  options?: NumberFormatOptions,
): string {
  return createAppFormatter(locale).number(value, options);
}

const BUSINESS_DATE_FORMAT: DateTimeFormatOptions = {
  year: "numeric",
  month: "long",
  day: "numeric",
};

const BUSINESS_DATE_TIME_FORMAT: DateTimeFormatOptions = {
  ...BUSINESS_DATE_FORMAT,
  hour: "2-digit",
  minute: "2-digit",
};

/**
 * Date d'exploitation, **toujours lue dans `Europe/Paris`** (ADR-006).
 *
 * Une sortie close à 02:00 heure de Paris appartient à la journée d'exploitation de la
 * veille ; elle doit s'afficher au même jour pour tout le monde. Sans fuseau épinglé,
 * `Intl` prendrait celui du poste et un chauffeur en déplacement — ou simplement un
 * navigateur mal réglé — verrait ses journées glisser d'un cran sans le moindre signal.
 */
export function formatBusinessDate(locale: AppLocale, instant: Date | number): string {
  return createAppFormatter(locale).dateTime(instant, BUSINESS_DATE_FORMAT);
}

/** Horodatage d'exploitation, même fuseau épinglé que `formatBusinessDate`. */
export function formatBusinessDateTime(locale: AppLocale, instant: Date | number): string {
  return createAppFormatter(locale).dateTime(instant, BUSINESS_DATE_TIME_FORMAT);
}
