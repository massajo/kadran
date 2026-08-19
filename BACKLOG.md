# BACKLOG.md — Issues GitHub

Backlog prêt à créer. Chaque ligne devient une issue GitHub dont le titre est `[KDN-<n>] <intitulé>`.
Référence fonctionnelle : `docs/SPEC-MVP-kadran.md`. Méthode de travail : `CLAUDE.md`.

---

## Conventions

**Labels obligatoires sur chaque issue**

| Famille | Valeurs |
|---|---|
| Type | `epic`, `story`, `spike`, `bug` |
| Domaine | `area:backend`, `area:frontend`, `area:infra`, `area:design` |
| Contexte | `ctx:activity`, `ctx:ingestion`, `ctx:costmodel`, `ctx:fiscal`, `ctx:performance`, `ctx:identity`, `ctx:privacy`, `ctx:audit`, `ctx:platform` |
| Priorité | `prio:P0`, `prio:P1`, `prio:P2` |
| Grain | `grain:trip`, `grain:outing`, `grain:day`, `grain:period` |
| Blocage | `needs-sample`, `blocked` |

**Gabarit de corps d'issue**

```markdown
## Contexte
[Pourquoi cette issue existe, avec renvoi à la section de spec]

## Périmètre
- [ ] …

## Critères d'acceptation
- Étant donné … quand … alors …

## Notes techniques
Réf. spec : §X.Y
Contexte borné : `ingestion`
Grain : PERIOD
Changeset Liquibase attendu : oui / non
Maquette : design/screens/<écran>/v<version>

## Definition of Done
- [ ] Tests unitaires du domaine, sans mock
- [ ] Test d'intégration Testcontainers si persistance
- [ ] Test d'isolation multi-tenant si nouvelle table
- [ ] Passage par `TenantScopedQuery` (ArchUnit vert)
- [ ] Événement d'audit émis pour toute mutation
- [ ] Changeset Liquibase avec rollback vérifié
- [ ] OpenAPI à jour et types front régénérés si l'API bouge
```

---

## EPIC KDN-1 — Socle et pipeline
`epic` · `prio:P0`

> **À livrer intégralement avant toute fonctionnalité métier.** Fin d'epic : un commit sur `main` produit et publie deux images conteneurisées, et `docker compose up` démarre une application accessible affichant une page authentifiée vide.

| # | Issue | Domaine |
|---|---|---|
| KDN-2 | Initialiser le monorepo : arborescence, `CLAUDE.md`, `BACKLOG.md`, `DESIGN-BRIEF.md`, licence, README | `infra` |
| KDN-3 | Squelette Gradle multi-modules avec `domain/{model,api,spi}`, `application`, `infrastructure/{api,spi}` | `backend` |
| KDN-4 | Squelette Next.js 15, shadcn/ui, structure `features/`, consommation de `design/tokens.json` | `frontend` |
| KDN-5 | `docker/compose.yml` : PostgreSQL 18, MinIO, backend, web, rechargement à chaud | `infra` |
| KDN-6 | Dockerfile backend multi-étapes : JAR en couches, `temurin:21-jre-alpine`, non-root, healthcheck | `infra` |
| KDN-7 | Dockerfile frontend : Next.js `output: standalone`, `node:22-alpine`, non-root | `infra` |
| KDN-8 | Workflow `commitlint.yml` : Conventional Commits sur titre de PR et commits | `infra` |
| KDN-9 | Workflow `ci-backend.yml` : ktlint, detekt, tests, ArchUnit, Testcontainers, seuil JaCoCo sur `domain` | `infra` |
| KDN-10 | Workflow `ci-frontend.yml` : eslint, `tsc --noEmit`, vitest, `next build` | `infra` |
| KDN-11 | Workflow `ci-liquibase.yml` : `update` puis `rollbackCount` sur Postgres éphémère | `infra` |
| KDN-12 | Workflow `ci-openapi.yml` : génération OpenAPI, détection de rupture, vérification des types front | `infra` |
| KDN-13 | Workflow `build-images.yml` : build multi-arch, Trivy, SBOM, push GHCR, étiquettes OCI | `infra` |
| KDN-14 | PostgreSQL + Liquibase : changelog maître, premier changeset, convention de nommage | `backend` |
| KDN-15 | `TenantContext`, filtre servlet, propagation coroutines, `correlation_id` en MDC | `backend` |
| KDN-16 | `TenantScopedQuery`, `TenantScopedTable`, règles ArchUnit (pureté domaine, SQL sans `tenant_id`) | `backend` |
| KDN-17 | Classe abstraite paramétrée de test d'isolation multi-tenant | `backend` |
| KDN-18 | Authentification JWT + refresh, Spring Security | `backend` |
| KDN-19 | Écrans de connexion et d'inscription, gestion de session, garde de route | `frontend` |
| KDN-121 | Observabilité backend : logs JSON structurés sans PII, MDC, `/actuator/prometheus` sur port de management séparé, conventions de nommage des métriques. **Déplacer Actuator sur le port de management casse le `HEALTHCHECK` de `backend/Dockerfile` (KDN-6), qui interroge le port applicatif : le corriger dans la même PR** | `backend` |
| KDN-122 | Prometheus + Grafana dans `docker/compose.yml`, tableaux de bord versionnés dans `docker/grafana/` | `infra` |

