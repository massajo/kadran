import {
  createFormatter,
  createTranslator,
  type IntlError,
  type NamespaceKeys,
  type NestedKeyOf,
} from "use-intl/core";

import { BUSINESS_TIME_ZONE, type AppLocale, FORMATTING_LOCALES } from "./config";
import { messagesFor } from "./messages";
import type { MessageCatalog } from "./messages/fr";

/**
 * Politique d'erreur : une clé absente **lève**, elle ne s'affiche pas.
 *
 * `use-intl` journalise l'erreur et rend `namespace.key` par défaut. Ce comportement est
 * refusé ici : afficher `nav.overview` à un chauffeur, c'est présenter un identifiant
 * comme un libellé — exactement ce que la règle 2.1 interdit pour les valeurs métier,
 * où une donnée absente ne se remplace jamais par un ersatz qui lui ressemble. Un écran
 * non traduit est un défaut de livraison : il doit tomber en test et en revue, pas en
 * production sous les yeux de l'utilisateur.
 */
export function failOnIntlError(error: IntlError): never {
  throw new Error(
    `Message i18n indisponible (${error.code}) : ${error.originalMessage ?? error.message}`,
  );
}

/**
 * Seconde barrière : même si `failOnIntlError` était un jour assoupli, aucun identifiant
 * de clé ne doit atteindre le rendu.
 */
export function failOnMessageFallback({
  key,
  namespace,
}: {
  key: string;
  namespace?: string;
}): never {
  throw new Error(
    `Clé i18n manquante : ${namespace ? `${namespace}.${key}` : key}. ` +
      "Compléter les deux catalogues avant de livrer l'écran.",
  );
}

/**
 * Réglages de formatage communs à tout le produit.
 *
 * Le fuseau y est épinglé une fois pour toutes (ADR-006) : aucun appelant n'a à y penser,
 * et aucun ne peut l'oublier.
 */
export function formattingConfigFor(locale: AppLocale) {
  return {
    // Étiquette régionale, pas l'identifiant interne : voir `FORMATTING_LOCALES`.
    locale: FORMATTING_LOCALES[locale],
    timeZone: BUSINESS_TIME_ZONE,
    onError: failOnIntlError,
  };
}

/** Configuration complète : formatage + catalogue + politique d'erreur. */
export function intlConfigFor(locale: AppLocale) {
  return {
    ...formattingConfigFor(locale),
    messages: messagesFor(locale),
    getMessageFallback: failOnMessageFallback,
  };
}

/**
 * Traducteur utilisable hors React — donc dans un Server Component, où les hooks de
 * `use-intl` ne sont pas disponibles.
 */
export function createAppTranslator<
  const Namespace extends NamespaceKeys<MessageCatalog, NestedKeyOf<MessageCatalog>> = never,
>(locale: AppLocale, namespace?: Namespace) {
  return createTranslator({ ...intlConfigFor(locale), namespace });
}

/** Formateur nombres/dates, fuseau épinglé. */
export function createAppFormatter(locale: AppLocale) {
  return createFormatter(formattingConfigFor(locale));
}
