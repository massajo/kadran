import { cookies, headers } from "next/headers";
import type { NamespaceKeys, NestedKeyOf } from "use-intl/core";

import { LOCALE_COOKIE, isAppLocale, negotiateLocale, type AppLocale } from "./config";
import { createAppFormatter, createAppTranslator } from "./intl";
import type { MessageCatalog } from "./messages/fr";

/**
 * ────────────────────────────────────────────────────────────────────────────────
 * Décision : la langue vit dans un cookie, **pas dans un segment d'URL** (KDN-132).
 * ────────────────────────────────────────────────────────────────────────────────
 *
 * L'alternative était `/fr/overview` · `/en/overview`, avec `src/app/[locale]/…`.
 * Elle est écartée pour trois raisons :
 *
 * 1. Le seul argument décisif en faveur des segments d'URL est le référencement — servir
 *    une page indexable par langue. Kadran est entièrement authentifié : aucune de ces
 *    pages n'est ni ne sera indexée. L'argument ne s'applique pas.
 *
 * 2. Un segment de langue double l'espace d'URL du produit. `/overview` et `/fr/overview`
 *    désignent le même écran, et tout ce qui référence une URL doit alors choisir :
 *    marque-pages, tickets de support, liens du journal d'audit (KDN-25), redirections
 *    d'authentification. Une URL canonique par écran est un actif qu'on ne récupère pas
 *    une fois perdu.
 *
 * 3. Le coût des segments n'est pas ponctuel mais récurrent : chaque `Link`, chaque
 *    `redirect`, chaque `middleware` de chaque écran à venir (KDN-19, KDN-25, KDN-41)
 *    doit porter la langue. C'est précisément la charge que la restructuration
 *    « pendant qu'il n'y a que huit fichiers » prétend éviter, et qu'elle installe.
 *
 * Le seul coût réel du cookie est que les pages deviennent rendues à la demande. Il est
 * nul ici : chaque page lit déjà la session et les données d'un tenant, donc aucune
 * n'était statique de toute façon.
 *
 * Le repli, lorsqu'aucun cookie n'est posé, est l'en-tête `Accept-Language`, puis le
 * français. La langue est donc résolue **côté serveur, avant le premier octet de HTML** :
 * pas de scintillement, et `<html lang>` est correct dès le rendu initial.
 */
export async function resolveLocale(): Promise<AppLocale> {
  const stored = (await cookies()).get(LOCALE_COOKIE)?.value;
  if (isAppLocale(stored)) return stored;

  return negotiateLocale((await headers()).get("accept-language"));
}

/** Traducteur d'un Server Component, langue résolue depuis la requête courante. */
export async function getTranslations<
  const Namespace extends NamespaceKeys<MessageCatalog, NestedKeyOf<MessageCatalog>> = never,
>(namespace?: Namespace) {
  return createAppTranslator(await resolveLocale(), namespace);
}

/** Formateur d'un Server Component, fuseau `Europe/Paris` épinglé. */
export async function getFormatter() {
  return createAppFormatter(await resolveLocale());
}
