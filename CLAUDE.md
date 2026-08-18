# CLAUDE.md — Instructions de travail

Ce fichier est lu à chaque session. Il définit **comment** travailler sur Kadran.
La spécification fonctionnelle et technique est dans `docs/SPEC-MVP-kadran.md` : elle définit **quoi** construire. En cas de contradiction entre ce fichier et la spec, la spec fait foi sur le fond, ce fichier sur la méthode.

---

## 1. Le projet en trois phrases

Kadran mesure la rentabilité réelle d'un chauffeur VTC indépendant : marge par sortie, coût de revient au kilomètre, commission effective des plateformes, revenu réellement disponible après charges. Les plateformes affichent du chiffre d'affaires ; Kadran affiche de la marge.

Périmètre v1 : **Uber uniquement**, alimenté par import de fichiers, plus Driversnote pour le kilométrage.

---

## 2. Règles d'or — non négociables

Ces règles priment sur toute considération de rapidité ou d'élégance. Une PR qui en viole une est rejetée, même si elle fonctionne.

### 2.1 Ne jamais inventer une donnée

**Interdit** : estimer une valeur par répartition proportionnelle. Uber ne donne pas la distance par course ; répartir les kilomètres du mois au prorata des tarifs produirait un chiffre qui *ressemble* à une donnée et n'en est pas une. C'est ADR-004.

Quand une donnée manque, la métrique renvoie `MetricUnavailable(reason)` — jamais `0`, jamais `null` silencieux, jamais une valeur reconstituée.

Corollaire pour le parsing : **si un échantillon de fichier n'est pas disponible, ne pas deviner les colonnes.** Arrêter, poser la question dans l'issue, marquer `needs-sample`. Un profil de mapping inventé est pire qu'absent : il échouera silencieusement en production.

### 2.2 Toute valeur monétaire est un `Money` en centimes

Jamais de `Double`, jamais de `Float`, jamais de `BigDecimal` nu pour de la monnaie. Les ratios sont des `Ratio` encapsulant un `BigDecimal`.

### 2.3 Toute requête est scopée par tenant

Aucun accès direct à `DSLContext` ou `JdbcTemplate` depuis un repository. Tout passe par `TenantScopedQuery`, qui exige le `TenantId` à la construction. ArchUnit le vérifie. Il n'y a pas de RLS pour rattraper un oubli (ADR-001).

Toute nouvelle table métier porte `tenant_id UUID NOT NULL` en tête de ses index composites, et fait l'objet d'un test d'isolation.

### 2.4 Le domaine est pur

`domain/model` ne dépend que de `shared-kernel` et de la bibliothèque standard. Pas de Spring, pas de Jackson, pas d'annotation de persistance. Si un test du domaine a besoin d'un mock, le modèle est mal découpé — corriger le modèle, pas le test.

### 2.5 Toute mutation est auditée

Chaque cas d'usage qui modifie l'état émet un événement d'audit via `@Audited`, dans la même transaction. Voir §8.4 de la spec pour le périmètre exact.

### 2.6 La journée d'exploitation n'est pas la journée calendaire

Une vacation de nuit traverse minuit. Toute agrégation journalière utilise `BusinessDayPolicy` (seuil par défaut 04:00), jamais `LocalDate.from(instant)`. C'est ADR-006 et c'est une source d'erreur silencieuse majeure.

### 2.7 Toute métrique porte sa confiance

Aucune valeur ne sort de l'API sans son `CompletenessLevel`. Le typage doit rendre impossible l'affichage d'un chiffre nu. En particulier, `Amplitude` est un type somme `Observed | Floor` : une borne inférieure ne doit jamais être présentée comme une mesure (ADR-009).

---

## 3. Structure du dépôt

```
kadran/
├── backend/
│   ├── shared-kernel/
│   ├── platform/                 # tenancy, sécurité, chiffrement, audit, outbox
│   ├── context-<nom>/
│   │   ├── domain/{model,api,spi}
│   │   ├── application/
│   │   └── infrastructure/{api/{rest,event}, spi/{persistence,storage,event}}
│   └── app/                      # bootstrap Spring Boot
├── web/
│   ├── src/app/(dashboard)/...
│   ├── src/features/<contexte>/
│   └── src/components/ui/        # shadcn, ne pas modifier à la main
├── design/                       # maquettes versionnées — voir DESIGN-BRIEF.md
├── docs/
├── docker/
└── .github/workflows/
```

`api` = ce que le module offre. `spi` = ce que le module exige d'un fournisseur. Cette convention s'applique aux ports (`domain/`) comme aux adaptateurs (`infrastructure/`).

---

## 4. Déroulé d'une issue

1. **Lire la section de spec référencée dans l'issue.** Ne pas commencer sans elle.
2. Si l'issue porte `needs-sample` et que l'échantillon n'est pas fourni : **s'arrêter**, commenter l'issue, passer à la suivante.
3. Créer la branche : `<type>/KDN-<n>-<slug>`.
4. Si la persistance est touchée : écrire le changeset Liquibase **d'abord**.
5. Écrire les tests avant ou avec le code. Domaine sans mock, application avec MockK, infrastructure avec Testcontainers.
6. Vérifier localement : `./gradlew check` et `pnpm verify`.
7. Commiter en Conventional Commits, scope = contexte borné.
8. Ouvrir la PR, remplir la checklist de DoD.

**Une issue = une PR = une intention.** Ne pas grouper plusieurs issues dans une PR. Si une issue s'avère trop large en cours de route, la scinder et le signaler plutôt que de livrer un gros bloc.

