# Kadran

**Kadran mesure la rentabilité réelle d'un chauffeur VTC indépendant.**
Les plateformes affichent du chiffre d'affaires ; Kadran affiche de la marge : marge par sortie,
coût de revient au kilomètre, commission effective, revenu réellement disponible après charges.

Périmètre v1 : **Uber uniquement** (ADR-008), alimenté par import de fichiers, plus Driversnote
pour le kilométrage.

---

## Où lire quoi

| Document | Répond à |
|---|---|
| [`docs/SPEC-MVP-kadran.md`](docs/SPEC-MVP-kadran.md) | **Quoi** construire — spécification fonctionnelle et technique. Fait foi sur le fond |
| [`CLAUDE.md`](CLAUDE.md) | **Comment** travailler — règles d'or, déroulé d'issue, Definition of Done. Fait foi sur la méthode |
| [`BACKLOG.md`](BACKLOG.md) | **Dans quel ordre** — epics et issues, labels, gabarit de corps d'issue |
| [`DESIGN-BRIEF.md`](DESIGN-BRIEF.md) | Cadre de maquettage, versionnement des écrans |
| [`docs/adr/`](docs/adr/) | Décisions d'architecture actées (ADR-001 à ADR-010) |

En cas de contradiction entre la spec et `CLAUDE.md` : la spec sur le fond, `CLAUDE.md` sur la méthode.

## Structure du dépôt

Monorepo (ADR-010) — le back, le front, les maquettes et la doc dans un seul dépôt, pour qu'une
même PR vérifie que les types du front correspondent au contrat OpenAPI du back.

```
backend/            Gradle multi-modules, Kotlin 2.2 / Spring Boot 4.1 — racine de construction
  shared-kernel/      value objects (Money, Ratio, Distance…) — zéro dépendance Spring
  platform/           tenancy, sécurité, chiffrement, audit, outbox
  context-<nom>/      un contexte borné : domain/{model,api,spi} · application · infrastructure/{api,spi}
  app/                bootstrap Spring Boot, composition des modules
web/                Next.js 15 (App Router), React 19, shadcn/ui
design/             maquettes versionnées — voir DESIGN-BRIEF.md
docs/               spécification et ADR
docker/             compose de développement (PostgreSQL 18, MinIO, back, web)
.github/workflows/  CI, validation Liquibase, publication d'images
```

`api` = ce que le module **offre**. `spi` = ce que le module **exige** d'un fournisseur (ADR-005).
La convention s'applique symétriquement aux ports (`domain/`) et aux adaptateurs (`infrastructure/`).

## Démarrer

> ⚠️ Le socle technique est en cours de construction — EPIC KDN-1. Les commandes ci-dessous
> fonctionnent ; il n'y a simplement pas encore de fonctionnalité métier derrière.

```bash
docker compose -f docker/compose.yml up -d   # Postgres, MinIO, backend, web, rechargement à chaud

# La racine de construction Gradle est `backend/`, pas la racine du dépôt :
# il n'existe pas de `gradlew` ici.
cd backend
./gradlew check                              # ktlint, detekt, tests, ArchUnit, couverture
./gradlew :app:bootRun

pnpm --filter web verify                     # eslint, tsc, vitest, build
pnpm --filter web dev
```

**Fin de l'EPIC KDN-1** : un commit sur `main` produit et publie deux images conteneurisées, et
`docker compose up` démarre une application accessible affichant une page authentifiée vide.
Aucune fonctionnalité métier n'est commencée avant cet état.

## Contribuer

Lire `CLAUDE.md` en entier avant la première PR. Les sept règles d'or (§2) ne sont pas négociables ;
en particulier :

- **Ne jamais inventer une donnée.** Une donnée manquante produit `MetricUnavailable(reason)`,
  jamais un zéro ni une valeur reconstituée par répartition (ADR-004).
- **Toute valeur monétaire est un `Money` en centimes.** Jamais de `Double`.
- **Toute requête est scopée par tenant**, via `TenantScopedQuery` (ADR-001).
- **La journée d'exploitation n'est pas la journée calendaire** — une vacation de nuit traverse
  minuit, toute agrégation journalière passe par `BusinessDayPolicy` (ADR-006).

Une issue = une PR = une intention. Branches `<type>/KDN-<n>-<slug>`, commits en Conventional
Commits avec `Refs: KDN-<n>`.

## Licence

Propriétaire — tous droits réservés. Voir [`LICENSE`](LICENSE).