---

## EPIC KDN-20 — Audit
`epic` · `ctx:audit` · `prio:P0`

| # | Issue | Domaine |
|---|---|---|
| KDN-21 | Table `audit_event` partitionnée par mois, `INSERT`/`SELECT` seuls pour le rôle applicatif | `backend` |
| KDN-22 | Annotation `@Audited` et aspect en couche `application` | `backend` |
| KDN-23 | Couverture des catégories §8.4 : auth, autorisation, métier, imports, PII, configuration, exports | `backend` |
| KDN-24 | Purge par détachement de partition, rétention 5 ans configurable | `backend` |
| KDN-25 | Écran Journal : table filtrable par acteur, action, date | `frontend` |

---

## EPIC KDN-26 — Identité et onboarding
`epic` · `ctx:identity` · `prio:P0`

| # | Issue | Domaine |
|---|---|---|
| KDN-27 | Agrégats `Tenant`, `Membership`, `Driver` avec anticipation du modèle flotte | `backend` |
| KDN-28 | API d'onboarding avec brouillon persisté et reprenable (`onboardingStatus`) | `backend` |
| KDN-29 | Intégration API Recherche d'entreprises pour pré-remplissage SIREN | `backend` |
| KDN-30 | Assistant d'onboarding étapes 1–2 : identité, profil fiscal | `frontend` |
| KDN-31 | Assistant d'onboarding étape 5 : premier import guidé, par document | `frontend` |

---

## EPIC KDN-32 — Modèle d'activité
`epic` · `ctx:activity` · `prio:P0`

| # | Issue | Domaine |
|---|---|---|
| KDN-33 | **`BusinessDayPolicy` et `toBusinessDay`, tests sur changements d'heure — prérequis de tout le reste** | `backend` |
| KDN-34 | Agrégat `Outing` : `businessDay`, `window` nullable, `spansMidnight`, `purpose` | `backend` |
| KDN-35 | Agrégat `RevenueRecord` avec `grain` et `platformExtras` | `backend` |
| KDN-36 | `PlatformProfile` et `SourceCapability`, avec le profil Uber | `backend` |
| KDN-37 | Agrégat `WorkDay` | `backend` |
| KDN-38 | **`IntervalUnion` + property-based tests — à traiter tôt** | `backend` |
| KDN-39 | Type somme `Amplitude` : `Observed` \| `Floor` (ADR-009) | `backend` |
| KDN-40 | API de saisie manuelle d'une sortie | `backend` |
| KDN-41 | Écran Sorties : liste, filtres, détail avec décomposition et sources | `frontend` |
| KDN-42 | Saisie et correction manuelle d'une sortie | `frontend` |

---

## EPIC KDN-43 — Confidentialité
`epic` · `ctx:privacy` · `prio:P0`

| # | Issue | Domaine |
|---|---|---|
| KDN-44 | Chiffrement enveloppe AES-256-GCM, DEK par tenant, converters de persistance | `backend` |
| KDN-45 | Blind index HMAC-SHA256 pour la recherche par égalité | `backend` |
| KDN-46 | Option `retainCounterpartyIdentity` (off par défaut) et pseudonymisation à l'import | `backend` |
| KDN-47 | Réduction des adresses Driversnote au code postal et à la ville | `backend` |
| KDN-48 | Purge programmée des données personnelles et de `raw_payload` | `backend` |
| KDN-49 | Masquage à l'affichage, bascule en clair avec ré-authentification et expiration 15 min | `frontend` |

---

## EPIC KDN-50 — Kilométrage
`epic` · `ctx:ingestion` · `grain:outing` · `prio:P0`