---

## 5. Commandes

```bash
# Environnement complet
docker compose -f docker/compose.yml up -d

# Backend
./gradlew check                        # ktlint, detekt, tests, ArchUnit, couverture
./gradlew :app:bootRun
./gradlew liquibaseUpdate liquibaseRollbackCount -PliquibaseCommandValue=1

# Frontend
pnpm --filter web verify               # eslint, tsc, vitest, build
pnpm --filter web dev
pnpm --filter web generate:api         # régénère les types depuis l'OpenAPI

# Issues
gh issue list --label "epic"
gh issue create --title "[KDN-42] ..." --body-file ...
```

---

## 6. Conventions

### Commits

```
<type>(<scope>): <description à l'impératif, minuscule, sans point final>

[corps expliquant le pourquoi, pas le quoi]

Refs: KDN-<n>
Closes #<n>
```

Types : `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `build`, `ci`.
Scopes : `activity`, `ingestion`, `costmodel`, `fiscal`, `performance`, `identity`, `privacy`, `audit`, `platform`, `web`, `db`, `ci`.

### Liquibase

Fichier : `backend/app/src/main/resources/db/changelog/changes/yyyyMMdd-KDN-<n>-<description-kebab>.sql`
Changeset : `--changeset kadran:yyyyMMdd-KDN-<n>-<seq> labels:<contexte> context:all`

Chaque changeset porte un `--rollback` fonctionnel — la CI le vérifie en exécutant réellement le rollback. Une migration fusionnée n'est jamais modifiée : on en ajoute une nouvelle.

### Branches

`feat/KDN-42-import-batch-state-machine`

---

## 7. Definition of Done

- [ ] Tests unitaires du domaine, sans mock
- [ ] Test d'intégration Testcontainers si persistance
- [ ] **Test d'isolation multi-tenant si nouvelle table**
- [ ] Passage par `TenantScopedQuery` (ArchUnit vert)
- [ ] Événement d'audit émis pour toute mutation
- [ ] Changeset Liquibase avec rollback vérifié
- [ ] OpenAPI à jour et types front régénérés si l'API bouge
- [ ] Aucune valeur monétaire en `Double`
- [ ] Aucune agrégation journalière sans `BusinessDayPolicy`
- [ ] Aucune donnée estimée par répartition

---

## 8. Pièges connus

Ces points ont déjà coûté du temps ou en coûteront. Ils sont documentés pour ne pas être redécouverts.

| Piège | Détail |
|---|---|
| CSV Bolt | BOM UTF-8 en tête, et l'en-tête est `"Chauffeur "` **avec un espace terminal** |
| CSV Heetch | `€` préfixé et collé au montant · `Mois` en `yyyy-M` sans zéro · une **ligne `Total`** à écarter · `Chiffre d'affaire HT` au singulier, à ne pas « corriger » |
| CSV Driversnote | `Taux` est arrondi à 2 décimales mais `Remboursement` ne l'est pas — **ne jamais recalculer l'un depuis l'autre**, ni écrire de contrôle en égalité stricte |
| Driversnote | `Début` et `Fin` peuvent porter une heure ou non. Accepter `dd/MM/yyyy` et `dd/MM/yyyy HH:mm`, et mettre `window` à `null` plutôt que d'inventer `00:00` |
| CSV factures Uber | 19 colonnes, **pas 18**. Noms accentués : `NuméroFacture`, `Quantité`, `ÉtiquetteTaxeUtilisateur`. `Tauxtaxe` avec un **`t` minuscule**, et sa valeur est la **chaîne `10%`**, pas `0.10`. HT = `MontantNet`, TTC = `MontantBrut` — `MontantHT`/`MontantTTC` n'existent pas |
| CSV factures Uber | `IdentifiantTaxeFournisseur` et `ÉtiquetteTaxeFournisseur` sont deux **listes parallèles dont l'ordre varie d'une ligne à l'autre**. Apparier par position dans chaque liste, ne jamais supposer que le SIREN vient en premier |
| CSV factures Uber | **Aucun horodatage** — `DateFacture` est une date seule. Le rapprochement se fait par **journée + montant**, jamais par horodatage. Jusqu'à 14 factures le même jour dans l'échantillon |
| CSV factures Uber | `AdresseUtilisateur` n'est **pas** minimisée à la source : le plus souvent une ville, parfois une adresse postale complète. `AdresseFournisseur` est l'adresse **personnelle du chauffeur** |
| Uber | Aucun identifiant commun entre les trois documents. Le rapprochement se fait par horodatage et montant, avec résolution manuelle en dessous du seuil de confiance |
| Uber | Le relevé hebdomadaire donne le net, les factures donnent le brut. La commission ne se calcule qu'en croisant les deux |
| Fuseau | `Europe/Paris`. Une nuit de passage à l'heure d'hiver dure 25 heures : l'amplitude doit le refléter |
| Sessions | Le temps connecté est l'**union** des intervalles, pas leur somme. Être connecté à deux plateformes ne crée pas deux heures dans une heure |

---

## 9. Quand s'arrêter et demander

Ne pas improviser dans ces cas — ouvrir une question sur l'issue :

- un format de fichier dont l'échantillon manque ;
- une règle fiscale ou un taux qui n'est pas dans la spec (les taux sont de la donnée en base, jamais du code) ;
- une métrique dont les données requises sont indisponibles — vérifier §3.7 de la spec avant de conclure ;
- toute décision qui contredirait un ADR : proposer un nouvel ADR plutôt que de contourner.
