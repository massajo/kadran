# DESIGN-BRIEF.md — Instructions pour Claude Design

Ce fichier gouverne tout le travail de maquettage de Kadran. Il définit le cadre, les contraintes non négociables et le système de versionnement.

Référence fonctionnelle : `docs/SPEC-MVP-kadran.md`, section 13.

---

## 1. Le produit, du point de vue du design

Kadran dit à un chauffeur VTC indépendant combien il gagne **réellement**, une fois retirés la commission de la plateforme, le coût de son véhicule et ses charges sociales et fiscales.

Le moment décisif de l'expérience est un écart : le chauffeur croit gagner X, Kadran lui montre Y. Tout le design existe pour rendre cet écart lisible, crédible et actionnable — pas pour l'assener.

**Contexte d'usage réel** : consultation depuis le véhicule, souvent la nuit, souvent fatigué, souvent d'une main sur un téléphone. Parfois le dimanche soir sur un ordinateur, pour faire le point sur la semaine.

Conséquences directes : dark mode par défaut, contraste élevé, cibles tactiles généreuses, un chiffre dominant par écran, tout le reste subordonné.

---

## 2. Contraintes non négociables

### 2.1 Le langage de la confiance

C'est la particularité de ce produit et elle doit exister dans le système de design avant tout écran.

Les données proviennent de fichiers importés, souvent incomplets. Chaque chiffre porte donc un niveau de confiance, et l'interface doit le montrer sans transformer l'écran en champ de mines.

| Niveau | Rendu | Règle |
|---|---|---|
| `RELIABLE` | valeur pleine, sans ornement | le cas nominal doit rester silencieux |
| `INDICATIVE` | fourchette au lieu d'une valeur ponctuelle, marqueur discret | jamais un avertissement rouge : c'est une information, pas une erreur |
| `INSUFFICIENT` | la valeur est **remplacée** par une action | « importez votre relevé de la semaine 32 », pas un tiret ni un zéro |
| `Floor` (borne inférieure) | formulation explicite : « au mieux 14,20 €/h » | ne jamais afficher une borne comme une mesure |
| `MetricUnavailable` | phrase expliquant la cause | « Uber ne fournit pas de temps de connexion » — l'absence est une information utile |

Ces cinq états doivent être maquettés comme des composants du système, avant les écrans qui les utilisent.

### 2.2 Interdits

- Aucun zéro, aucun tiret, aucun graphique vide pour représenter une donnée manquante.
- Aucun chiffre sans unité ni période.
- Aucune couleur porteuse de sens sans redondance textuelle ou d'icône.
- Aucune métaphore de « gamification » : pas de badge, pas de série, pas de félicitations. L'utilisateur gagne sa vie, il ne joue pas.
- Aucun ton culpabilisant sur une mauvaise performance. Kadran constate, il ne juge pas.

### 2.3 Accessibilité

Contraste AA minimum sur tous les textes, AAA sur le chiffre héros. Taille de police minimale 14 px en interface, 16 px sur mobile. Navigation clavier complète. Tableaux avec en-têtes associés.

---

## 3. Direction visuelle

**Registre juste** : le tableau de bord d'investissement personnel. Un chiffre net, une tendance, une explication au clic.

**Registre à éviter** : le logiciel de gestion de flotte et la supervision logistique — trop denses, trop froids, orientés contrôle d'autrui plutôt que compréhension de soi.

Pistes de recherche sur Dribbble : `financial dashboard dark`, `fintech analytics mobile`, `energy monitoring dashboard`, `personal finance net worth`. Chercher la sobriété et la hiérarchie, pas les effets.

**Tokens** — source unique de vérité dans `design/tokens.json`, consommée par le front. Aucun token ne doit exister uniquement dans une maquette.

Points à figer dès la v0.1 : palette dark et sa variante light, échelle typographique (le chiffre héros doit tenir sur mobile sans troncature), échelle d'espacement, rayons, élévations, palette sémantique (positif / négatif / neutre / avertissement), et la palette dédiée aux niveaux de confiance qui doit être **distincte** de la palette positif/négatif — une donnée peu fiable n'est pas une mauvaise nouvelle.

---

## 4. Écrans du périmètre v1

Périmètre v1 = Uber seul. Ne pas maquetter d'écran de comparaison entre plateformes.

