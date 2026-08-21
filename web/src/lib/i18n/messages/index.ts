import type { AppLocale } from "../config";
import en from "./en";
import fr, { type MessageCatalog } from "./fr";

/**
 * Deux langues, deux catalogues, chargés en statique.
 *
 * Un `import()` dynamique par langue n'apporterait rien ici : les catalogues pèsent
 * quelques kilo-octets et l'application est authentifiée, donc rendue à la demande.
 * Le jour où le volume le justifiera, c'est cette fonction seule qui changera.
 */
export const CATALOGUES = { fr, en } satisfies Record<AppLocale, MessageCatalog>;

export function messagesFor(locale: AppLocale): MessageCatalog {
  return CATALOGUES[locale];
}
