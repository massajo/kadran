"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState, type ReactNode } from "react";

/**
 * Les Server Components sont le défaut (spec §10.5). TanStack Query ne sert que les
 * fragments réellement interactifs — filtres, résolution d'ambiguïtés, imports en cours.
 *
 * Le client est créé dans l'état du composant, jamais au niveau du module : un client
 * partagé entre requêtes fuiterait le cache d'un tenant vers un autre.
 */
export function QueryProvider({ children }: { children: ReactNode }) {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { staleTime: 30_000, refetchOnWindowFocus: false },
        },
      }),
  );

  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
