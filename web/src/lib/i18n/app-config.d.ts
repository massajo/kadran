import type { AppLocale, FormattingLocale } from "./config";
import type { MessageCatalog } from "./messages/fr";

/**
 * Augmentation de `use-intl` : la bibliothèque connaît ainsi la liste des langues et la
 * forme du catalogue.
 *
 * C'est la première des deux barrières contre une clé manquante — `t("nav.overwiew")`
 * ne compile pas. La seconde est le `onError` de `intl.ts`, qui lève à l'exécution : le
 * typage seul ne couvre pas une clé construite dynamiquement.
 */
declare module "use-intl" {
  interface AppConfig {
    // Les deux : l'identifiant interne pour les traductions, l'étiquette régionale
    // pour le formatage (`FORMATTING_LOCALES`). Aucune autre valeur ne compile.
    Locale: AppLocale | FormattingLocale;
    Messages: MessageCatalog;
  }
}
