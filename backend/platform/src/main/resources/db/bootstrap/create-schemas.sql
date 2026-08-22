-- Crée les schémas PostgreSQL avant que Liquibase ne s'y connecte (KDN-136, ADR-013).
--
-- **La création de schéma n'est jamais la responsabilité de Liquibase.** Deux raisons.
-- D'abord une contrainte technique : `databasechangelog` vit dans `kadran`, qui doit donc
-- exister avant la toute première connexion de Liquibase — un changeset ne peut pas créer
-- le schéma où il s'inscrit lui-même, problème de l'œuf et de la poule vérifié
-- empiriquement dans les deux sens (schéma absent → `CREATE TABLE kadran.databasechangelog`
-- échoue ; schéma déjà présent au moment où le changeset tourne → son propre
-- `CREATE SCHEMA` échoue à son tour). Ensuite un principe : ça rend le rôle applicatif
-- indépendant du privilège `CREATE SCHEMA`, ce qui compte sur une base provisionnée hors du
-- dépôt (une plateforme managée, une équipe infra) — les schémas y préexistent, Liquibase
-- n'a besoin que d'y écrire.
--
-- Point d'entrée unique, invoqué différemment selon le contexte — la même source, jamais
-- recopiée :
--   - docker/compose.yml   : monté dans /docker-entrypoint-initdb.d/ du service postgres,
--                            exécuté par l'image officielle au tout premier démarrage sur un
--                            volume vierge
--   - Testcontainers       : PostgreSQLContainer.withInitScript(...), rejoué à chaque
--                            conteneur puisqu'il n'y a jamais de volume persistant
--   - ci-liquibase.yml     : exécuté via psql juste avant les pas Liquibase — les services
--                            GitHub Actions n'acceptent pas de montage de volume
--
-- `IF NOT EXISTS` : idempotent, pour rester rejouable sans effet sur une base où les
-- schémas existeraient déjà (par exemple provisionnés par une plateforme managée).
--
-- Trois schémas applicatifs, chacun pour un motif distinct détaillé en tête du changeset
-- KDN-136 (`db/changelog/changes/20260822_KDN-136_...sql`) : `kadran` l'opérationnel
-- générique, `audit` le réglementaire, `credentials` les secrets d'authentification.
CREATE SCHEMA IF NOT EXISTS kadran;
CREATE SCHEMA IF NOT EXISTS audit;
CREATE SCHEMA IF NOT EXISTS credentials;

-- Le rôle applicatif s'appelle `kadran`, comme le schéma opérationnel qui vient de naître.
-- `search_path` par défaut vaut `"$user", public` : dès que `kadran` existe, `"$user"` s'y
-- résout, et toute DDL non qualifiée — celle des changesets déjà fusionnés (KDN-14, KDN-27),
-- écrits et testés quand seul `public` existait — se met à atterrir dans `kadran` au lieu de
-- `public`, silencieusement, sans qu'aucun de ces changesets n'ait changé d'une ligne.
-- Vérifié empiriquement : sans cette ligne, `ALTER TABLE public.schema_baseline SET SCHEMA
-- kadran` (le changeset KDN-136) échoue, la table s'étant créée directement dans `kadran`.
--
-- `ALTER ROLE ... SET search_path` est une préférence durable au niveau du rôle, appliquée à
-- toute connexion future quel que soit l'outil (pool JDBC de l'application, plugin Gradle
-- Liquibase, psql, Testcontainers) — un seul point de vérité, pas une chaîne de connexion à
-- répéter partout. Elle rend le comportement des changesets non qualifiés indépendant du
-- moment où `kadran` apparaît : ils continuent de viser `public`, pour toujours, quelle que
-- soit la base sur laquelle ils rejouent.
-- Enveloppé pour tolérer un rôle qui ne s'appelle pas `kadran` : les tests Testcontainers
-- de ce dépôt (`PostgreSQLContainer("postgres:18-alpine")` sans `.withUsername(...)`)
-- connectent sous le rôle par défaut de Testcontainers (`test`), qui ne collisionne jamais
-- avec un nom de schéma et n'a donc pas besoin de ce pin — sans cette garde, la ligne
-- échouerait avec « role "kadran" does not exist » sur ces conteneurs-là.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'kadran') THEN
        ALTER ROLE kadran SET search_path TO public;
    END IF;
END
$$;
