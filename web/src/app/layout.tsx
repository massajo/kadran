import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";

import { AppIntlProvider } from "@/lib/i18n/intl-provider";
import { getTranslations, resolveLocale } from "@/lib/i18n/locale";
import { QueryProvider } from "@/lib/query/query-provider";

import "./globals.css";

// Les métadonnées suivent la langue résolue : le titre d'onglet et la description
// partagée ne peuvent pas rester figés en français pour un utilisateur anglophone.
export async function generateMetadata(): Promise<Metadata> {
  const t = await getTranslations("app");
  return { title: t("title"), description: t("description") };
}

export const viewport: Viewport = {
  // Le chiffre héros doit tenir sur 375 px sans troncature (DESIGN-BRIEF §6).
  width: "device-width",
  initialScale: 1,
  themeColor: "#0a0a0a",
};

export default async function RootLayout({ children }: { children: ReactNode }) {
  // `data-theme="dark"` est posé côté serveur : pas de bascule visible au chargement.
  // `lang` suit la même logique — résolu avant le premier octet, jamais corrigé après coup.
  const locale = await resolveLocale();

  return (
    <html lang={locale} data-theme="dark">
      <body>
        <AppIntlProvider locale={locale}>
          <QueryProvider>{children}</QueryProvider>
        </AppIntlProvider>
      </body>
    </html>
  );
}
