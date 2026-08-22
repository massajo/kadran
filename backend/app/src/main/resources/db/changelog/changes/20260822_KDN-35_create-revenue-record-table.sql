--liquibase formatted sql

-- Table `revenue_record` (KDN-35, spec §7.3, §7.6) — le revenu, quelle que soit la plateforme
-- et quel que soit son grain. Remplace `Trip` comme porteur de revenu : `Trip` supposait un
-- grain course uniforme, que seule Uber respecte (Bolt exporte au grain période, §3.4).
--
-- **Trois zones de stockage distinctes (ADR-003, spec §7.6), pas une table plate.**
--   - **Canonique** : `platform`, `grain`, `coverage_from`/`coverage_to`, les six montants
--     de `RevenueBreakdown` + `currency`, la TVA, les compteurs d'activité — colonnes SQL
--     classiques, typées, utilisées par le moteur de métriques.
--   - **Structuré générique** : `external_refs`, `provenance` — communs à toute plateforme
--     (donc canoniques, pas des « extras »), mais de cardinalité variable (spec §7.7 : un
--     `RevenueRecord` peut porter plusieurs `externalRef`), d'où JSONB plutôt que des tables
--     filles pour une v1 mono-plateforme dont la volumétrie ne le justifie pas.
--   - **Extras** (`platform_extras`) : spécifique à une plateforme, clés préfixées
--     (`uber.priorityFee`, `bolt.driverScore` — spec §7.6), indexé GIN pour rester
--     exploitable par le moteur de métriques malgré son typage dynamique.
--   - **Brut** (`raw_payload`) : le document source intégral, conservé pour rejeu (spec
--     §7.6), **jamais lu par aucun calcul** — `JooqRevenueRecordRepository` projette
--     explicitement les colonnes ci-dessus pour toute lecture du domaine, `raw_payload`
--     délibérément exclu de cette projection (voir `RevenueRecordTables.Reads.CANONICAL` et
--     `RevenueRecordRawPayloadNeverReadTest`).
--
-- **Aucun `CHECK` sur les colonnes à valeurs closes** (`platform`, `grain`). Suivant KDN-137
-- (« retirer les contraintes check, validation applicative seule »), la validation vit dans
-- le domaine (`PlatformId`, `Grain` — des enums Kotlin) plutôt que dans une contrainte que la
-- base devrait maintenir manuellement en synchronisation à chaque plateforme ajoutée.
--
-- `tenant_id` ouvre la clé primaire composite et le seul index secondaire de cette table
-- (`CLAUDE.md` §2.3). La table est créée **qualifiée** `kadran.revenue_record`, plutôt que de
-- compter sur le `search_path` du rôle de connexion : celui-ci ne pointe vers `kadran` que
-- pour le rôle applicatif `kadran` (voir `db/bootstrap/create-schemas.sql`), pas pour le rôle
-- `test` sous lequel tournent les conteneurs Testcontainers de ce dépôt. Une DDL non qualifiée
-- y atterrirait silencieusement dans `public`.

--changeset kadran:20260822_KDN-35_01 labels:activity context:all
--comment Table revenue_record, le revenu quelle que soit la plateforme et le grain
CREATE TABLE kadran.revenue_record (
    tenant_id                  UUID        NOT NULL REFERENCES kadran.tenant (tenant_id),
    id                         UUID        NOT NULL,
    platform                   VARCHAR(16) NOT NULL,
    grain                      VARCHAR(8)  NOT NULL,
    coverage_from              TIMESTAMPTZ NOT NULL,
    coverage_to                TIMESTAMPTZ NOT NULL,
    -- Zone structurée générique : Set<ExternalRef> / Set<DataProvenance> du domaine.
    external_refs              JSONB       NOT NULL DEFAULT '[]'::jsonb,
    provenance                 JSONB       NOT NULL DEFAULT '[]'::jsonb,
    -- RevenueBreakdown — une devise commune aux six montants (RevenueBreakdown.init).
    currency                   VARCHAR(3)  NOT NULL,
    amount_gross_cents         BIGINT      NOT NULL,
    amount_net_cents           BIGINT      NOT NULL,
    amount_commission_cents    BIGINT      NOT NULL,
    amount_tips_cents          BIGINT      NOT NULL,
    amount_incentives_cents    BIGINT      NOT NULL,
    amount_surcharges_cents    BIGINT      NOT NULL,
    -- VatBreakdown, nullable en bloc : absent pour une plateforme qui ne fournit pas de TVA.
    vat_base_cents              BIGINT,
    vat_amount_cents            BIGINT,
    vat_total_cents             BIGINT,
    vat_rate                    NUMERIC,
    -- ActivityCounts, chaque compteur individuellement nullable (CLAUDE.md §2.1 : jamais 0
    -- a la place d'une donnee absente).
    counts_trips                 INTEGER,
    counts_online_time_seconds   BIGINT,
    counts_distance_m            BIGINT,
    -- Extras et brut (ADR-003, spec §7.6) — voir le commentaire de tete de fichier.
    platform_extras            JSONB       NOT NULL DEFAULT '{}'::jsonb,
    raw_payload                JSONB       NOT NULL,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_revenue_record PRIMARY KEY (tenant_id, id)
);
-- Lecture courante : les revenus d'un exploitant, les plus recents d'abord.
CREATE INDEX idx_revenue_record_tenant_coverage ON kadran.revenue_record (tenant_id, coverage_from DESC);
-- Zone « extras » exploitable par le moteur de metriques malgre son typage dynamique
-- (spec §7.6 : `CREATE INDEX ... USING GIN (platform_extras jsonb_path_ops)`).
CREATE INDEX idx_revenue_record_extras ON kadran.revenue_record USING GIN (platform_extras jsonb_path_ops);
--rollback DROP TABLE kadran.revenue_record;