| Priorité | Écran | Cœur |
|---|---|---|
| 1 | **Overview** | Chiffre héros `F4` revenu réellement disponible. Bloc Commission : taux effectif Uber, décomposition, écart au taux annoncé. Quatre tuiles : km productifs, coût mort, marge moyenne par sortie, sorties non rentables |
| 2 | **Sorties** | L'écran quotidien. Une ligne par sortie : journée d'exploitation, durée, km, revenus, coûts, marge, €/h. Sorties non rentables mises en évidence. Détail décomposant intégralement, avec les sources de chaque chiffre |
| 3 | **Coûts** | Assistant en étapes, valeurs sectorielles pré-remplies, impact sur le coût kilométrique recalculé en direct. **Écran déterminant pour l'activation** : s'il est pénible, le produit n'a aucune donnée de coût et ne sert à rien |
| 4 | **Imports** | Calendrier de complétude par semaine (vert / orange / gris). Le vide doit être visible et actionnable. Écran de résolution des rapprochements ambigus : sources à gauche, propositions à droite, validation en un geste |
| 5 | **Onboarding** | 5 étapes, progression visible, retour arrière, reprise après abandon |
| 6 | **Fiscal** | Provisions, ventilation TVA, suivi des seuils avec projection de dépassement |
| 7 | **Journal** | Table filtrable des opérations. Registre de confiance, pas un écran d'administration système |

Chaque écran doit être livré dans ses **quatre états** : vide, chargement, nominal, dégradé (données partielles). L'état dégradé n'est pas optionnel — c'est l'état le plus fréquent des premières semaines d'utilisation.

---

## 5. Versionnement des maquettes

### 5.1 Arborescence

```
design/
├── tokens.json                 # source unique des tokens
├── CHANGELOG.md                # historique chronologique, une entrée par itération
├── DECISIONS.md                # décisions de design argumentées, format ADR
├── components/                 # système de design, versionné à part
│   └── v0.2/
│       ├── confidence-states.png
│       └── ...
└── screens/
    ├── overview/
    │   ├── v0.1/
    │   │   ├── overview--nominal--v0.1.png
    │   │   ├── overview--empty--v0.1.png
    │   │   ├── overview--degraded--v0.1.png
    │   │   └── NOTES.md
    │   └── v0.2/
    └── outings/
```

### 5.2 Nommage

`<écran>--<état>--v<version>.<ext>`

États : `nominal`, `empty`, `loading`, `degraded`, `error`, et si besoin `mobile-nominal`.

### 5.3 Numérotation

- `v0.x` — exploration, avant toute implémentation.
- `v1.0` — première version implémentée. **Une version implémentée est gelée** : on ne la modifie plus, on en crée une nouvelle.
- Incrément mineur (`v1.1`) : ajustement dans le cadre existant.
- Incrément majeur (`v2.0`) : changement de structure d'écran, de parcours ou de tokens.

### 5.4 Statuts

Chaque dossier de version porte un `NOTES.md` dont l'en-tête déclare le statut :

```markdown
---
screen: overview
version: 0.2
status: review          # draft | review | approved | implemented | superseded
supersedes: 0.1
issue: KDN-78
date: 2026-08-20
---

## Intention
Ce qu'on cherche à résoudre par rapport à la version précédente.

## Changements
- ...

## Décisions ouvertes
- ...

## Ce qui est volontairement écarté
- ...
```

Le champ `superseded` n'est jamais supprimé du dépôt : l'historique des maquettes abandonnées est ce qui empêche de refaire deux fois le même essai.

### 5.5 CHANGELOG

Une entrée par itération, la plus récente en haut :

```markdown
## v0.2 — 2026-08-20 — Overview, Sorties
**Intention.** Le chiffre héros passait inaperçu à côté du bloc Commission.
**Changements.** Hiérarchie retravaillée ; Commission descendue sous la ligne de flottaison ; état dégradé ajouté sur les quatre tuiles.
**Décision.** Voir DECISIONS.md #007.
**Statut.** review
```

### 5.6 Articulation avec le développement

- Une issue de développement front référence une version de maquette précise : `design/screens/overview/v0.2`.
- Une maquette passe en `implemented` uniquement quand la PR correspondante est fusionnée.
- Une évolution demandée après implémentation crée une nouvelle version **et** une nouvelle issue. Jamais de modification en place.
- `tokens.json` est la seule passerelle entre design et code. Une couleur qui n'y figure pas ne doit pas apparaître dans le front.

---

## 6. Ordre de travail recommandé

1. `tokens.json` et les composants d'état de confiance — avant tout écran.
2. Overview, ses quatre états.
3. Sorties, liste et détail.
4. Coûts — y consacrer le plus de soin, c'est l'écran qui décide de l'activation.
5. Imports, dont l'écran de résolution des ambiguïtés.
6. Onboarding.
7. Fiscal et Journal.

À chaque étape, livrer d'abord la version mobile. Le desktop est une expansion du mobile, pas l'inverse : le chiffre héros doit tenir sur 375 px de large avant qu'on se demande à quoi ressemble un écran large.
