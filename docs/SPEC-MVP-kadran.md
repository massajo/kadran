# Kadran — Spec MVP
### Plateforme d'analyse de rentabilité pour chauffeurs VTC

**Version :** 1.8
**Destinataire :** Claude Code (implémentation) + Claude Design (maquettes)
**Stack :** Next.js 15 (App Router) / React 19 / shadcn-ui — Spring Boot 3.5 / Kotlin 2.2 / Gradle 9 / JDK 21 / PostgreSQL 18 / Liquibase

> **v1.7 → v1.8** : §11.2 — le titre de commit porte `[KDN-<n>]` en tête de description, après le préfixe Conventional Commits · §11.1 — le séparateur des fichiers et identifiants Liquibase passe à l'underscore, et les exemples sont renumérotés sur les issues réellement créées · §8.4 — l'identifiant de changeset de `audit_event` portait `kadran:20260818-04-01`, sans trigramme, contraire à l'ADR-007.
>
> **v1.6 → v1.7** : §10.3 **versions figées sur ce qui est réellement livré** — Gradle 9.7, Spring Boot 3.5, Kotlin 2.2, JDK 21, et non plus Spring Boot 3.4 / Kotlin 2.1 · paquet racine `io.korallis.kadran`, `core` pour le shared-kernel · deux incompatibilités de chaîne de construction documentées.
>
> **v1.5 → v1.6** : §3.3 **corrigée sur export réel** — 19 colonnes et non 18, 7 noms erronés, `Tauxtaxe` en chaîne `10%`, `MontantNet`/`MontantBrut` au lieu de `MontantHT`/`MontantTTC` · **les factures ne portent aucun horodatage**, le rapprochement `TripMatcher` se fait par journée et montant, pas par horodatage · `NuméroFacture` retenu comme `externalRef` Uber · spike KDN-66 clos.
>
> **v1.4 → v1.5** : §10.6 pipeline CI/CD complet jusqu'à la publication d'images · EPIC KDN-1 étendu et rendu bloquant · backlog détaillé back/front/infra externalisé dans `BACKLOG.md` · instructions d'implémentation dans `CLAUDE.md` · brief et versionnement des maquettes dans `DESIGN-BRIEF.md`.
>
> **v1.3 → v1.4** : **périmètre v1 restreint à Uber** (ADR-008) · §3.7 métriques disponibles et indisponibles en mono-plateforme · `Amplitude` typée `Observed`/`Floor` (ADR-009) · pipeline d'ingestion générique séparé des profils plateforme · epics Bolt, Heetch et arbitrage reportés en v1.1, profils déjà spécifiés · ordre de traitement recommandé.
>
> **v1.2 → v1.3** : Heetch et Driversnote mappés sur exports réels (§3.5, §3.6) · `SourceCapability` — les trois plateformes ont des capacités disjointes (§4.2) · **journée d'exploitation à seuil configurable, une vacation chevauche minuit** (§4.3) · convention `api`/`spi` (ADR-005) · trigramme `KDN` sur toute référence d'issue (ADR-007) · métrique `C7` barème vs frais réels · localisation de l'export Bolt *Trips*.
>
> **v1.1 → v1.2** : nom Kadran · la sortie remplace la course comme unité d'analyse · mapping Bolt sur export réel · abandon du RLS au profit du `WHERE` explicite · contexte `Audit` · stockage JSONB des spécificités plateforme.

---

## 1. Positionnement et non-goals

### 1.1 La thèse produit

Les plateformes VTC mesurent le **chiffre d'affaires**. Personne ne mesure la **marge**.

Un chauffeur qui affiche 6 000 € de CA mensuel ignore quasi systématiquement :
- combien lui a réellement coûté l'heure qu'il vient de passer en ligne ;
- quelle est sa commission **effective** par plateforme (≠ le taux affiché) ;
- combien lui a rapporté, en net de tout, la sortie de mardi ;
- ce que lui rapporte sa 11ᵉ heure de la journée.

Kadran est un **instrument de mesure de la rentabilité**, pas un outil de gestion. Chaque écran répond à : *« est-ce que ça vaut le coup ? »*

### 1.2 Différenciation

| Acteur | Ce qu'il fait | Ce qu'il ne fait pas |
|---|---|---|
| Uber / Bolt / Heetch | CA brut, silo par plateforme, €/h **hors coûts** | Coûts, marge réelle, vision consolidée |
| Gridwise / Solo (US) | Tracking auto, market data, export fiscal | Coût de revient réel, arbitrage à la marge |
| INCOM / Stairling | Salariat, paie, administratif | Aide à la décision opérationnelle |
| Indy / Abby / Pennylane | Comptabilité, déclarations | Métriques d'exploitation |
| Driversnote | Kilométrage certifié | Revenus, coûts, rentabilité |
| **Kadran** | **Marge nette par sortie / heure / km / plateforme** | Compta, paie, dispatch |

> Bolt affiche déjà un « revenus nets par heure ». C'est un €/h **brut de charges**, calculé sur le temps connecté à Bolt seulement. L'écart avec le `M2` de Kadran est la démonstration du produit.

### 1.3 Non-goals (stricts)

- ❌ Pas un logiciel de comptabilité. Aucun plan comptable, aucun journal. On produit des *provisions* et des *exports*, pas des écritures.
- ❌ Pas de télédéclaration, pas d'API DGFiP/URSSAF.
- ❌ Pas de dispatch, pas de réservation, pas d'app passager.
- ❌ Pas de tracking GPS natif en v1.
- ❌ Pas de conseil fiscal personnalisé. Les provisions sont des **estimations**, libellées comme telles.
- ❌ **Aucune estimation silencieuse.** Toute valeur reconstituée porte son statut d'estimation dans le modèle et dans l'UI.

---

## 2. Personas et périmètre

**Persona MVP** — indépendant (micro, EURL, SASU), 3–4 plateformes, 45–60 h/semaine, un véhicule, utilise Driversnote.
**Persona v2** — gestionnaire de flotte, 3 à 30 véhicules. Le modèle multi-tenant du MVP doit permettre son ajout sans migration structurante.

### 2.3 Périmètre v1.0 — **Uber uniquement** (ADR-008)

| Inclus v1.0 | Reporté v1.1 | Exclu (backlog) |
|---|---|---|
| Import Uber : relevé hebdo, récapitulatif fiscal, factures CSV | Import Bolt (profil §3.4 déjà spécifié) | Connecteurs API |
| Import Driversnote + CSV kilométrage générique | Import Heetch (profil §3.6 déjà spécifié) | Tracking GPS natif |
| Analyse à la **sortie**, à la journée, au mois | Comparaison inter-plateformes `A1`–`A6` | Analyse à la course |
| Modèle de coûts versionné (1 véhicule) | | Multi-véhicules |
| Métriques disponibles en v1 : §3.7 | | Zones, prédictif, benchmark |
| Provisions URSSAF / TVA / trésorerie | | Génération de déclarations |
| Chiffrement PII, affichage masqué, audit trail | | SSO, coffre-fort documentaire |

**Ce que le report ne change pas.** Le modèle `SourceCapability` (§4.2), le pipeline d'ingestion générique et le `MappingProfile` en base restent au périmètre v1 : ils sont ce qui permettra d'ajouter Bolt puis Heetch en profils de données, sans toucher au moteur. Les profils Bolt et Heetch sont déjà entièrement spécifiés (§3.4, §3.6) et n'ont plus qu'à être implémentés le moment venu.

**Le risque à surveiller.** Un produit mono-plateforme n'est plus « la consolidation que personne ne fait » — il devient « un meilleur tableau de bord Uber ». La proposition de valeur v1 repose donc entièrement sur ce qu'Uber ne dit pas : la commission effective réelle, le coût de revient, le kilométrage à vide, la marge par sortie, le revenu réellement disponible. C'est suffisant pour un MVP, mais l'ajout d'une deuxième plateforme doit rester proche dans la feuille de route.

---

## 3. Sources de données réelles

> Section fondée sur des documents réels. Elle prime sur toute supposition.

### 3.1 Uber — relevé hebdomadaire (PDF)

Période lundi 4 h → lundi 4 h. Section *Transactions* : une ligne par événement.

| Colonne | Contenu | Mapping |
|---|---|---|
| Effectué | horodatage d'enregistrement | `recordedAt` |
| Événement | type (`Uber X`, `Pourboire`…) + horodatage de la course | `tripType`, `occurredAt` |
| Vos revenus | montant **net de frais** | `revenue.netToDriver` |
| Versements | virements sortants | trésorerie (v2) |
| Solde | cumul | contrôle de cohérence |

Section *Détails pour : Vos revenus* : Prix total du trajet (net de frais) · Prix de base (net de frais) · UberX Priority · Temps d'attente au lieu de prise en charge · Pourboire.

→ Grain **course**. Fournit le net par course et les pourboires isolés.
→ **Absents** : prix payé par le passager, distance, durée, adresses.

### 3.2 Uber — récapitulatif fiscal (PDF, mensuel)

| Donnée | Exemple | Usage |
|---|---|---|
| Kilométrage total | 1 273 km | `D2` mensuel |
| Nombre de courses | 94 | contrôle de complétude |
| Revenus issus de courses | 2 075,80 HT / 174,38 TVA / 2 250,18 TTC | ventilation TVA |
| Frais de service Uber | 786,91 € | **commission effective** |
| Montant total | 1 470,27 € | rapprochement |

→ Grain **mois**. Sur cet exemple : 786,91 / 2 257,18 = **34,9 %**.

### 3.3 Uber — factures (CSV) — **vérifié sur export réel**

> Section corrigée d'après un export réel `UBER-2026-07-17-2026-08-17-driver-to-rider-invoice.csv`
> (90 lignes, 19 colonnes). **Elle remplace la liste supposée de la v1.4, qui était fausse sur
> 7 des 18 colonnes annoncées.** Décision KDN-66 close.

**Forme du fichier.** UTF-8 **sans BOM**, fins de ligne `LF`, séparateur `,`, valeurs entre
guillemets, décimale `.`, devise `EUR` constante. Nom de fichier porteur de la période :
`UBER-<début>-<fin>-driver-to-rider-invoice.csv`.

| # | Colonne réelle | Contenu | Mapping |
|---|---|---|---|
| 0 | `NuméroFacture` | forme `A-99-9999-9999999`, unique par ligne | **`externalRef`** |
| 1 | `DateFacture` | `yyyy-MM-dd` — **date seule, aucune heure** | `coverage` (journée) |
| 2 | `Devise` | `EUR` | `Money.currency` |
| 3 | `LienFacture` | URL de la facture PDF | `platformExtras`, non suivi |
| 4 | `NomUtilisateur` | prénom + nom du passager | **PII passager** → §8.1 |
| 5 | `AdresseUtilisateur` | **hétérogène** : ville seule, ou adresse complète avec code postal | **PII passager** → §8.1 |
| 6 | `IdentifiantTaxeUtilisateur` | vide sur 85 lignes / 90 | ignoré si vide |
| 7 | `ÉtiquetteTaxeUtilisateur` | liste : `n° TVA,SIREN,Electronic Address` | apparié à la colonne 6 |
| 8 | `NomFournisseur` | raison sociale du chauffeur | `platformExtras` |
| 9 | `AdresseFournisseur` | **adresse du chauffeur** — constante | **PII chauffeur** → §8.1 |
| 10 | `IdentifiantTaxeFournisseur` | liste `SIREN,TVA` — **ordre variable** | `platformExtras` |
| 11 | `ÉtiquetteTaxeFournisseur` | liste d'étiquettes — **ordre variable** | apparié à la colonne 10 |
| 12 | `DescriptionArticle` | `Prix du service de transport` | `ServiceNature` |
| 13 | `Quantité` | `1` sur toutes les lignes | — |
| 14 | `MontantNet` | montant **HT** | base HT |
| 15 | `Tauxtaxe` | **`10%` — chaîne avec le signe `%`** | `ServiceNature` = `POINT_TO_POINT` |
| 16 | `MontantTaxe` | TVA | `F2` collectée |
| 17 | `MontantBrut` | montant **TTC** | **`R1`** |
| 18 | `MontantTotal` | **strictement identique à `MontantBrut`** (90/90) | contrôle |

