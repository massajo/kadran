import { redirect } from "next/navigation";

export default function Home() {
  // L'Overview est l'écran d'entrée : chiffre héros `F4` (spec §13).
  redirect("/overview");
}
