import type { ReactNode } from "react";

import { resolveLocale } from "@/lib/i18n/locale";
import { LocaleSwitcher } from "@/lib/i18n/locale-switcher";

/**
 * Le sélecteur de langue doit exister ici aussi : la connexion est la première page vue,
 * et un utilisateur qui ne lit pas le français doit pouvoir changer de langue avant
 * d'avoir un compte — donc avant qu'aucune préférence ne puisse être enregistrée côté
 * serveur. Le cookie, lui, est posé dès ce moment.
 */
export default async function AuthLayout({ children }: { children: ReactNode }) {
  const locale = await resolveLocale();

  return (
    <div className="min-h-dvh">
      <div className="flex justify-end border-b border-neutral-800 p-2">
        <LocaleSwitcher locale={locale} />
      </div>
      {children}
    </div>
  );
}