| # | Issue | Domaine |
|---|---|---|
| KDN-51 | **[spike] `Début`/`Fin` du CSV Driversnote portent-ils une heure ? Ouvrir en éditeur de texte, pas en tableur** — `needs-sample`, **BLOQUANT** | `backend` |
| KDN-52 | Profil de mapping Driversnote : 9 colonnes, `Taux` arrondi non recalculable, dates avec ou sans heure | `backend` |
| KDN-53 | Import CSV kilométrage générique avec assistant de correspondance de colonnes | `backend` |
| KDN-54 | Exclusion des sorties `PERSONNEL` des calculs de rentabilité | `backend` |
| KDN-55 | Assistant de correspondance de colonnes | `frontend` |

---

## EPIC KDN-56 — Ingestion, socle générique
`epic` · `ctx:ingestion` · `prio:P0`

| # | Issue | Domaine |
|---|---|---|
| KDN-57 | Agrégat `ImportBatch` et machine à états `UPLOADED → … → COMMITTED \| FAILED` | `backend` |
| KDN-58 | `MappingProfile` en base et moteur d'application déclaratif | `backend` |
| KDN-59 | Stockage `platform_extras` / `raw_payload` en JSONB, index GIN | `backend` |
| KDN-60 | Rejeu d'un import après correction de profil, sans nouveau fichier | `backend` |
| KDN-61 | Rapport de divergence sur périodes recouvrantes, last-write-wins par `externalRef` | `backend` |
| KDN-62 | Stockage des fichiers sources sur S3/MinIO | `backend` |
| KDN-63 | Écran Imports : upload, suivi de statut, revue des anomalies | `frontend` |
| KDN-64 | Calendrier de complétude par semaine, avec CTA d'import ciblé | `frontend` |

---

## EPIC KDN-65 — Ingestion Uber
`epic` · `ctx:ingestion` · `prio:P0`

> Seule plateforme du périmètre v1 (ADR-008).

| # | Issue | Domaine |
|---|---|---|
| KDN-66 | **[spike] En-têtes complets du CSV factures Uber** — `needs-sample` | `backend` |
| KDN-67 | Parseur PDF relevé hebdomadaire, section Transactions | `backend` |
| KDN-68 | Parseur PDF relevé hebdomadaire, section Détails des revenus | `backend` |
| KDN-69 | Parseur PDF récapitulatif fiscal mensuel | `backend` |
| KDN-70 | Parseur CSV factures avec traitement PII | `backend` |
| KDN-71 | `TripMatcher` : rapprochement des trois documents par horodatage et montant, scoring, seuils | `backend` |
| KDN-72 | Contrôle de cohérence sur le solde cumulé du relevé | `backend` |
| KDN-73 | File de résolution manuelle des rapprochements ambigus | `backend` |
| KDN-74 | Écran de résolution : sources à gauche, propositions à droite, validation en un geste | `frontend` |

---

## EPIC KDN-75 — Rattachement revenus ↔ sorties
`epic` · `ctx:activity` · `prio:P0`

| # | Issue | Domaine |
|---|---|---|
| KDN-76 | Moteur de rattachement temporel avec score de confiance | `backend` |
| KDN-77 | Mode dégradé au grain journée si les horaires sont absents | `backend` |
| KDN-78 | Restitution du rattachement dans le détail d'une sortie | `frontend` |

---

## EPIC KDN-79 — Modèle de coûts
`epic` · `ctx:costmodel` · `prio:P0`

| # | Issue | Domaine |
|---|---|---|
| KDN-80 | `CostModel` versionné par `validFrom`/`validTo`, résolution à la date de l'opération | `backend` |
| KDN-81 | Référentiel de valeurs sectorielles par défaut, par segment de véhicule | `backend` |
| KDN-82 | Métriques `C1`–`C7` dont `C7` barème kilométrique vs frais réels | `backend` |
| KDN-83 | Assistant de saisie des coûts, étapes 3–4 de l'onboarding, impact `C3` recalculé en direct | `frontend` |
| KDN-84 | Écran de modification du modèle de coûts avec historique des versions | `frontend` |

---

## EPIC KDN-85 — Moteur de métriques
`epic` · `ctx:performance` · `prio:P0`

