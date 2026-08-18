// Traduit `design/tokens.json` en variables CSS consommées par Tailwind.
//
// `tokens.json` est la **seule passerelle entre design et code** (DESIGN-BRIEF §5.6) :
// une couleur qui n'y figure pas ne doit pas apparaître dans le front. Ce script est
// ce qui rend la règle applicable — sans lui, la passerelle n'existe que sur le papier.
//
// Le fichier n'est pas encore livré par le design. Tant qu'il manque, le script le dit
// et sort proprement : il ne fabrique aucune valeur de remplacement.

import { readFile, writeFile } from "node:fs/promises";
import { existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const source = resolve(here, "../../design/tokens.json");
const target = resolve(here, "../src/app/tokens.css");

if (!existsSync(source)) {
  console.error(
    [
      "design/tokens.json est absent.",
      "",
      "Il doit être livré par le design avant tout écran (DESIGN-BRIEF §6.1) et figer :",
      "  · la palette dark et sa variante light,",
      "  · l'échelle typographique — le chiffre héros doit tenir sur 375 px sans troncature,",
      "  · les échelles d'espacement, les rayons, les élévations,",
      "  · la palette sémantique positif / négatif / neutre / avertissement,",
      "  · la palette des niveaux de confiance, DISTINCTE de la précédente :",
      "    une donnée peu fiable n'est pas une mauvaise nouvelle.",
      "",
      "Aucune valeur n'est inventée ici. Voir KDN-4.",
    ].join("\n"),
  );
  process.exit(1);
}

const flatten = (node, path = []) =>
  Object.entries(node).flatMap(([key, value]) =>
    value !== null && typeof value === "object"
      ? flatten(value, [...path, key])
      : [[[...path, key].join("-"), String(value)]],
  );

const tokens = JSON.parse(await readFile(source, "utf8"));
const declare = (scope) =>
  flatten(tokens[scope] ?? {})
    .map(([name, value]) => `  --${name}: ${value};`)
    .join("\n");

await writeFile(
  target,
  [
    "/* Généré par `pnpm tokens` depuis design/tokens.json. Ne pas modifier à la main. */",
    ":root {",
    declare("dark"),
    "}",
    "",
    ':root[data-theme="light"] {',
    declare("light"),
    "}",
    "",
  ].join("\n"),
  "utf8",
);
console.log(`tokens.css régénéré depuis ${source}`);
