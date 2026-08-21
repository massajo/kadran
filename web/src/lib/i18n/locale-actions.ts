"use server";

import { revalidatePath } from "next/cache";
import { cookies } from "next/headers";

import { LOCALE_COOKIE, LOCALE_COOKIE_MAX_AGE, isAppLocale } from "./config";

/**
 * Change la langue et la persiste.
 *
 * Action serveur soumise par un vrai `<form>` : le sélecteur fonctionne sans JavaScript,
 * et la page revient déjà traduite — aucun rendu intermédiaire dans l'ancienne langue.
 *
 * La valeur reçue vient du client : elle est validée avant d'être écrite. Un cookie de
 * langue arbitraire ferait échouer la résolution du catalogue à chaque requête suivante.
 */
export async function setLocale(formData: FormData): Promise<void> {
  const requested = formData.get("locale");

  if (!isAppLocale(requested)) {
    throw new Error(`Langue non supportée : ${String(requested)}`);
  }

  (await cookies()).set({
    name: LOCALE_COOKIE,
    value: requested,
    maxAge: LOCALE_COOKIE_MAX_AGE,
    path: "/",
    sameSite: "lax",
  });

  // Le cookie est lu par le layout racine : c'est toute l'arborescence qu'il faut refaire,
  // pas seulement la page courante.
  revalidatePath("/", "layout");
}
