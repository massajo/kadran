"use client";

import type { ReactNode } from "react";
import { IntlProvider } from "use-intl";

import type { AppLocale } from "./config";
import { intlConfigFor } from "./intl";

/**
 * Rend le catalogue disponible aux Client Components via `useTranslations`.
 *
 * `use-intl` est agnostique du framework et n'embarque pas de directive `"use client"` :
 * c'est ce fichier qui pose la frontière. Les Server Components, eux, n'ont pas besoin
 * du provider — ils passent par `getTranslations()` et `use-intl/core`.
 *
 * La langue est résolue côté serveur et descendue en prop : le client ne la redevine pas,
 * et serveur et client rendent donc rigoureusement le même HTML.
 */
export function AppIntlProvider({
  locale,
  children,
}: {
  locale: AppLocale;
  children: ReactNode;
}) {
  return <IntlProvider {...intlConfigFor(locale)}>{children}</IntlProvider>;
}