**Écarts par rapport à la liste supposée en v1.4.** `NumeroFacture` → `NuméroFacture` (accentué) ·
`EtiquetteTaxeUtilisateur` → `ÉtiquetteTaxeUtilisateur` (`É` majuscule accentué) · `Quantite` →
`Quantité` · `TauxTaxe` → **`Tauxtaxe`** (second `t` minuscule) · `MontantHT` → `MontantNet` ·
`MontantTTC` → `MontantBrut` · `PaysFournisseur` → `AdresseFournisseur` · colonnes `MontantTotal`
ajoutée · ordre des colonnes 5 à 7 différent. Les noms sont à reprendre **exactement**, accents et
casse compris.

**Contrôles d'intégrité vérifiés sur l'échantillon (90/90 lignes, écart nul) :**
- `MontantNet + MontantTaxe = MontantBrut`
- `MontantBrut = MontantTotal`
- `MontantTaxe = MontantNet × 10 %` à ±0,01 près

**Pièges de parsing.**
- `Tauxtaxe` vaut la **chaîne** `10%`, pas `0.10` ni `10`. Le `%` fait partie de la valeur.
- `IdentifiantTaxeFournisseur` et `ÉtiquetteTaxeFournisseur` sont deux listes parallèles séparées par
  des virgules **dont l'ordre varie d'une ligne à l'autre** (53 lignes dans un ordre, 37 dans
  l'autre). Il faut **apparier étiquette et valeur par position dans leur propre liste**, jamais
  supposer que le SIREN vient en premier.
- `AdresseUtilisateur` n'est **pas** minimisée à la source : la plupart des lignes ne portent qu'une
  ville, mais au moins une porte une adresse postale complète. Le traitement §8.1 est indispensable.
- `AdresseFournisseur` est l'adresse personnelle du chauffeur — la même que le libellé `Home` des
  exports Driversnote (§3.5).

⚠️ **Aucun horodatage.** `DateFacture` est une **date seule**. Les factures ne portent ni heure, ni
distance, ni durée, ni adresse de prise en charge ou de dépose. Conséquences directes :

- Le rapprochement avec le relevé hebdomadaire (§7.7, `TripMatcher`) ne peut **pas** se faire « par
  horodatage et montant » comme annoncé : il se fait par **journée + montant**, ce qui est bien plus
  faible. L'échantillon compte jusqu'à **14 factures sur une même journée**, dont plusieurs à des
  montants proches. Le taux de résolution manuelle sera nettement supérieur à ce qu'anticipait la
  spec, et le seuil de confiance doit être calibré en conséquence.
- `M4` et `M5` restent inactives : pas de distance par course (ADR-004).

**Totaux constatés sur la période du 17/07 au 17/08/2026** — 90 courses :
Σ HT = 1 292,65 € · Σ TVA = 129,31 € · Σ TTC = **1 421,96 €**. Ce Σ TTC est le `R1` de la période,
à croiser avec le net du relevé hebdomadaire pour obtenir `R3`.

→ Grain **course**. Seule source du prix payé par le passager. Contient de la **PII passager**
et de la **PII chauffeur** (§8.1).

### 3.4 Bolt — export « Revenus par chauffeur » (CSV)

Séparateur `,`, valeurs entre guillemets, **BOM UTF-8 en tête** (à consommer explicitement), décimale `.`, en-têtes suffixés de l'unité après un `|`.

| Colonne source | Mapping canonique |
|---|---|
| `Chauffeur `, `Adresse e-mail`, `Téléphone` | PII chauffeur → §7 (noter l'espace final du premier en-tête) |
| `Identifiant chauffeur`, `Identifiant individuel` | `externalRefs` |
| `Revenus bruts (total)\|€` | `R1` |
| `Revenus bruts (paiement dans l'application)\|€` / `(paiement par espèces)\|€` | ventilation encaissement |
| `Montant des espèces perçu\|€` | trésorerie |
| `Pourboires des clients\|€` | `R5` |
| `Revenus des campagnes\|€` | `R6` |
| `Remboursements de frais\|€`, `Frais d'annulation\|€`, `Frais de péage\|€`, `Frais de réservation\|€` | `R8` et suppléments |
| `Total des frais\|€` | total prélevé |
| `Frais de service\|€` | **commission → `R3`** |
| `Remboursements aux clients\|€`, `Autres frais\|€` | ajustements |
| `Revenus nets\|€` | `R2` |
| `Paiement prévu\|€` | trésorerie |
| `Revenus bruts par heure\|€/h`, `Revenus nets par heure\|€/h` | **`M1` déclaré par la plateforme** — à confronter à `M2` |
| `Réduction sur commission (dans l'application)\|€` / `(en espèces)\|€` | ajustements |
| `Courses terminées` | nb courses |
| `Temps en ligne (min)` | **`T2` par plateforme** |
| `Distance totale de course\|km`, `Distance moyenne de course\|km` | **`D2` par plateforme** |
| `Utilisation\|%` | occupancy déclarée — à confronter à `T7`/`T8` |
| `Taux d'acceptation total\|%`, `Taux d'acceptation effectif\|%` | `A5` |
| `Taux de courses terminées (toutes)\|%` / `(acceptées)\|%` | `A5` |
| `Niveau`, `Catégories actives`, `Courses en espèces activées`, `Score chauffeur\|%`, `Score moyen du chauffeur\|★` | **`platformExtras` (JSONB)** — sans équivalent Uber |

Contrôle d'intégrité à implémenter : `Revenus bruts (total) − Total des frais = Revenus nets`. Vérifié sur l'échantillon (26,20 − 6,29 = 19,91).

⚠️ **Grain = période d'export.** Une ligne par chauffeur pour toute la période demandée. L'assistant d'import doit donc **guider vers un export hebdomadaire**, et détecter/avertir quand la période importée dépasse 8 jours : au-delà, l'analyse par créneau et par jour devient impossible.

**Export Bolt *Trips*, au grain course.** Chemin : `fleets.bolt.eu` → onglet **Trips / Courses** → sélection de la période → téléchargement CSV. D'après la documentation Bolt, le tableau contient les adresses de prise en charge, le statut de la course, les pourboires, les prix et **les distances**, avec recherche par nom de chauffeur.

C'est la seule source connue offrant la distance **par course** pour l'une des trois plateformes. Elle rouvrirait `M4` et `M5` pour Bolt uniquement — et, par extension, permettrait d'étalonner le rapport distance/tarif afin de qualifier (sans jamais l'imputer, cf. ADR-004) le comportement des autres plateformes. Les adresses de prise en charge sont de la **PII passager** et relèvent de §8.1.

### 3.5 Driversnote — export CSV (confirmé)

Colonnes : `Début`, `Fin`, `De`, `À`, `Raison`, `Commentaire`, `Distance`, `Taux`, `Remboursement`.

| Colonne | Exemple | Mapping |
|---|---|---|
| `Début` / `Fin` | `02/06/2026` → `03/06/2026` | `Outing.window` |
| `De` / `À` | `Home, 1 Rue Louis Antoine de Bougainville, 91300 Massy` | libellés — **PII, réduits au code postal** (§8.1) |
| `Raison` | `Professionnel` | `TripPurpose` |
| `Commentaire` | libre | `platformExtras` |
| `Distance` | `130.68` | `Outing.distance` (décimale `.`) |
| `Taux` | `0.47` | barème appliqué — **arrondi à 2 décimales** |
| `Remboursement` | `61.81` | montant barème réel |

**Trois constats déterminants.**

**a) Une sortie chevauche minuit.** L'échantillon va du 02/06 au 03/06 : une vacation de nuit. Voir §4.3 — la journée d'exploitation ne peut pas être la journée calendaire.

**b) `Taux` est arrondi, `Remboursement` ne l'est pas.** 130,68 × 0,47 = 61,42, alors que le fichier indique 61,81 (taux réel 0,47298). **Ne jamais recalculer `Remboursement` à partir de `Taux`** : lire la valeur fournie. Contrôle d'intégrité à ne pas implémenter comme une égalité stricte.

**c) Statut des horaires — à vérifier avant l'issue KDN-28.** Les colonnes `Début` et `Fin` s'affichent comme des dates dans un tableur, mais Excel masque systématiquement la composante horaire. **Ouvrir le CSV dans un éditeur de texte, pas dans Excel**, pour trancher.
- Si horaires présents → grain `OUTING`, métriques `M11`–`M13` exactes.
- Si dates seules → grain `DAY`, avec gestion du chevauchement de minuit par la règle §4.3.

Le parseur doit accepter les deux formes : `dd/MM/yyyy` et `dd/MM/yyyy HH:mm`, et positionner `Outing.window` à `null` dans le premier cas plutôt que d'inventer 00:00.

→ Grain `OUTING` ou `DAY`. Source de `D1`, donc de `D4` par différence avec les agrégats plateformes.

Import CSV kilométrage générique obligatoire en parallèle, avec assistant de correspondance de colonnes.

### 3.6 Heetch — export CSV (confirmé)

Colonnes : `Mois`, `Moyen de paiement`, `Nombre de transactions`, `Chiffre d'affaires TTC`, `TVA`, `Chiffre d'affaire HT`, `Frais de service`, `Frais de service TVA`.