| # | Issue | Domaine |
|---|---|---|
| KDN-86 | `MetricDefinition` avec `requiredGrain` et `requiredCapabilities`, registre | `backend` |
| KDN-87 | `MetricUnavailable(reason)` — jamais zéro, jamais null silencieux | `backend` |
| KDN-88 | `DataCompleteness` et `CompletenessLevel` | `backend` |
| KDN-89 | Projections quotidiennes, recalcul événementiel idempotent | `backend` |
| KDN-90 | Métriques temps, distance, revenu (§6.1–6.3) | `backend` |
| KDN-91 | Métriques marge dont `M11`–`M13` marge par sortie | `backend` |
| KDN-92 | Endpoint `/metrics` avec `confidence` systématique | `backend` |
| KDN-93 | Endpoint de traçabilité : sources ayant produit chaque métrique | `backend` |
| KDN-94 | Composants d'état de confiance : `RELIABLE`, `INDICATIVE`, `INSUFFICIENT`, `Floor`, `Unavailable` | `frontend` |

---

## EPIC KDN-95 — Dashboard
`epic` · `ctx:performance` · `prio:P0`

| # | Issue | Domaine |
|---|---|---|
| KDN-96 | Écran Overview, chiffre héros `F4`, quatre états | `frontend` |
| KDN-97 | Bloc Commission : taux effectif Uber, décomposition, écart au taux annoncé | `frontend` |
| KDN-98 | Comparaison de périodes et drill-down | `frontend` |
| KDN-99 | Distribution du revenu net par heure et jour de semaine | `frontend` |

---

## EPIC KDN-100 — Fiscal
`epic` · `ctx:fiscal` · `prio:P0`

| # | Issue | Domaine |
|---|---|---|
| KDN-101 | `FiscalProfile` et table `provision_rate` versionnée par `valid_from` | `backend` |
| KDN-102 | Métriques `F1`–`F5`, ventilation TVA 10 % / 20 % portée par la ligne de revenu | `backend` |
| KDN-103 | Alertes de seuil avec projection de dépassement | `backend` |
| KDN-104 | Écran Fiscal : provisions, TVA, seuils | `frontend` |

---

## EPIC KDN-105 — Exports
`epic` · `prio:P1`

| # | Issue | Domaine |
|---|---|---|
| KDN-106 | Export CSV/XLSX pour expert-comptable | `backend` |
| KDN-107 | Synthèse mensuelle PDF | `backend` |
| KDN-108 | Écran d'export avec sélection de période et de périmètre | `frontend` |

---

## Reporté en v1.1 — hors périmètre initial

| Epic | Contenu |
|---|---|
| **KDN-110 — Ingestion Bolt** | Profil de mapping des 39 colonnes (BOM UTF-8, en-tête `"Chauffeur "` à espace terminal), contrôle d'intégrité bruts − frais = nets, avertissement si période > 8 jours, **[spike] qualifier l'export *Trips*** (`fleets.bolt.eu` → Trips → période → CSV) |
| **KDN-115 — Ingestion Heetch** | Profil de mapping des 8 colonnes (`€` préfixé, `Mois` en `yyyy-M`, ligne `Total` à écarter), refus explicite des métriques temps et distance |
| **KDN-120 — Arbitrage plateforme** | Métriques `A1`–`A6`, heatmap marge × jour × créneau, écran Plateformes |

Les profils Bolt et Heetch sont **déjà entièrement spécifiés** en §3.4 et §3.6 de la spec. Le socle d'ingestion générique (KDN-56) est conçu pour les accueillir sans modification du moteur.

---

## Ordre de traitement recommandé

```
KDN-1  (socle et pipeline, intégral)
  → KDN-20  (audit)
  → KDN-26  (identité, onboarding partiel)
  → KDN-33  (BusinessDayPolicy — avant tout le reste du modèle d'activité)
  → KDN-32  (modèle d'activité)
  → KDN-51  (spike Driversnote — BLOQUANT)
  → KDN-50  (kilométrage)
  → KDN-43  (confidentialité)
  → KDN-56  (socle d'ingestion)
  → KDN-66  (spike factures Uber — bloque la métrique de vente)
  → KDN-65  (ingestion Uber)
  → KDN-75  (rattachement)
  → KDN-79  (coûts)
  → KDN-85  (métriques)
  → KDN-100 (fiscal)
  → KDN-95  (dashboard)
  → KDN-105 (exports)
```

**Deux issues conditionnent le reste :** KDN-33 précède tout calcul journalier ; KDN-51 et KDN-66 sont bloquées tant que les échantillons de fichiers ne sont pas fournis. Ne pas les contourner en inventant les formats.
