// Configuration commitlint — cf. CLAUDE.md §6 et spec §11.2.
//
// Format imposé : `<type>(<scope>): [KDN-<n>] <description à l'impératif, minuscule, sans point
// final>`. Le `[KDN-<n>]` fait partie de la description (après le préfixe Conventional Commits),
// donc les règles `subject-case` / `subject-full-stop` natives de commitlint — qui s'appliquent
// à la description entière, crochets compris — ne conviennent pas : elles échoueraient sur le
// `[` initial. On les désactive et on les remplace par trois règles locales qui isolent le texte
// après `[KDN-<n>] ` avant de vérifier la casse et la ponctuation.

const TYPES = ["feat", "fix", "refactor", "test", "docs", "chore", "perf", "build", "ci"];

const SCOPES = [
  "activity",
  "ingestion",
  "costmodel",
  "fiscal",
  "performance",
  "identity",
  "privacy",
  "audit",
  "platform",
  "web",
  "db",
  "ci",
];

const KDN_SUBJECT_PATTERN = /^\[KDN-\d+\] (.+)$/;

/** @type {import('@commitlint/types').Plugin} */
const kadranPlugin = {
  rules: {
    "kadran-subject-references-issue": ({ subject }) => {
      if (!subject) {
        return [false, 'la description est vide, elle doit commencer par "[KDN-<n>] "'];
      }
      if (!KDN_SUBJECT_PATTERN.test(subject)) {
        return [
          false,
          'la description doit commencer par "[KDN-<n>] " suivi du texte, par exemple ' +
            '"[KDN-42] mapper l\'export driversnote"',
        ];
      }
      return [true];
    },
    "kadran-subject-lower-case": ({ subject }) => {
      const match = KDN_SUBJECT_PATTERN.exec(subject ?? "");
      if (!match) {
        // Signalé par kadran-subject-references-issue.
        return [true];
      }
      const text = match[1].trim();
      if (/^[A-ZÀ-Ý]/.test(text)) {
        return [false, 'le texte après "[KDN-<n>]" doit commencer par une minuscule'];
      }
      return [true];
    },
    "kadran-subject-no-full-stop": ({ subject }) => {
      const match = KDN_SUBJECT_PATTERN.exec(subject ?? "");
      if (!match) {
        // Signalé par kadran-subject-references-issue.
        return [true];
      }
      const text = match[1].trim();
      if (text.endsWith(".")) {
        return [false, 'le texte après "[KDN-<n>]" ne doit pas se terminer par un point'];
      }
      return [true];
    },
  },
};

export default {
  extends: ["@commitlint/config-conventional"],
  plugins: [kadranPlugin],
  rules: {
    "type-enum": [2, "always", TYPES],
    "type-case": [2, "always", "lower-case"],
    "scope-enum": [2, "always", SCOPES],
    "scope-empty": [2, "never"],
    "scope-case": [2, "always", "lower-case"],
    "subject-empty": [2, "never"],
    // Désactivées : la description commence par "[KDN-<n>]", incompatible avec les règles
    // natives. Remplacées par les règles kadran-subject-* ci-dessous.
    "subject-case": [0],
    "subject-full-stop": [0],
    "kadran-subject-references-issue": [2, "always"],
    "kadran-subject-lower-case": [2, "always"],
    "kadran-subject-no-full-stop": [2, "always"],
    "header-max-length": [2, "always", 100],
  },
};