| Colonne | Exemple | Mapping |
|---|---|---|
| `Mois` | `2026-2` | période — **non complété par un zéro** |
| `Moyen de paiement` | `Carte bancaire` | dimension d'encaissement |
| `Nombre de transactions` | `1` | nb courses |
| `Chiffre d'affaires TTC` | `€21.00` | `R1` |
| `TVA` | `€1.91` | `F2` collectée |
| `Chiffre d'affaire HT` | `€19.09` | base HT (noter le singulier — faute d'orthographe de la source, à reproduire telle quelle) |
| `Frais de service` | `€5.46` | **commission → `R3`** |
| `Frais de service TVA` | `€0.00` | autoliquidation |

Commission effective sur l'échantillon : 8,58 / 33,00 = **26,0 %**.

**Pièges de parsing :**
- Symbole `€` **préfixé et collé** au montant, décimale `.`.
- `Mois` au format `yyyy-M` sans zéro de complétion (`2026-2`, pas `2026-02`).
- **Une ligne `Total` en fin de fichier**, à détecter et écarter — elle a une cellule `Mois` valant `Total` et des colonnes vides.
- Le libellé `Chiffre d'affaire HT` est au singulier alors que `Chiffre d'affaires TTC` est au pluriel. Ne pas « corriger » dans le profil de mapping.

⚠️ **Heetch ne fournit ni temps ni distance.** Aucune colonne de temps en ligne, aucune distance. Conséquence directe : `M1`, `M2`, `T2`, `D2` sont **incalculables pour Heetch**. La plateforme contribue au revenu consolidé, à la commission comparée et à la TVA, mais ne peut pas apparaître dans un classement `€/h` par plateforme.

C'est la justification empirique du modèle `SourceCapability` (§4.2) : trois plateformes, trois jeux de capacités disjoints. Le moteur de métriques doit refuser proprement, pas produire un zéro.

| Capacité | Uber | Bolt | Heetch |
|---|---|---|---|
| Revenu brut / net | ✅ | ✅ | ✅ |
| Commission | ✅ (mois) | ✅ | ✅ |
| Ventilation TVA | ✅ | ⚠️ partielle | ✅ |
| Nombre de courses | ✅ | ✅ | ✅ |
| Temps en ligne | ❌ | ✅ | ❌ |
| Distance | ✅ (mois) | ✅ | ❌ |
| Grain le plus fin | course | période d'export | **mois** |
| Moyen de paiement | ❌ | ✅ | ✅ |

### 3.7 Ce qu'Uber seul permet — et ne permet pas

Périmètre v1 : Uber + Driversnote + modèle de coûts. Le tableau ci-dessus montre une limite qu'il faut regarder en face : **Uber ne fournit aucun temps de connexion.**

**Disponible en v1 :**

| Métriques | Source |
|---|---|
| `R1`–`R10` revenus, pourboires, incitations, suppléments | relevé hebdo + factures |
| **`R3`/`R4` commission effective et écart au taux affiché** | récapitulatif fiscal + factures |
| `F1`–`F6` provisions, TVA 10/20, revenu réellement disponible, seuils | récapitulatif fiscal |
| `D1`, `D2`, `D4`, `D5` distance totale, facturée, à vide, taux productif | Driversnote + récapitulatif fiscal |
| `C1`–`C7` coûts, coût mort, barème vs frais réels | modèle de coûts |
| `M6`, `M7` seuil de rentabilité, point mort | coûts + revenus |
| **`M11`–`M13` marge par sortie, €/h de la sortie, sortie non rentable** | Driversnote + relevé hebdo |
| Distribution du revenu net par heure et jour de semaine | horodatage des courses du relevé |
| `S1`–`S3` amplitude, volatilité, dépendance | — |

**Indisponible en v1 :**

| Métrique | Raison |
|---|---|
| `A1`–`A6` arbitrage entre plateformes | une seule plateforme |
| `T2` temps connecté, `T7` taux d'occupation | Uber ne fournit pas de temps en ligne |
| `M1` €/h déclaré par la plateforme, `M3` écart déclaré/réel | dépend de `T2` |
| `M4`, `M5` marge et perte par course | pas de distance par course (ADR-004) |

**Conséquence sur `M2`, la métrique produit.** `M2` a pour dénominateur l'amplitude `T1`, qui ne peut plus venir des sessions plateforme. Deux sources, dans cet ordre :

```kotlin
sealed interface Amplitude {
    val duration: Duration
    data class Observed(override val duration: Duration) : Amplitude   // fenêtre Driversnote
    data class Floor(override val duration: Duration) : Amplitude      // première → dernière course Uber
}
```

- `Observed` — la fenêtre de la sortie Driversnote, si les horaires existent (KDN-37). Valeur exacte.
- `Floor` — l'écart entre la première et la dernière course Uber de la journée d'exploitation. C'est une **borne inférieure**, pas une estimation : le temps d'attente avant la première course et après la dernière n'y figure pas, donc `M2` calculé sur cette base est **optimiste**.

Ce n'est pas une violation d'ADR-004 : rien n'est réparti au prorata, et la nature de la valeur est portée par le type. Mais l'UI doit l'afficher explicitement — « au mieux 14,20 €/h, vraisemblablement moins » — et jamais comme un chiffre nu. Si les horaires Driversnote existent, `Floor` n'est qu'un secours.

---

## 4. La granularité — décision de conception centrale

Les sources ne partagent pas le même grain. Le modèle doit l'assumer explicitement plutôt que de le masquer.

```kotlin
enum class Grain(val order: Int) {
    TRIP(0),      // Uber relevé + factures
    OUTING(1),    // Driversnote — une sortie
    DAY(2),
    PERIOD(3)     // Bolt fleet export, Uber récapitulatif fiscal
}
```

**Règle : une métrique déclare le grain minimal qu'elle exige. Si les données disponibles sont d'un grain plus grossier, la métrique n'est pas calculée — elle n'est jamais estimée par répartition.**

```kotlin
data class MetricDefinition(
    val id: MetricId,
    val requiredGrain: Grain,
    val requiredSources: Set<SourceCapability>,
    val minimumCompleteness: CompletenessLevel
)
```

### 4.1 L'unité économique est la sortie, pas la course

C'est le pivot de la v1.2. Driversnote produit des sorties ; les plateformes produisent des revenus horodatés. Croiser les deux sur la fenêtre temporelle donne une marge **exacte** par sortie, sans aucune estimation :

> *« Ta sortie de mardi : 4 h 12, 130,7 km, 61,80 € de coûts variables, 148,30 € net plateformes → 86,50 € de marge, soit 20,60 €/h. »*

C'est plus actionnable qu'une marge par course, et c'est calculable. `M4` et `M5` (grain `TRIP`) restent dans le catalogue en P2, activables uniquement si une source au grain course avec distance apparaît — export Trips de Bolt, ou export RGPD Uber.

### 4.2 `SourceCapability` — ce que chaque source sait dire

Le tableau comparatif de §3.6 démontre que les capacités sont disjointes. Elles doivent être déclaratives et vérifiées avant tout calcul.

```kotlin
enum class SourceCapability {
    GROSS_REVENUE, NET_REVENUE, COMMISSION, VAT_BREAKDOWN,
    TRIP_COUNT, ONLINE_TIME, DISTANCE, PAYMENT_METHOD,
    TIP, INCENTIVE, PER_TRIP_TIMESTAMP, COUNTERPARTY_IDENTITY
}

data class PlatformProfile(
    val platform: PlatformId,
    val finestGrain: Grain,
    val capabilities: Set<SourceCapability>
)
```

Quand une métrique exige une capacité absente, le moteur renvoie `MetricUnavailable(reason)` — **jamais zéro, jamais null silencieux**. L'UI affiche « non disponible pour Heetch : cette plateforme ne fournit pas de temps en ligne », ce qui est une information utile en soi.

### 4.3 La journée d'exploitation n'est pas la journée calendaire

L'échantillon Driversnote va du **02/06 au 03/06** : une vacation de nuit. C'est le cas nominal en VTC, pas l'exception. Découper sur minuit couperait chaque nuit de travail en deux, produisant deux demi-journées aux métriques absurdes (amplitude tronquée, coût fixe compté deux fois).

```kotlin
data class BusinessDayPolicy(val cutoff: LocalTime = LocalTime.of(4, 0))

fun Instant.toBusinessDay(policy: BusinessDayPolicy, zone: ZoneId): LocalDate =
    atZone(zone).minusHours(policy.cutoff.hour.toLong()).toLocalDate()
```

- Défaut **04:00**, cohérent avec la frontière hebdomadaire d'Uber (lundi 4 h → lundi 4 h) : les deux découpages s'alignent, ce qui simplifie le rapprochement.
- Paramétrable par tenant : un chauffeur de jour peut préférer 00:00.
- **Toutes** les projections journalières (`T1`, `C4`, `M2`, `M8`) utilisent la journée d'exploitation. Le champ `outing_date` en base stocke la journée d'exploitation, pas la date calendaire de début.
- Une sortie chevauchant le seuil est rattachée à la journée d'exploitation de son **début**.
- Fuseau : `Europe/Paris`, avec gestion explicite des changements d'heure — une nuit de passage à l'heure d'hiver dure 25 heures et l'amplitude doit le refléter.

### 4.4 Agrégat `Outing`

```kotlin
class Outing(
    val id: OutingId,
    val tenantId: TenantId,
    val businessDay: LocalDate,         // journée d'exploitation, cf. §4.3
    val window: WorkPeriod?,            // null si la source ne fournit pas d'horaires
    val spansMidnight: Boolean,
    val distance: Distance,
    val purpose: TripPurpose,           // PROFESSIONNEL | PERSONNEL
    val startLabel: String?,            // "Home" — PII, cf. §8.1
    val endLabel: String?,
    val mileageAllowance: Money?,       // colonne Remboursement Driversnote
    val source: MileageSource,          // DRIVERSNOTE | CSV_GENERIC | MANUAL
    val linkedRevenue: LinkedRevenue?
)
```

Les sorties `PERSONNEL` sont exclues de tous les calculs de rentabilité mais conservées pour le total odométrique.

---

## 5. Qualité de la donnée

Une métrique calculée sur des données incomplètes est pire qu'absente. **Toute métrique exposée porte son taux de complétude.**

```kotlin
data class DataCompleteness(
    val revenueCoverage: Ratio,
    val timeCoverage: Ratio,
    val distanceCoverage: Ratio,
    val availableGrain: Grain,
    val platformsCovered: Set<PlatformId>,
    val platformsDeclaredActive: Set<PlatformId>
) {
    val level: CompletenessLevel get() = when {
        overall >= 0.95 -> RELIABLE
        overall >= 0.75 -> INDICATIVE
        else            -> INSUFFICIENT
    }
}
```

- `RELIABLE` → valeur affichée normalement.
- `INDICATIVE` → badge + fourchette, jamais une valeur ponctuelle.
- `INSUFFICIENT` → métrique **masquée**, remplacée par un CTA d'import ciblé.

---

## 6. Catalogue de métriques

Chaque métrique porte son grain minimal.

### 6.1 Temps

| Id | Métrique | Formule | Grain | Prio |
|---|---|---|---|---|
| `T1` | Amplitude | dernière déconnexion − première connexion | DAY | P0 |
| `T2` | Temps connecté | **union** des intervalles (Bolt : `Temps en ligne`) | PERIOD | P0 |
| `T4` | Temps passager | Σ durées de course | TRIP | P1 |
| `T5` | Temps d'attente | T2 − T4 | TRIP | P1 |
| `T6` | Temps hors ligne travaillé | déclaratif | DAY | P1 |
| `T7` | Taux d'occupation | T4 / T2 | TRIP | P1 |
| `T8` | **Taux d'utilisation économique** | T4 / T1 | TRIP | P1 |
| `T10` | **Durée de sortie** | fenêtre Driversnote | OUTING | P0 |

### 6.2 Distance

| Id | Métrique | Formule | Grain | Prio |
|---|---|---|---|---|
| `D1` | Km totaux | Σ sorties professionnelles | OUTING | P0 |
| `D2` | Km facturés | agrégats plateformes | PERIOD | P0 |
| `D4` | **Km à vide** | D1 − D2 | PERIOD | P0 |
| `D5` | **Taux de km productifs** | D2 / D1 | PERIOD | P0 |

### 6.3 Revenu

| Id | Métrique | Formule | Grain | Prio |
|---|---|---|---|---|
| `R1` | CA brut passager | factures Uber / `Revenus bruts` Bolt | PERIOD | P0 |
| `R2` | CA net reversé | relevé Uber / `Revenus nets` Bolt | PERIOD | P0 |
| `R3` | **Commission effective** | 1 − (R2 / R1), par plateforme | PERIOD | P0 |
| `R4` | Écart commission effective / affichée | R3 − taux nominal | PERIOD | P0 |
| `R5` | Pourboires | isolé | PERIOD | P0 |
| `R6` | Revenus d'incitation | campagnes, quests, garanties | PERIOD | P0 |
| `R7` | Dépendance aux incitations | R6 / R2 | PERIOD | P1 |
| `R8` | Suppléments et refacturations | péages, attente, réservation | PERIOD | P1 |
| `R9` | Prix moyen par course | R2 / nb courses | PERIOD | P0 |
| `R10` | Indice de concentration plateforme | HHI | PERIOD | P1 |

### 6.4 Coûts

**Fixes** (€/mois) : LLD ou amortissement + financement, RC circulation, RC Pro, REVTC amorti, carte VTC amortie, téléphonie, adhésions, frais bancaires, comptable.
**Variables** (€/km ou €/unité) : carburant/électricité, entretien, pneumatiques, lavage, péages non refacturés, stationnement.

| Id | Métrique | Formule | Grain | Prio |
|---|---|---|---|---|
| `C1` | Coût variable au km | Σ variables / D1 | PERIOD | P0 |
| `C2` | Coût fixe journalier | fixes mensuels / jours travaillés | PERIOD | P0 |
| `C3` | **Coût kilométrique complet** | (fixes + variables) / D1 | PERIOD | P0 |
| `C4` | **Coût mort** | C2 sur une journée non travaillée | DAY | P0 |
| `C5` | Coût du km à vide | D4 × C1 | PERIOD | P0 |
| `C6` | Coût énergétique au 100 km | suivi séparé | PERIOD | P1 |
| `C7` | **Barème kilométrique vs frais réels** | Σ `Remboursement` Driversnote − (D1 × C3) | PERIOD | P1 |

> `C7` exploite la colonne `Remboursement` de Driversnote, qui applique le barème fiscal. Comparée au coût réel `C3`, elle répond à une question que personne n'outille : *« ai-je intérêt au barème ou aux frais réels cette année ? »* Métrique à réserver aux formes juridiques pour lesquelles l'arbitrage existe — sans équivalent en micro-entreprise, où l'abattement est forfaitaire.

### 6.5 Marge — métriques signature

| Id | Métrique | Formule | Grain | Prio |
|---|---|---|---|---|
| `M1` | €/h déclaré par la plateforme | importé (Bolt) ou R2 / T2 | PERIOD | P0 |
| `M2` | **€/h net réel** | (R2 − variables − provisions) / **T1** | DAY | P0 |
| `M3` | **Écart déclaré / réel** | M1 − M2 | DAY | P0 |
| `M6` | Seuil de rentabilité horaire | (C2 / h travaillées) + (C1 × vitesse moy.) | PERIOD | P0 |
| `M7` | Point mort mensuel | CA couvrant fixes + variables + provisions | PERIOD | P0 |
| `M10` | Marge par plateforme × période | M2 ventilé | PERIOD | P0 |
| `M11` | **Marge nette par sortie** | revenus fenêtre − (km × C1) − quote-part fixe | OUTING | P0 |
| `M12` | **€/h net de la sortie** | M11 / T10 | OUTING | P0 |
| `M13` | **Sortie non rentable** | M11 < 0 | OUTING | P0 |
| `M8` | Rendement marginal horaire | marge de la dernière heure | DAY | P1 |
| `M4` | Marge nette par course | — | TRIP | P2 |
| `M5` | Taux de courses à perte | — | TRIP | P2 |

> `M2` est **la** métrique produit : dénominateur = amplitude, pas temps connecté.
> `M3` est le moment de bascule : Bolt annonce 4,87 €/h net, Kadran affiche le vrai chiffre.
> `M11`–`M13` sont le nouveau cœur opérationnel, exact et sans estimation.
> `M4`/`M5` restent inactives tant qu'aucune source au grain course avec distance n'existe. **Ne pas les implémenter par répartition proportionnelle.**

### 6.6 Arbitrage plateforme

| Id | Métrique | Grain | Prio |
|---|---|---|---|
| `A1` | Classement plateformes par `M2` | PERIOD | P0 |
| `A2` | Heatmap `M2` × jour × créneau | DAY | P1 |
| `A3` | Commission effective comparée | PERIOD | P0 |
| `A4` | Délai de paiement moyen constaté | PERIOD | P1 |
| `A5` | Acceptation / annulation / complétion | PERIOD | P1 |
| `A6` | Écart `Utilisation` déclarée vs `T8` réel | PERIOD | P1 |

### 6.7 Fiscal & trésorerie

| Id | Métrique | Prio |
|---|---|---|
| `F1` | Provision cotisations sociales | P0 |
| `F2` | TVA collectée / déductible / solde, ventilée **10 % vs 20 %** | P0 |
| `F3` | Provision impôt sur le revenu | P1 |
| `F4` | **Revenu réellement disponible** = R2 − coûts − F1 − F2 − F3 | P0 |
| `F5` | Suivi seuils (franchise TVA, plafond micro) + projection | P0 |
| `F6` | Prévisionnel de trésorerie 90 jours | P1 |

TVA : transport point à point 10 %, mise à disposition forfaitaire 20 %. Champ `ServiceNature` porté par la ligne de revenu, défaut par plateforme, surcharge manuelle. Le `TauxTaxe` des factures Uber fait foi quand disponible.

`F4` est le chiffre héros du dashboard.

### 6.8 Risque

| Id | Métrique | Prio |
|---|---|---|
| `S1` | Amplitude moyenne, jours consécutifs travaillés | P1 |
| `S2` | Volatilité du revenu hebdomadaire | P1 |
| `S3` | Alerte de dépendance (≥ 70 % du CA sur une plateforme) | P1 |

---

## 7. Modèle de domaine

### 7.1 Carte des contextes

```
┌──────────────────┐  ┌────────────────────┐  ┌──────────────────┐
│ Identity &       │  │ Ingestion (ACL)    │  │ Privacy          │
│ Tenancy          │  │ upstream           │  │ (generic)        │
└────────┬─────────┘  └─────────┬──────────┘  └────────┬─────────┘
         │                       │                      │
         │                       ▼                      │
         │            ┌────────────────────┐            │
         │            │ Activity (core)    │◄───────────┘
         │            │ Outing, WorkDay,   │
         │            │ RevenueRecord      │
         │            └─────────┬──────────┘
   ┌─────┴──────┐               │
   ▼            ▼               ▼
┌──────────┐ ┌─────────────┐ ┌────────────────────┐
│CostModel │ │FiscalProfile│ │ Performance        │
└────┬─────┘ └─────┬───────┘ └─────────┬──────────┘
     └─────────────┴───────────────────┘
                   │
                   ▼
         ┌────────────────────┐
         │ Audit (generic)    │  ← observe tous les contextes
         └────────────────────┘
```

`Ingestion` est un **Anti-Corruption Layer strict** : aucun type Uber/Bolt/Heetch/Driversnote ne franchit sa frontière.

### 7.2 Shared Kernel

```kotlin
@JvmInline value class TenantId(val value: UUID)
@JvmInline value class DriverId(val value: UUID)

data class Money(val amountCents: Long, val currency: Currency)
data class Ratio(val value: BigDecimal)
data class Distance(val meters: Long)
data class WorkPeriod(val from: Instant, val to: Instant)

enum class PlatformId { UBER, BOLT, HEETCH, FREENOW, ALLOCAB, DIRECT, OTHER }
enum class ServiceNature { POINT_TO_POINT, DISPOSAL }
enum class Grain { TRIP, OUTING, DAY, PERIOD }
```

**Jamais de `Double` pour de la monnaie.**

### 7.3 `Activity` — agrégats

```kotlin
class RevenueRecord(                        // remplace Trip comme porteur de revenu
    val id: RevenueRecordId,
    val tenantId: TenantId,
    val platform: PlatformId,
    val grain: Grain,                       // TRIP pour Uber, PERIOD pour Bolt
    val coverage: WorkPeriod,               // instant unique ou période
    val externalRefs: Set<ExternalRef>,
    val amounts: RevenueBreakdown,          // brut, net, commission, pourboires, incitations, suppléments
    val vat: VatBreakdown?,
    val counts: ActivityCounts?,            // courses, temps en ligne, distance
    val platformExtras: JsonNode,           // §7.6
    val provenance: Set<DataProvenance>
)
```

```kotlin
class Outing(...)                           // cf. §4.2

class WorkDay(
    val id: WorkDayId,
    val tenantId: TenantId,
    val date: LocalDate,
    val outings: List<OutingId>,
    val sessions: List<OnlineSession>,      // chevauchements possibles
    val offlineWork: List<OfflineActivity>
) {
    fun amplitude(): Duration
    fun connectedTime(): Duration           // IntervalUnion, PAS une somme
}
```

> ⚠️ `IntervalUnion` : connecté à Uber et Bolt simultanément ≠ deux heures dans une heure. Property-based tests obligatoires. Source de bug n°1.

Événements : `RevenueRecorded`, `OutingRecorded`, `WorkDayClosed`, `RevenueLinkedToOuting`, `DataGapDetected`.

### 7.4 `CostModel`

Versionné par `validFrom` / `validTo`, jamais muté. Changer d'assurance en juin ne réécrit pas la marge de mars. Toute métrique résout le modèle **actif à la date**.

### 7.5 `FiscalProfile`

`legalForm`, `vatRegime`, `socialScheme`, `rates` (versionnés par date), `thresholds`. Les taux sont **de la configuration en base**, seedée par changeset Liquibase, jamais en dur.

### 7.6 Stockage JSONB des spécificités plateforme

Les plateformes n'exposent pas les mêmes champs : Bolt fournit `Niveau`, `Score chauffeur`, `Catégories actives`, `Courses en espèces activées` ; Uber n'a aucun équivalent ; Heetch aura d'autres champs encore.

**Principe : trois zones de stockage distinctes.**

| Zone | Contenu | Colonne |
|---|---|---|
| **Canonique** | Champs partagés par toutes les plateformes, typés, indexés, utilisés par les métriques | colonnes SQL classiques |
| **Extras** | Champs spécifiques plateforme, normalisés en clés stables, exploitables en UI | `platform_extras JSONB` |
| **Brut** | Ligne source intégrale, telle qu'importée | `raw_payload JSONB` |

```sql
platform_extras JSONB NOT NULL DEFAULT '{}'::jsonb,
raw_payload     JSONB NOT NULL,
CREATE INDEX idx_revenue_extras ON revenue_record USING GIN (platform_extras jsonb_path_ops);
```

Règles :
- Une métrique du §6 ne lit **jamais** `raw_payload`. Si un champ devient nécessaire à une métrique, il est promu en colonne canonique par un changeset.
- `raw_payload` est conservé pour permettre le **rejeu** d'un import après correction d'un profil de mapping, sans redemander le fichier.
- Les clés de `platform_extras` sont préfixées : `bolt.driverScore`, `bolt.level`, `uber.priorityFee`.
- Purge de `raw_payload` alignée sur la politique de rétention (§8.4).

### 7.7 `Ingestion`

```kotlin
class ImportBatch(
    val id: ImportBatchId,
    val tenantId: TenantId,
    val source: SourceDescriptor,           // plateforme + type doc + version détectée
    val rawFileRef: BlobRef,
    val declaredPeriod: WorkPeriod?,
    val detectedGrain: Grain,
    val status: ImportStatus,               // UPLOADED → PARSED → MAPPED → LINKED → VALIDATED → COMMITTED | FAILED
    val mappingProfileId: MappingProfileId,
    val anomalies: List<ImportAnomaly>
)
```

`MappingProfile` est **une donnée en base**, pas du code : descripteur JSON colonnes → champs canoniques + transformations. Les formats changent sans préavis ; un profil se corrige en production sans redéploiement.

Pipeline : `Detect → Parse → Map → Link → Validate → Commit`.
- CSV : `commons-csv`, gestion explicite du BOM UTF-8 et des en-têtes à espace terminal.
- PDF : `pdfbox`, `tabula-java` en secours.
- `Link` : rattachement temporel des revenus aux sorties. Seuil de confiance ; en dessous, file de résolution manuelle.
- Recouvrement de périodes : *last-write-wins par `externalRef`* + rapport de divergence exposé.
- Contrôles d'intégrité par source (ex. Bolt : `bruts − frais = nets`) exécutés avant `VALIDATED`.

### 7.8 `Performance`

Projections, pas d'agrégat. `MetricDefinition` déclarative avec `requiredGrain`. Table `daily_metrics_projection`, vues matérialisées semaine/mois, rafraîchissement **déclenché par événement**. Endpoint de **traçabilité** obligatoire : pour toute métrique, retourner les enregistrements sources et le modèle de coûts qui l'ont produite.

---

## 8. Confidentialité et audit

### 8.1 Minimisation à l'import

Option de tenant `retainCounterpartyIdentity`, **désactivée par défaut**. Quand elle est off :
- Uber : `NomUtilisateur` et `AdresseUtilisateur` ne sont jamais persistés ; seul un pseudonyme HMAC stable est conservé (détection des clients récurrents sans stockage d'identité).
- Bolt : `Adresse e-mail` et `Téléphone` du chauffeur idem.
- Driversnote : `De` / `À` contiennent des adresses personnelles (« Home, 1 Rue…, 91300 Massy ») → réduits au code postal et à la ville.

La donnée absente ne fuit pas. C'est la première ligne de défense.

### 8.2 Chiffrement de ce qui est conservé

```kotlin
@Encrypted(classification = PII_HIGH)  val passengerName: EncryptedString?
@BlindIndex(algorithm = HMAC_SHA256)   val passengerNameIndex: ByteArray?
```

AES-256-GCM, enveloppe à deux niveaux : **DEK par tenant**, chiffrée par une KEK applicative (variable d'environnement en dev, KMS en production). Stockage `bytea`. Chiffrement appliqué dans le convertisseur de persistance — le domaine manipule des types clairs. `raw_payload` contenant de la PII est chiffré au même titre.

### 8.3 Affichage masqué

Rendu par défaut `M*** D***`, `91300 Massy`. Bascule « afficher en clair » : ré-authentification, expiration à 15 min, **journalisation systématique**.

### 8.4 Audit trail — journal de toutes les opérations

Table append-only, partitionnée par mois.

```sql
--changeset kadran:20260818_KDN-21_01 labels:audit context:all
CREATE TABLE audit_event (
    id              BIGSERIAL,
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    tenant_id       UUID,                       -- null pour les événements hors tenant
    actor_id        UUID,
    actor_type      VARCHAR(24) NOT NULL,       -- USER | SYSTEM | JOB | ANONYMOUS
    action          VARCHAR(64) NOT NULL,       -- ex. IMPORT_COMMITTED, COST_MODEL_UPDATED
    entity_type     VARCHAR(64),
    entity_id       VARCHAR(64),
    outcome         VARCHAR(16) NOT NULL,       -- SUCCESS | FAILURE | DENIED
    correlation_id  UUID NOT NULL,
    ip_address      INET,
    user_agent      TEXT,
    payload_before  JSONB,
    payload_after   JSONB,
    metadata        JSONB NOT NULL DEFAULT '{}'::jsonb
) PARTITION BY RANGE (occurred_at);
--rollback DROP TABLE audit_event;
```

**Portée — tout est journalisé :**

| Catégorie | Exemples |
|---|---|
| Authentification | connexion, échec, déconnexion, rafraîchissement de jeton, changement de mot de passe |
| Autorisation | tout refus d'accès (`DENIED`), toute tentative de lecture cross-tenant |
| Données métier | création / modification / suppression de `Outing`, `RevenueRecord`, `CostModel`, `FiscalProfile`, `Vehicle` |
| Imports | upload, parsing, mapping appliqué, résolution manuelle, commit, rejeu, échec |
| PII | tout affichage en clair, tout export contenant de la PII |
| Configuration | changement de profil de mapping, de taux de provision, d'option de tenant |
| Cycle de vie tenant | création, invitation, changement de rôle, suppression |
| Exports | toute génération de fichier, avec périmètre et période |

**Garanties d'intégrité :**
- Le rôle applicatif n'a que `INSERT` et `SELECT` sur `audit_event`. `UPDATE` et `DELETE` sont révoqués — l'immuabilité est garantie par la base, pas par le code.
- `correlation_id` propagé via MDC depuis un filtre servlet, présent dans les logs applicatifs et dans chaque événement d'audit : une requête utilisateur se reconstitue de bout en bout.
- Écriture dans la **même transaction** que l'opération métier pour les mutations, afin qu'un rollback métier n'orpheline pas d'entrée d'audit. Les événements de lecture PII sont écrits hors transaction.
- Rétention : 5 ans (alignée sur les obligations de conservation), purge par détachement de partition.

**Implémentation :** annotation `@Audited(action = "...")` interceptée par AOP en couche `application`, complétée par les événements de domaine. Ne jamais auditer depuis la couche `infrastructure` : le contexte métier y est perdu.

**Écran :** journal consultable par le propriétaire du tenant, filtrable par date, acteur et action. C'est à la fois une exigence RGPD et un argument de confiance.

> L'audit trail prend une importance particulière depuis l'abandon du RLS (§9.1) : il devient le contrôle **détectif** qui compense la perte du contrôle **préventif** au niveau base.

---

## 9. Multi-tenancy

### 9.1 Décision : `WHERE` explicite, sans Row-Level Security

**Décision actée (ADR-001).** L'isolation repose sur un prédicat `tenant_id` explicite dans chaque requête, et non sur des policies PostgreSQL.

*Motif :* simplicité opérationnelle, absence de couplage entre pool de connexions et contexte de sécurité, lisibilité des requêtes, absence de comportement divergent entre rôles.

*Risque assumé :* l'isolation devient une propriété du code, pas de la base. Un prédicat oublié expose des données. **Trois contrôles compensatoires sont donc obligatoires et non négociables.**

**Contrôle 1 — Impossible d'écrire une requête sans tenant.**

Aucun repository n'accède directement au `JdbcTemplate` ou au DSL jOOQ. Tout passe par un type qui exige le tenant à la construction :

```kotlin
class TenantScopedQuery private constructor(
    private val tenantId: TenantId,
    private val delegate: DSLContext
) {
    fun <R : Record> from(table: TenantScopedTable<R>): SelectConditionStep<R> =
        delegate.selectFrom(table).where(table.tenantId.eq(tenantId.value))

    companion object {
        fun of(context: TenantContext, delegate: DSLContext) =
            TenantScopedQuery(context.requireTenantId(), delegate)
    }
}
```

Le prédicat n'est pas ajouté par le développeur : il est structurellement présent. `TenantScopedTable` est une interface que toute table métier implémente.

**Contrôle 2 — ArchUnit.**

```kotlin
@ArchTest
val repositoriesMustUseTenantScopedQuery = noClasses()
    .that().resideInAPackage("..infrastructure.spi.persistence..")
    .should().dependOnClassesThat().haveSimpleName("DSLContext")
    .because("tout accès doit passer par TenantScopedQuery")
```

Complété par une règle scannant les chaînes SQL littérales : toute requête mentionnant une table métier sans `tenant_id` fait échouer le build.

**Contrôle 3 — Tests d'isolation systématiques.**

Une classe de test abstraite paramétrée par table, à instancier pour **chaque** nouvelle table métier. Le gabarit d'issue (§11.3) l'impose dans la Definition of Done : deux tenants, données croisées, vérification qu'aucune méthode publique du repository ne retourne de ligne étrangère.

**Contrôle 4 (détectif) — audit trail.** Toute tentative d'accès à une entité d'un autre tenant produit un événement `DENIED` (§8.4), avec alerte au-delà d'un seuil.

### 9.2 `TenantContext`

ThreadLocal + propagation coroutines, alimenté par un filtre servlet depuis le JWT. `requireTenantId()` lève une exception plutôt que de retourner `null` : un contexte absent est un bug, pas un cas nominal. Les jobs asynchrones reçoivent le tenant explicitement en paramètre — jamais par héritage de contexte.

### 9.3 Modèle relationnel anticipant la flotte

```
Tenant (entité juridique exploitante)
  └── Membership (role: OWNER | MANAGER | DRIVER)
        └── Driver   ← peut appartenir à plusieurs Tenants dans le temps
Vehicle       → Tenant
Outing        → tenant_id + driver_id + vehicle_id
RevenueRecord → tenant_id + driver_id + platform
```

`tenant_id` sur **toutes** les tables métier, `NOT NULL`, en tête de chaque index composite.

### 9.4 Onboarding d'un tenant

Assistant en 5 étapes, brouillon sauvegardé à chaque étape (l'abandon en cours est le premier risque d'activation). État `Tenant.onboardingStatus` persisté et reprenable.

1. **Identité** — raison sociale, SIREN (validation de clé), forme juridique, adresse. Pré-remplissage depuis l'API Recherche d'entreprises sur saisie du SIREN.
2. **Profil fiscal** — la forme juridique propose un régime TVA et un schéma de cotisations, modifiables. Taux de provision affichés en clair, avec un lien « pourquoi ces taux ».
3. **Véhicule** — immatriculation, énergie, mise en circulation, mode de détention. Détermine les valeurs de coûts par défaut.
4. **Modèle de coûts** — formulaire guidé poste par poste, **pré-rempli de valeurs sectorielles**, impact sur `C3` recalculé en direct. Étape déterminante pour l'activation : si elle est pénible, l'utilisateur abandonne et le produit n'a aucune donnée de coût.
5. **Premier import** — sélection des plateformes actives, puis guide illustré par document (relevé hebdo Uber, récapitulatif fiscal, export factures, export Bolt **hebdomadaire**, export Driversnote). Un import réussi conclut l'onboarding.

---

## 10. Architecture technique

### 10.1 Modules Gradle

```
kadran-backend/
├── shared-kernel/            # value objects, zéro dépendance Spring
├── platform/                 # tenancy, sécurité, chiffrement, audit, outbox
├── context-identity/
├── context-ingestion/
├── context-activity/
├── context-costmodel/
├── context-fiscal/
├── context-performance/
└── app/                      # bootstrap Spring Boot, composition
```

### 10.2 Structure interne d'un contexte

**Convention retenue : `api` / `spi`** (ADR-005), appliquée symétriquement aux ports et aux adaptateurs.

```
context-activity/
├── domain/
│   ├── model/                    # agrégats, VO, événements — Kotlin pur
│   ├── api/                      # ports offerts : interfaces de cas d'usage
│   └── spi/                      # ports requis : OutingRepository, BlobStore…
├── application/                  # implémente domain/api, consomme domain/spi
└── infrastructure/
    ├── api/
    │   ├── rest/                 # controllers, DTOs, mappers
    │   └── event/                # listeners entrants
    └── spi/
        ├── persistence/          # repositories via TenantScopedQuery
        ├── storage/              # accès blob
        └── event/                # publication outbox
```

**Pourquoi c'est meilleur qu'`inbound`/`outbound`.** `api` et `spi` sont une convention Java établie de longue date — JDBC, JNDI, JAXP, `ServiceLoader`, Quarkus — et elle porte une sémantique précise : **API = ce que le module offre à ses consommateurs, SPI = ce que le module exige d'un fournisseur**. C'est exactement la distinction port pilotant / port piloté de l'architecture hexagonale, avec un vocabulaire que tout développeur Java reconnaît sans explication. `inbound`/`outbound` décrit un sens de circulation ; `api`/`spi` décrit un contrat. Le second est plus informatif.

**La réserve, et sa parade.** Stricto sensu, `api` et `spi` désignent des *interfaces*, pas des implémentations : `infrastructure/api` peut se lire à tort comme « l'API REST publique ». D'où deux règles de nommage :
- les sous-dossiers d'`infrastructure` sont toujours nommés par la technologie (`rest`, `persistence`, `storage`), jamais laissés nus ;
- la symétrie `domain/api` ↔ `infrastructure/api` est systématique, ce qui rend la lecture univoque à la première navigation.

Règle ArchUnit : `domain` ne dépend que de `shared-kernel` et de la stdlib ; `domain/model` ne dépend pas de `domain/api` ni de `domain/spi` ; `infrastructure/spi` n'est jamais importé par `application`.

### 10.3 Choix techniques

| Sujet | Choix | Motif |
|---|---|---|
| Persistance | Spring Data JDBC + jOOQ (analytique et `TenantScopedQuery`) | JPA modélise mal les agrégats DDD |
| Migrations | **Liquibase**, changesets SQL | §11.1 |
| Événements | `ApplicationEventPublisher` + outbox transactionnelle | extractible vers Kafka sans réécriture |
| Métriques | jobs asynchrones idempotents | recalcul déterministe et rejouable |
| Fichiers | S3-compatible (MinIO en dev) | conservation des sources pour rejeu |
| Chiffrement | Tink ou JCA AES-GCM | §8.2 |
| Audit | AOP `@Audited` + événements de domaine | §8.4 |
| Auth | JWT + refresh, Spring Security | — |
| Observabilité | Logs JSON structurés, Micrometer + registre Prometheus, Micrometer Tracing | §10.7 |
| Tests | JUnit 5, Kotest, **MockK**, Testcontainers, ArchUnit | §10.4 |

**Versions de la chaîne de construction** — figées sur ce qui est effectivement livré par KDN-3,
et non sur une intention. Toute version se déclare dans `backend/gradle/libs.versions.toml`,
jamais en dur dans un script de build.

| Élément | Version | Remarque |
|---|---|---|
| JDK | 21 | `jvmToolchain(21)`, cible `JVM_21` |
| Gradle | 9.7 | wrapper commité, racine de construction dans `backend/` |
| Kotlin | 2.2 | `allWarningsAsErrors`, `-Xjsr305=strict` |
| Spring Boot | 3.5 | BOM importé par `io.spring.dependency-management` |
| detekt | 1.23 | dernière stable ; la 2.x est encore en alpha |
| ktlint | 1.5 via `ktlint-gradle` 14 | |
| ArchUnit | 1.5 | |
| Kotest / MockK | 6.2 / 1.14 | MockK réservé à la couche `application` (§10.4) |

**Paquet racine : `io.korallis.kadran`.** Un contexte borné occupe
`io.korallis.kadran.<contexte>` — par exemple `io.korallis.kadran.performance` ; `platform`
occupe `io.korallis.kadran.platform` ; le shared-kernel occupe **`io.korallis.kadran.core`**, le
paquet désignant ce que la brique est pour le reste du code plutôt que le nom de son module.

**Deux incompatibilités de chaîne de construction, résolues et à ne pas redécouvrir.**

- `io.spring.dependency-management` applique le BOM Spring Boot à **toutes** les configurations,
  y compris celles dont le Kotlin Gradle Plugin se sert pour la compilation incrémentale. Il y
  rétrograde `kotlin-build-tools-impl` et fait échouer la compilation dès qu'un module a des
  sources. La propriété `kotlin.version` du BOM doit être alignée sur le catalogue.
- detekt embarque son propre compilateur Kotlin et refuse de tourner sous une version différente,
  alors que le Kotlin Gradle Plugin aligne tout le groupe `org.jetbrains.kotlin` sur celle du
  projet. La règle de résolution doit être réenregistrée **après** la sienne, et ne porter que sur
  la configuration `detekt`.

### 10.4 Stratégie de test

- **Domaine** : objets réels, **aucun mock**. Si un test de domaine a besoin d'un mock, le modèle est mal découpé.
- **Application** : MockK sur les ports sortants. `mockk<T>(relaxed = false)` pour que tout appel non stubbé échoue bruyamment.
- **Infrastructure** : Testcontainers PostgreSQL, jamais H2 — JSONB, partitionnement et types Postgres ne s'y comportent pas pareil.
- **Architecture** : ArchUnit (dépendances, `TenantScopedQuery`, SQL littéral).
- **Property-based** (Kotest) : obligatoire sur `IntervalUnion`, `Money`, et le rattachement temporel revenus ↔ sorties.
- **Isolation** : classe abstraite paramétrée, instanciée pour chaque table métier.

### 10.5 Frontend

```
kadran-web/src/
├── app/(dashboard)/{overview,platforms,outings,costs,imports,fiscal,audit}/
├── features/{activity,cost-model,performance,ingestion,onboarding,audit}/
├── components/ui/            # shadcn
└── lib/
```

Server Components par défaut. TanStack Query, `nuqs` pour les filtres en URL. Recharts. Types générés depuis l'OpenAPI (`openapi-typescript`) — aucun DTO écrit à la main.

### 10.6 Pipeline CI/CD — à mettre en place en premier

**Principe : rien n'est développé avant que la chaîne complète ne tourne à vide.** Le tout premier incrément livre un « hello world » back et front qui traverse l'intégralité du pipeline jusqu'à une image publiée. Ajouter la conteneurisation après coup coûte trois fois plus cher et laisse toujours des écarts entre local et déployé.

**Dépôt : monorepo.**

```
kadran/
├── backend/                  # Gradle multi-modules
├── web/                      # Next.js
├── design/                   # maquettes versionnées, cf. DESIGN-BRIEF.md
├── docs/                     # spec, ADR
├── docker/                   # compose de développement
├── .github/workflows/
├── CLAUDE.md
└── BACKLOG.md
```

**Workflows GitHub Actions**

| Workflow | Déclencheur | Contenu |
|---|---|---|
| `commitlint.yml` | PR | Conventional Commits sur le titre de PR et chaque commit |
| `ci-backend.yml` | PR, push (`backend/**`) | JDK 21, cache Gradle, `ktlint` + `detekt`, tests unitaires, **ArchUnit**, tests d'intégration Testcontainers, seuil JaCoCo |
| `ci-liquibase.yml` | PR (`backend/**/changelog/**`) | `update` puis `rollbackCount` sur Postgres éphémère — **valide que chaque changeset a un rollback fonctionnel** |
| `ci-frontend.yml` | PR, push (`web/**`) | Node 22, pnpm, `eslint`, `tsc --noEmit`, `vitest`, `next build` |
| `ci-openapi.yml` | PR | Génération OpenAPI, détection de rupture de contrat, régénération des types front et vérification qu'ils sont à jour |
| `build-images.yml` | push sur `main`, tags | Build multi-arch, scan Trivy, SBOM, push GHCR |
| `e2e.yml` | nightly + PR étiquetée | Playwright sur la stack complète via compose |

**Portes de qualité bloquantes** — un échec bloque la fusion :
- couverture minimale sur le module `domain` (le reste est indicatif) ;
- aucune violation ArchUnit (pureté du domaine, `TenantScopedQuery`, absence de SQL sans `tenant_id`) ;
- rollback Liquibase vérifié ;
- aucune vulnérabilité `HIGH` ou `CRITICAL` dans les images ;
- types front régénérés et commités.

**Images**

```
ghcr.io/<org>/kadran-backend:{sha|semver|latest}
ghcr.io/<org>/kadran-web:{sha|semver|latest}
```

- Backend : build multi-étapes, JAR Spring Boot en couches, runtime `eclipse-temurin:21-jre-alpine`, utilisateur non-root, `HEALTHCHECK` sur `/actuator/health`.
- Front : `output: 'standalone'` de Next.js, runtime `node:22-alpine`, utilisateur non-root.
- Étiquetage OCI complet (`org.opencontainers.image.revision`, `.source`, `.created`) : une image doit toujours pouvoir être rattachée à son commit.
- Aucun secret dans une image. Toute configuration par variable d'environnement.

**Développement local** — `docker/compose.yml` : PostgreSQL 18, MinIO, backend, web, Prometheus et Grafana (§10.7.5), avec rechargement à chaud. Une seule commande doit suffire à démarrer l'ensemble ; c'est la condition pour que Claude Code puisse valider son travail.

### 10.7 Observabilité

**Trois flux distincts, à ne jamais confondre.** L'`audit_event` (§8.4) est une obligation
légale : immuable, conservé 5 ans, stocké en base, jamais expédié hors du système. Les **logs
applicatifs** servent l'exploitation : éphémères, expédiés vers un agrégateur, jamais opposables.
Les **métriques** sont des agrégats numériques sans identifiant. Router l'audit vers l'agrégateur
de logs, ou déduire une preuve d'un log, sont deux erreurs symétriques — la première fait sortir
de la donnée soumise à conservation, la seconde s'appuie sur un flux qu'on peut perdre.

#### 10.7.1 Logs applicatifs

**Format JSON dès le premier jour**, via le support structuré natif de Spring Boot 3.4+
(`logging.structured.format.console`), sans dépendance externe : ajouter le JSON après coup oblige
à réécrire chaque appel de log et chaque règle d'extraction en aval.

Champs posés en MDC par le filtre de KDN-15, présents sur **chaque** ligne :

| Clé | Contenu | Origine |
|---|---|---|
| `correlation_id` | UUID de la requête utilisateur, renvoyé au client | filtre servlet, accepté en en-tête entrant ou généré |
| `tenant_id` | UUID opaque du tenant courant | `TenantContext` |
| `trace_id`, `span_id` | identifiants techniques | Micrometer Tracing (§10.7.3) |

`correlation_id` est l'identifiant **fonctionnel** : c'est lui qu'on donne à un utilisateur qui
signale un incident, et lui qui figure dans l'événement d'audit correspondant. `trace_id` est
l'identifiant **technique** de la trace distribuée. Les deux coexistent, aucun ne remplace l'autre.

**Une ligne d'accès par requête HTTP terminée** : méthode, **route templatisée**
(`/api/outings/{id}`, jamais l'URI brute), statut, durée en millisecondes, taille de la réponse.
L'URI brute en clair de log est une fuite d'identifiants et rend tout regroupement impossible.

**Une ligne d'entrée et de sortie par traitement notable** — import d'un lot, application d'un
profil de mapping, recalcul de métriques, consommation d'outbox — avec la durée, le résultat et le
même `correlation_id` que la requête qui l'a déclenché. Un traitement asynchrone qui perd le
`correlation_id` de son déclencheur est un traitement qu'on ne saura pas rattacher à un incident.

**Aucune PII dans un log. Jamais.** Ni adresse, ni nom, ni e-mail, ni SIREN, ni identifiant de
contrepartie. Les logs quittent le système, échappent au chiffrement de §8.2 et à la purge de
§8.3 : une adresse écrite dans un log survit à l'effacement du compte qui l'a produite. Un UUID
opaque de tenant est acceptable ; tout ce qui désigne une personne ne l'est pas. En cas de doute
sur un objet à journaliser, journaliser son identifiant, pas son contenu.

#### 10.7.2 Métriques

**Micrometer avec registre Prometheus, en collecte *pull*** sur `/actuator/prometheus`.

**Le port de management est séparé du port applicatif** (`management.server.port`), et n'est
exposé ni par le service web ni par l'ingress. `/actuator/prometheus` publie la topologie interne
de l'application ; il est joignable par le collecteur, par rien d'autre.

Conventions de nommage, alignées sur les usages Prometheus :

- préfixe `kadran_`, puis contexte borné, puis sujet : `kadran_ingestion_parse_failures_total` ;
- suffixe `_total` sur les compteurs, unité de base en suffixe pour le reste (`_seconds`,
  `_bytes`) — jamais de millisecondes dans un nom de métrique ;
- les métriques HTTP, JVM et datasource viennent d'Actuator : ne pas les redéfinir.

Métriques métier attendues au MVP : échecs de parsing par profil de mapping, lots d'import par
état de la machine à états, durée des recalculs de métriques, profondeur et âge de l'outbox.

> **Aucune métrique ne porte de dimension `tenant_id`, `driver_id` ou identifiant d'agrégat.**
> Deux motifs, chacun suffisant. La cardinalité : une série temporelle est créée par combinaison
> de labels, et un label par tenant multiplie la charge du collecteur par le nombre de clients —
> c'est la première cause d'effondrement d'une instance Prometheus. La confidentialité : les
> métriques partent vers un système d'exploitation qui n'a ni le chiffrement de §8.2, ni les
> restrictions d'accès de §9, ni la purge de §8.3. **Ce qui doit être lu par tenant se lit dans
> l'application, jamais dans Grafana.**

#### 10.7.3 Traces

Micrometer Tracing avec pont OpenTelemetry, propagation W3C `traceparent`, `trace_id` et `span_id`
posés en MDC. **L'instrumentation est en place en v1, l'exportation ne l'est pas** : on branche un
exporteur OTLP le jour où un collecteur existe, sans retoucher le code applicatif.

#### 10.7.4 Santé

`/actuator/health` avec sondes `liveness` et `readiness` distinctes. La `readiness` dépend de la
base : une instance qui ne peut pas migrer ni requêter ne doit pas recevoir de trafic. La
`liveness` n'en dépend pas — une base indisponible ne doit pas déclencher une boucle de
redémarrages.

#### 10.7.5 Pile locale

`docker/compose.yml` embarque **Prometheus et Grafana** en plus de PostgreSQL, MinIO, backend et
web. Prometheus collecte le port de management du backend ; Grafana est provisionné par fichiers,
et **ses tableaux de bord sont versionnés dans le dépôt** (`docker/grafana/`). Un tableau de bord
construit à la souris dans une instance locale n'existe pas : il disparaît au premier
`docker compose down -v`.

---

## 11. Conventions

### 11.1 Liquibase

**Trigramme projet : `KDN`.** Toute référence d'issue s'écrit `KDN-<numéro>`.

**Format :** SQL. **Emplacement :** `app/src/main/resources/db/changelog/changes/`.
**Fichier :** `yyyyMMdd_KDN-<numéro d'issue>_<description-kebab-case>.sql`
**Identifiant de changeset :** `kadran:yyyyMMdd_KDN-<issue>_<séquence>`

Le séparateur est l'underscore, la référence d'issue reste en tirets : `KDN-21` ne se coupe pas.
Les trois segments — date, issue, description — se distinguent ainsi de la description elle-même,
qui est en kebab-case.

```
20260818_KDN-21_create-audit-event.sql
20260818_KDN-27_create-tenant-and-membership.sql
20260819_KDN-34_create-outing-table.sql
20260820_KDN-34_add-outing-linked-revenue.sql
20260825_KDN-101_seed-provision-rates-2026.sql
```

Le trigramme n'est pas décoratif : il rend les fichiers identifiables une fois extraits de leur arborescence — dans un diff, une revue de PR, un log Liquibase, ou le jour où un second dépôt partagera la même base.

**Pas de changeset monolithique initial.** Chaque issue apporte ses tables. Une migration livrée n'est jamais modifiée ; on en ajoute une nouvelle.

Changelog maître — `db.changelog-master.xml`, seul fichier non-SQL :

```xml
<databaseChangeLog xmlns="http://www.liquibase.org/xml/ns/dbchangelog" ...>
    <includeAll path="db/changelog/changes/" relativeToChangelogFile="false"/>
</databaseChangeLog>
```

`includeAll` trie alphabétiquement ; le préfixe `yyyyMMdd` garantit l'ordre chronologique.

Gabarit :

```sql
--liquibase formatted sql

--changeset kadran:20260819_KDN-34_01 labels:activity context:all
--comment Création de la table outing
CREATE TABLE outing (
    id             UUID PRIMARY KEY,
    tenant_id      UUID        NOT NULL,
    driver_id      UUID        NOT NULL,
    vehicle_id     UUID,
    business_day   DATE        NOT NULL,   -- journée d'exploitation, cf. §4.3
    started_at     TIMESTAMPTZ,            -- null si la source ne fournit pas d'horaires
    ended_at       TIMESTAMPTZ,
    spans_midnight BOOLEAN     NOT NULL DEFAULT false,
    distance_m     BIGINT      NOT NULL,
    purpose        VARCHAR(16) NOT NULL,
    source         VARCHAR(24) NOT NULL,
    platform_extras JSONB      NOT NULL DEFAULT '{}'::jsonb,
    raw_payload    JSONB       NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_outing_tenant_day ON outing (tenant_id, business_day DESC);
--rollback DROP TABLE outing;
```

Règles : un `--rollback` sur **tout** changeset ; un changeset = une intention ; jamais de `DROP` de colonne portant des données sans changeset de sauvegarde préalable ; `labels` = nom du contexte borné.

### 11.2 Git

**Conventional Commits**, scope = contexte borné.

```
<type>(<scope>): [KDN-<issue>] <description à l'impératif, minuscule, sans point final>

[corps]

Refs: KDN-<issue>
Closes #<issue>
```

Types : `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `perf`, `build`, `ci`.
Scopes : `activity`, `ingestion`, `costmodel`, `fiscal`, `performance`, `identity`, `privacy`, `audit`, `platform`, `web`, `db`.

```
feat(ingestion): [KDN-35] mapper l'export bolt revenus par chauffeur

Profil de mapping des 39 colonnes, gestion du BOM UTF-8 et de l'espace
terminal de l'en-tête "Chauffeur ". Contrôle d'intégrité bruts - frais = nets.
Champs sans équivalent Uber stockés dans platform_extras.

Refs: KDN-35
Closes #35
```

**Le numéro ouvre la description, pas la ligne.** `[KDN-<issue>]` se place **après** le `<type>(<scope>):`, jamais avant : le préfixe Conventional Commits doit rester en tête pour que commitlint (§10.6) analyse le message. Le numéro devient le premier élément lisible de la description, ce qui rend un `git log --oneline` navigable sans ouvrir un seul commit.

**Double référence assumée.** `KDN-35` est la référence stable et lisible hors contexte (fichiers Liquibase, branches, revues) ; `#35` reste nécessaire pour que GitHub ferme automatiquement l'issue. Les deux ne sont pas redondantes, elles s'adressent à des lecteurs différents.

Rupture de contrat → `feat(api)!:` + `BREAKING CHANGE:` en pied.
**Branches :** `<type>/KDN-<issue>-<slug>` → `feat/KDN-35-map-bolt-driver-revenue-export`
**Titre d'issue GitHub :** `[KDN-35] Profil de mapping de l'export Bolt`
**PR :** titre au format Conventional Commit, squash-merge.

### 11.3 Issues GitHub

Niveaux : `epic` → `story` → `spike`.

```markdown
## Contexte
[Pourquoi, avec renvoi à la section de spec]

## Périmètre
- [ ] …

## Critères d'acceptation
- Étant donné … quand … alors …

## Notes techniques
Contexte borné : `ingestion`
Grain concerné : PERIOD
Changesets Liquibase attendus : oui / non
Réf. spec : §3.4

## Definition of Done
- [ ] Tests unitaires du domaine (sans mock)
- [ ] Test d'intégration Testcontainers si persistance
- [ ] **Test d'isolation multi-tenant si nouvelle table**
- [ ] Passage par `TenantScopedQuery` vérifié par ArchUnit
- [ ] Événements d'audit émis pour toute mutation
- [ ] Changeset Liquibase avec rollback
- [ ] OpenAPI à jour si endpoint
```

Labels : `epic`, `story`, `spike`, `bug` · `ctx:activity`, `ctx:ingestion`, … · `prio:P0`–`P2` · `needs-sample` · `grain:trip|outing|day|period`.

---

## 12. Epics et issues

Référencement `KDN-<n>`. La liste détaillée, découpée back / front / infra et prête à créer sur GitHub, se trouve dans **`BACKLOG.md`**. Les numéros ci-dessous sont indicatifs et doivent correspondre aux numéros réellement attribués à la création.

**EPIC KDN-1 — Socle et pipeline** *(à livrer intégralement avant toute fonctionnalité)* · KDN-2 Monorepo, structure de dossiers, `CLAUDE.md`, `BACKLOG.md` · KDN-3 Squelette Gradle multi-modules (`domain/{model,api,spi}`, `application`, `infrastructure/{api,spi}`) · KDN-4 Squelette Next.js + shadcn + tokens de design · KDN-5 `docker/compose.yml` (Postgres, MinIO, back, web) · KDN-6 Dockerfiles multi-étapes back et front, non-root, healthcheck · KDN-7 Workflows CI back, front, commitlint · KDN-8 Workflow `build-images` vers GHCR avec Trivy et SBOM · KDN-9 Workflow de validation Liquibase avec test de rollback · KDN-10 Génération OpenAPI et vérification de non-rupture · KDN-11 PostgreSQL + Liquibase + changelog maître · KDN-12 `TenantContext` + filtre servlet + `correlation_id` MDC · KDN-13 `TenantScopedQuery`, `TenantScopedTable`, règles ArchUnit · KDN-14 Classe abstraite de test d'isolation · KDN-15 Auth JWT + refresh

> **Définition de fin de l'EPIC KDN-1 :** un commit sur `main` produit et publie deux images conteneurisées, et `docker compose up` démarre une application accessible affichant une page authentifiée vide. Aucune fonctionnalité métier n'est commencée avant cet état.

**EPIC KDN-10 — Audit** · KDN-11 Table `audit_event` partitionnée, permissions restreintes · KDN-12 Annotation `@Audited` + aspect · KDN-13 Couverture des catégories §8.4 · KDN-14 Écran de consultation du journal · KDN-15 Purge par détachement de partition

**EPIC KDN-16 — Identité & onboarding** · KDN-17 `Tenant`, `Membership`, `Driver` · KDN-18 Inscription / connexion · KDN-19 Assistant étapes 1–2 avec brouillon reprenable · KDN-20 Pré-remplissage SIREN

**EPIC KDN-21 — Modèle d'activité** · KDN-22 `BusinessDayPolicy` + `toBusinessDay`, tests sur changements d'heure **(prérequis de tout le reste)** · KDN-23 Agrégat `Outing` · KDN-24 Agrégat `RevenueRecord` avec `grain` · KDN-25 `PlatformProfile` et `SourceCapability` · KDN-26 Agrégat `WorkDay` · KDN-27 **`IntervalUnion` + property-based tests (P0, tôt)** · KDN-28 Saisie manuelle d'une sortie · KDN-29 Liste et détail des sorties

**EPIC KDN-30 — Confidentialité** · KDN-31 Chiffrement enveloppe, DEK par tenant, converters · KDN-32 Blind index HMAC · KDN-33 Masquage + bascule ré-authentifiée · KDN-34 Option `retainCounterpartyIdentity` et purge · KDN-35 Réduction des adresses Driversnote au code postal

**EPIC KDN-36 — Kilométrage** · KDN-37 **[spike] `Début`/`Fin` du CSV Driversnote portent-ils une heure ? — ouvrir en éditeur de texte, pas en tableur. BLOQUANT** · KDN-38 Profil de mapping Driversnote (9 colonnes, `Taux` arrondi non recalculable) · KDN-39 Import CSV générique avec assistant de correspondance · KDN-40 Exclusion des sorties personnelles

**EPIC KDN-41 — Ingestion, socle générique** · KDN-42 Modèle `ImportBatch` + machine à états du pipeline · KDN-43 `MappingProfile` en base + moteur d'application · KDN-44 Stockage `platform_extras` / `raw_payload` + rejeu d'un import après correction de profil · KDN-45 Écran d'upload, de suivi et de revue des anomalies · KDN-46 Rapport de divergence sur périodes recouvrantes · KDN-47 Détection du grain et avertissement si la période importée est trop large

**EPIC KDN-49 — Ingestion Uber** *(seule plateforme du périmètre v1)* · KDN-50 **[spike] En-têtes complets du CSV factures — `needs-sample`** · KDN-51 Parseur PDF relevé hebdomadaire, section Transactions · KDN-52 Parseur PDF relevé hebdomadaire, section Détails des revenus · KDN-53 Parseur PDF récapitulatif fiscal · KDN-54 Parseur CSV factures avec traitement PII (§8.1) · KDN-55 `TripMatcher` : rapprochement des trois documents par horodatage et montant · KDN-56 Écran de résolution des rapprochements ambigus · KDN-57 Contrôle de cohérence sur le solde cumulé du relevé · KDN-58 `PlatformProfile` Uber et déclaration de capacités

**EPIC KDN-90 — Ingestion Bolt** *(v1.1 — profil entièrement spécifié en §3.4)* · KDN-91 Profil de mapping Bolt (39 colonnes, BOM UTF-8, en-tête `"Chauffeur "` à espace terminal) · KDN-92 Contrôle d'intégrité bruts − frais = nets · KDN-93 Avertissement si période > 8 jours · KDN-94 **[spike] Export Bolt *Trips* (`fleets.bolt.eu` → onglet Trips → période → CSV) : qualifier colonnes, distance et horaires**

**EPIC KDN-95 — Ingestion Heetch** *(v1.1 — profil entièrement spécifié en §3.6)* · KDN-96 Profil de mapping Heetch (8 colonnes, `€` préfixé, `Mois` en `yyyy-M`, ligne `Total` à écarter) · KDN-97 Refus explicite des métriques temps et distance

**EPIC KDN-98 — Arbitrage plateforme** *(v1.1)* · KDN-99 Métriques `A1`–`A6` · KDN-100 Heatmap marge × jour × créneau · KDN-101 Écran Plateformes avec commission comparée

**EPIC KDN-59 — Rattachement revenus ↔ sorties** · KDN-60 Moteur de rattachement temporel avec score · KDN-61 File de résolution manuelle · KDN-62 Écran de résolution · KDN-63 Mode dégradé grain journée si horaires absents

**EPIC KDN-64 — Modèle de coûts** · KDN-65 `CostModel` versionné + résolution à la date · KDN-66 Référentiel de valeurs sectorielles par défaut · KDN-67 Onboarding étapes 3–4 avec impact `C3` en direct · KDN-68 Métriques `C1`–`C7`

**EPIC KDN-69 — Moteur de métriques** · KDN-70 `MetricDefinition` avec `requiredGrain` et `requiredCapabilities` · KDN-71 `MetricUnavailable` et son rendu · KDN-72 `DataCompleteness` · KDN-73 Projections + recalcul événementiel idempotent · KDN-74 Métriques temps, distance, revenu · KDN-75 Métriques marge dont `M11`–`M13` · KDN-76 Endpoint `/metrics` + traçabilité

**EPIC KDN-77 — Dashboard** · KDN-78 Overview, chiffre héros `F4` · KDN-79 Écran Sorties avec `M11`/`M12` et `M13` · KDN-80 Comparaison de périodes et drill-down · KDN-81 Bandeau de complétude + CTA d'import · KDN-82 Bloc commission effective `R3`/`R4` avec sa décomposition — **remplace l'écran Plateformes en v1**

**EPIC KDN-83 — Fiscal** · KDN-84 `FiscalProfile` + `provision_rate` versionnée · KDN-85 Métriques `F1`–`F5`, ventilation TVA 10/20 · KDN-86 Alertes de seuil avec projection

**EPIC KDN-87 — Exports** · KDN-88 Export CSV/XLSX expert-comptable · KDN-89 Synthèse mensuelle PDF

**Ordre de traitement recommandé pour la v1 :** KDN-1 → KDN-10 → KDN-16 → **KDN-22** → KDN-21 → **KDN-37** → KDN-36 → KDN-30 → KDN-41 → KDN-49 → KDN-59 → KDN-64 → KDN-69 → KDN-83 → KDN-77 → KDN-87. Les epics KDN-90, KDN-95 et KDN-98 sont hors périmètre v1.

Les issues `[spike]` portent le label `needs-sample` et sont **bloquantes** dans leur epic. KDN-37 conditionne toutes les métriques au grain `OUTING` et devient critique en périmètre Uber seul : sans horaires Driversnote, `M2` retombe sur la borne inférieure `Floor` (§3.7). KDN-22 conditionne toutes les métriques journalières et doit précéder KDN-23. KDN-50 bloque le calcul de `R1`, donc `R3`, donc la métrique de vente du produit.

---

## 13. Brief pour Claude Design

**Principe :** consultation depuis le véhicule, en fin de journée, fatigué. Densité maîtrisée, hiérarchie brutale, contraste élevé, **dark mode par défaut**.

**Overview.** Chiffre héros `F4` (revenu réellement disponible du mois). En dessous, le **bloc Commission** : taux effectif Uber avec sa décomposition (frais de service / CA TTC) et l'écart au taux communément annoncé — c'est le moment de bascule émotionnelle du produit en périmètre mono-plateforme. Puis quatre tuiles : km productifs, coût mort, marge par sortie moyenne, sorties non rentables.

**Sorties.** L'écran opérationnel quotidien. Une ligne par sortie : journée d'exploitation, durée, km, revenus, coûts, marge, €/h. Sorties non rentables mises en évidence. Le détail décompose intégralement, avec les sources qui ont produit chaque chiffre, et signale explicitement quand l'amplitude est une borne inférieure (§3.7).

**Plateformes.** *Hors périmètre v1* — l'écran arrive avec la deuxième plateforme. En v1, la comparaison de commissions vit dans le bloc Commission de l'Overview.

**Coûts.** Assistant en étapes, valeurs par défaut pré-remplies, impact `C3` recalculé en direct. Écran déterminant pour l'activation.

**Imports.** Calendrier de complétude par plateforme et par semaine (vert / orange / gris). Le vide doit être visible et actionnable. Écran de résolution des rattachements ambigus : deux colonnes, validation en un geste. Avertissement explicite si l'export Bolt couvre plus de 8 jours.

**Journal.** Table dense, filtrable par acteur, action et date. Registre de confiance, pas un écran d'administration système.

**Direction visuelle.** Dribbble : « financial dashboard dark », « fintech analytics mobile », « energy monitoring dashboard ». **Éviter** « fleet management » et « logistics » : registre de supervision, trop chargé. L'analogie juste est le tableau de bord d'investissement personnel — un chiffre net, une tendance, une explication au clic.

---

## 14. Décisions actées et ouvertes

| # | Sujet | Statut |
|---|---|---|
| ADR-001 | Isolation par `WHERE` explicite, sans RLS, avec 4 contrôles compensatoires (§9.1) | **Acté** |
| ADR-002 | La sortie remplace la course comme unité économique (§4) | **Acté** |
| ADR-003 | Trois zones de stockage : canonique / extras JSONB / brut JSONB (§7.6) | **Acté** |
| ADR-004 | Aucune estimation par répartition proportionnelle | **Acté** |
| ADR-005 | Convention `api` / `spi` pour ports et adaptateurs (§10.2) | **Acté** |
| ADR-006 | Journée d'exploitation à seuil configurable, défaut 04:00 (§4.3) | **Acté** |
| ADR-007 | Trigramme `KDN` sur toute référence d'issue (§11.1) | **Acté** |
| ADR-008 | **Périmètre v1 restreint à Uber**, Bolt et Heetch reportés en v1.1 (§2.3) | **Acté** |
| ADR-009 | `Amplitude` typée `Observed` / `Floor`, jamais un chiffre nu (§3.7) | **Acté** |
| ADR-010 | **Monorepo** back + front + design + docs (§10.6). Motif principal : `ci-openapi` vérifie dans une même PR que les types front correspondent au contrat back. Choix réversible — extraire `web/` reste peu coûteux, fusionner deux dépôts ne l'est pas | **Acté** |
| ADR-011 | **Observabilité** : logs JSON structurés sans PII, métriques Prometheus **sans dimension `tenant_id`**, port de management séparé, audit distinct des logs (§10.7) | **Acté** |
| D1 | Les colonnes `Début`/`Fin` de Driversnote portent-elles une heure ? | **Bloquant — KDN-37** |
| D2 | L'export Bolt *Trips* contient-il la distance et les horaires par course ? | Ouvert — KDN-48 |
| D3 | Référentiel de coûts sectoriels par défaut | À constituer |
| D4 | Benchmark anonymisé entre tenants | v2, consentement, k-anonymat ≥ 20 |
| D5 | Application mobile | PWA en v1.5 |
| D6 | Modèle tarifaire | Abonnement unique. Pas de freemium sur les métriques : c'est le produit |
| D7 | Nom | Kadran — à valider INPI et disponibilité de domaine |
