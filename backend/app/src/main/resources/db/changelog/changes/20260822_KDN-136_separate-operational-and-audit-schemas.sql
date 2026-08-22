--liquibase formatted sql

-- Séparation des schémas PostgreSQL (KDN-136, spec §9.3, ADR-013). Jusqu'ici tout vivait,
-- implicitement, dans `public` : `schema_baseline` (KDN-14), `tenant`, `driver`, `membership`,
-- `vehicle` (KDN-27).
--
-- Trois schémas applicatifs existent désormais :
--   - `kadran`, l'**opérationnel générique** — les cinq tables ci-dessus aujourd'hui, et
--     `outing`, `revenue_record`, `cost_model`… demain, pour les contextes autres que
--     l'identité et l'authentification.
--   - `audit`, le **réglementaire** — vide à ce stade, préparé pour `audit_event` (KDN-21)
--     et `entity_change` (KDN-126), qui n'existent pas encore.
--   - `credentials`, les **secrets d'authentification** — vide à ce stade, préparé pour le
--     compte et les jetons de rafraîchissement que KDN-19 branchera sur le port
--     `CredentialsFinder` de KDN-18. Séparé de `kadran` pour le même motif que l'audit :
--     un hash de mot de passe et un refresh token appellent des `GRANT` plus stricts que le
--     reste de la donnée opérationnelle. Nommé `credentials` plutôt que `auth` : Supabase
--     réserve déjà `auth` (ainsi que `storage`, `realtime`, `vault`, `extensions`…) sur tout
--     projet Postgres qu'il gère — un nom générique évite la collision si ce chemin est
--     un jour emprunté.
--
-- **Ce changeset ne crée aucun schéma.** La création est entièrement externe à Liquibase —
-- `db/bootstrap/create-schemas.sql`, exécuté avant la première connexion de l'application,
-- quel que soit l'environnement (image Postgres en développement, pas Liquibase en CI,
-- script d'initialisation Testcontainers). Deux raisons : (1) `databasechangelog` vit dans
-- `kadran`, qui doit donc exister avant que Liquibase n'ouvre sa toute première connexion —
-- un changeset ne peut pas créer le schéma où il s'inscrit lui-même, problème de l'œuf et
-- de la poule vérifié empiriquement dans les deux sens ; (2) ça rend le rôle applicatif
-- indépendant du privilège `CREATE SCHEMA` — sur une base gérée en dehors du dépôt (une
-- plateforme managée, un provisionnement par une équipe infra), les schémas préexistent et
-- Liquibase n'a besoin que d'y écrire.
--
-- Ce changeset ne fait donc que déplacer des tables qui existent déjà.

--changeset kadran:20260822_KDN-136_01 labels:platform context:all
--comment Deplacement des tables existantes vers le schema kadran (schemas crees hors Liquibase)
ALTER TABLE public.schema_baseline SET SCHEMA kadran;
ALTER TABLE public.tenant SET SCHEMA kadran;
ALTER TABLE public.driver SET SCHEMA kadran;
ALTER TABLE public.membership SET SCHEMA kadran;
ALTER TABLE public.vehicle SET SCHEMA kadran;

--rollback ALTER TABLE kadran.vehicle SET SCHEMA public;
--rollback ALTER TABLE kadran.membership SET SCHEMA public;
--rollback ALTER TABLE kadran.driver SET SCHEMA public;
--rollback ALTER TABLE kadran.tenant SET SCHEMA public;
--rollback ALTER TABLE kadran.schema_baseline SET SCHEMA public;
