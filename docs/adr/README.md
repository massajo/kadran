# Décisions d'architecture (ADR)

Les décisions actées de Kadran sont aujourd'hui consignées dans le tableau §14 de
[`../SPEC-MVP-kadran.md`](../SPEC-MVP-kadran.md), avec leur motivation développée dans la section
correspondante de la spec.

| # | Sujet | Section |
|---|---|---|
| ADR-001 | Isolation par `WHERE` explicite, sans RLS, avec 4 contrôles compensatoires | §9.1 |
| ADR-002 | La sortie remplace la course comme unité économique | §4 |
| ADR-003 | Trois zones de stockage : canonique / extras JSONB / brut JSONB | §7.6 |
| ADR-004 | Aucune estimation par répartition proportionnelle | §2.1 de `CLAUDE.md`, §4 |
| ADR-005 | Convention `api` / `spi` pour ports et adaptateurs | §10.2 |
| ADR-006 | Journée d'exploitation à seuil configurable, défaut 04:00 | §4.3 |
| ADR-007 | Trigramme `KDN` sur toute référence d'issue | §11.1 |
| ADR-008 | Périmètre v1 restreint à Uber | §2.3 |
| ADR-009 | `Amplitude` typée `Observed` / `Floor`, jamais un chiffre nu | §3.7 |
| ADR-010 | Monorepo back + front + design + docs | §10.6 |

**Ce répertoire n'en est volontairement pas une copie.** Dupliquer le contenu créerait deux sources
de vérité qui divergeraient. L'extraction en fichiers `NNN-<slug>.md` autonomes reste à faire, et
sera l'occasion de compléter chaque décision de son contexte, de ses alternatives écartées et de
ses conséquences — format Nygard. Voir la question ouverte de l'issue KDN-2.

## Décision nouvelle

Toute décision qui contredirait un ADR existant fait l'objet d'un **nouvel ADR** qui remplace le
précédent (`status: superseded by ADR-0NN`). On ne contourne pas un ADR, on le remplace.
