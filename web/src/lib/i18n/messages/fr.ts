/**
 * Catalogue français — **la source de référence**.
 *
 * Les clés sont des identifiants : elles sont en anglais (CLAUDE.md §6, « Langue »).
 * Les valeurs, elles, sont évidemment dans la langue du catalogue.
 *
 * Ce fichier est la seule source pour le français : il n'y a pas de second fichier `fr`
 * quelque part, et donc aucune clé dupliquée à arbitrer. `en.ts` se déclare conforme à
 * la forme de ce catalogue, ce qui fait d'une clé manquante une erreur de compilation.
 *
 * `as const` fige le catalogue : les valeurs ne sont pas modifiables à l'exécution, et la
 * forme dérivée ci-dessous se calcule sur une structure connue clé à clé. Les arguments
 * ICU (`{issue}` plus bas), eux, ne sont pas vérifiés par le type — c'est le rôle du
 * `onError` de `intl.ts`, qui lève, et du test qui compare les arguments d'une langue à
 * l'autre.
 */
const fr = {
  app: {
    title: "Kadran",
    description:
      "La rentabilité réelle d'un chauffeur VTC : marge par sortie, coût de revient au " +
      "kilomètre, commission effective, revenu réellement disponible.",
  },
  nav: {
    label: "Navigation principale",
    overview: "Vue d'ensemble",
    outings: "Sorties",
    costs: "Coûts",
    imports: "Imports",
    fiscal: "Fiscal",
    audit: "Journal",
  },
  auth: {
    login: { title: "Connexion" },
    register: { title: "Inscription" },
  },
  screen: {
    // `{issue}` porte le numéro d'issue qui livrera l'écran : « … — KDN-96. »
    notImplemented: "Route en place, écran non implémenté — {issue}.",
  },
  // Les noms de langue restent des endonymes — « Français », « English » — dans les deux
  // catalogues : un sélecteur se lit dans la langue qu'il propose, pas dans celle qu'on
  // quitte. C'est aussi ce qui rend l'attribut `lang` du bouton exact.
  localeSwitcher: {
    label: "Langue",
    fr: "Français",
    en: "English",
  },
  // Niveaux de complétude (spec §5). Trois niveaux, trois conduites distinctes :
  // affichage normal, fourchette obligatoire, métrique masquée.
  completeness: {
    reliable: "Fiable",
    reliableHint: "Les données couvrant cette période sont complètes.",
    indicative: "Indicatif",
    indicativeHint: "Données partielles : à lire comme un ordre de grandeur, pas comme une mesure.",
    insufficient: "Insuffisant",
    insufficientHint: "Trop de données manquent pour calculer cette métrique.",
  },
  // Amplitude (ADR-009) : `Observed` et `Floor` ne disent pas la même chose et ne doivent
  // jamais s'aplatir en un mot commode. `Floor` est une **borne inférieure** — l'attente
  // avant la première course et après la dernière n'y figure pas — donc toute métrique
  // rapportée à cette amplitude est optimiste. Les deux langues gardent cette distinction.
  amplitude: {
    observed: "Amplitude mesurée",
    observedHint: "Fenêtre réelle de la sortie, relevée par Driversnote.",
    floor: "Amplitude minimale",
    floorHint:
      "Borne inférieure : de la première à la dernière course. Le temps d'attente hors " +
      "courses n'y figure pas, l'amplitude réelle est donc plus longue.",
    floorValue: "au moins {duration}",
  },
} as const;

export default fr;

/**
 * Forme d'un catalogue : la structure de la référence française, dont les feuilles sont
 * des chaînes quelconques. C'est ce type que `en.ts` doit satisfaire — pas `typeof fr`,
 * qui exigerait les mêmes littéraux et rendrait toute traduction impossible.
 */
export type MessageCatalog = CatalogShape<typeof fr>;

type CatalogShape<T> = {
  [Key in keyof T]: T[Key] extends string ? string : CatalogShape<T[Key]>;
};
