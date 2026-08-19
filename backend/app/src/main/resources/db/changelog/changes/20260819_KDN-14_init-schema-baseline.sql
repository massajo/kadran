--liquibase formatted sql

-- Socle de migration (KDN-14). Aucune table métier ici : `tenant` et `membership`
-- relèvent de KDN-27, `audit_event` de KDN-21. `schema_baseline` est une table de
-- contrôle, non scopée par tenant, qui atteste qu'une base a bien été initialisée par
-- Kadran — le garde-fou qui distingue une base vierge d'une base étrangère avant que
-- la première migration métier ne s'y applique.

--changeset kadran:20260819_KDN-14_01 labels:platform context:all
--comment Table de contrôle attestant qu'une base porte le schéma Kadran
CREATE TABLE schema_baseline (
    id             SMALLINT    PRIMARY KEY DEFAULT 1,
    application    VARCHAR(32) NOT NULL,
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_schema_baseline_singleton CHECK (id = 1)
);
INSERT INTO schema_baseline (application) VALUES ('kadran');
--rollback DROP TABLE schema_baseline;
