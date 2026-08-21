import type { ReactNode } from "react";

import { getTranslations, resolveLocale } from "@/lib/i18n/locale";
import { LocaleSwitcher } from "@/lib/i18n/locale-switcher";

/**
 * Les URL ne portent pas la langue (voir la justification dans `lib/i18n/locale.ts`) :
 * `href` est le même dans les deux langues, seul `labelKey` change de rendu.
 */
const SCREENS = [
  { href: "/overview", labelKey: "overview" },
  { href: "/outings", labelKey: "outings" },
  { href: "/costs", labelKey: "costs" },
  { href: "/imports", labelKey: "imports" },
  { href: "/fiscal", labelKey: "fiscal" },
  { href: "/audit", labelKey: "audit" },
] as const;

export default async function DashboardLayout({ children }: { children: ReactNode }) {
  const t = await getTranslations("nav");
  const locale = await resolveLocale();

  return (
    <div className="min-h-dvh">
      {/* Le libellé de la navigation est lu par les lecteurs d'écran : il se traduit
          comme n'importe quel texte visible. */}
      <nav aria-label={t("label")} className="border-b border-neutral-800">
        <div className="flex flex-wrap items-center justify-between gap-2 p-2">
          <ul className="flex flex-wrap gap-1">
            {SCREENS.map(({ href, labelKey }) => (
              <li key={href}>
                <a
                  href={href}
                  className="block rounded px-3 py-2 text-sm hover:bg-neutral-800 focus-visible:outline-2"
                >
                  {t(labelKey)}
                </a>
              </li>
            ))}
          </ul>
          <LocaleSwitcher locale={locale} />
        </div>
      </nav>
      <main className="p-4">{children}</main>
    </div>
  );
}
