import type { Metadata, Viewport } from "next";
import type { ReactNode } from "react";

import { QueryProvider } from "@/lib/query/query-provider";

import "./globals.css";

export const metadata: Metadata = {
  title: "Kadran",
  description:
    "La rentabilité réelle d'un chauffeur VTC : marge par sortie, coût de revient au kilomètre, " +
    "commission effective, revenu réellement disponible.",
};

export const viewport: Viewport = {
  // Le chiffre héros doit tenir sur 375 px sans troncature (DESIGN-BRIEF §6).
  width: "device-width",
  initialScale: 1,
  themeColor: "#0a0a0a",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  // `data-theme="dark"` est posé côté serveur : pas de bascule visible au chargement.
  return (
    <html lang="fr" data-theme="dark">
      <body>
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
