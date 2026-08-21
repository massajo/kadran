"use client";

import { useTranslations } from "use-intl";

import { LOCALES, type AppLocale } from "./config";
import { setLocale } from "./locale-actions";

/**
 * Sélecteur de langue.
 *
 * Un bouton de soumission par langue plutôt qu'un `<select>` : à deux langues, l'état
 * courant se lit d'un coup d'œil, le changement tient en un clic, et le tout fonctionne
 * sans JavaScript puisque c'est une soumission de formulaire ordinaire.
 *
 * `aria-current` — et non `disabled` — marque la langue active : le bouton reste
 * atteignable au clavier et annoncé par les lecteurs d'écran.
 */
export function LocaleSwitcher({ locale }: { locale: AppLocale }) {
  const t = useTranslations("localeSwitcher");

  return (
    <form action={setLocale} className="flex items-center gap-1">
      <span className="sr-only" id="locale-switcher-label">
        {t("label")}
      </span>
      <ul aria-labelledby="locale-switcher-label" className="flex gap-1">
        {LOCALES.map((candidate) => (
          <li key={candidate}>
            <button
              type="submit"
              name="locale"
              value={candidate}
              lang={candidate}
              aria-current={candidate === locale ? "true" : undefined}
              className="rounded px-2 py-1 text-xs hover:bg-neutral-800 aria-[current]:bg-neutral-800 focus-visible:outline-2"
            >
              {t(candidate)}
            </button>
          </li>
        ))}
      </ul>
    </form>
  );
}
