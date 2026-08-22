--liquibase formatted sql

-- Agrégat `Outing` (KDN-34, spec §4.4) — l'unité économique du produit (§4.1, ADR-002).
--
-- `outing_date` stocke la **journée d'exploitation** (spec §4.3, `BusinessDayPolicy`, KDN-33),
-- jamais la date calendaire de début : une vacation qui commence à 22h et finit à 3h le
-- lendemain porte la date du jour où elle a commencé.
--
-- `window_from` / `window_to` sont **conjointement nuls ou conjointement renseignés** :
-- Driversnote peut ne fournir qu'une date sans horaire, et `Outing.window` est alors `null`
-- plutôt qu'une heure inventée (ADR-004, `CLAUDE.md` §8 piège Driversnote). L'invariant est
-- porté par le domaine — `WorkPeriod` regroupe les deux bornes dans un seul type, si bien
-- qu'aucun code de production ne peut écrire l'une sans l'autre. Pas de `CHECK` ici, dans la
-- continuité de KDN-137 : la validation applicative suffit, un `CHECK` dupliquerait la règle.
--
-- `start_label` / `end_label` **ne sont volontairement pas des colonnes de cette table.** La
-- spec §4.4 les porte sur l'agrégat ; la spec §8.1 les qualifie de PII (« Home, 1 Rue…,
-- 91300 Massy »), dont la réduction au code postal et à la ville est traitée par KDN-47, pas
-- encore livrée. Le domaine porte donc ces deux champs (`Outing.startLabel`/`endLabel`), mais
-- l'adaptateur de persistance ne les écrit pas : les persister en clair aujourd'hui les
-- exposerait sans le traitement que KDN-47 doit encore apporter.
--
-- `linked_revenue_record_id` ne porte **aucune** clé étrangère : la table `revenue_record`
-- (KDN-35) est construite en parallèle sur une autre branche et n'existe pas encore. C'est une
-- simple référence — ni score ni statut de rapprochement, qui viendront avec KDN-75.

--changeset kadran:20260822_KDN-34_01 labels:activity context:all
--comment Table outing, l'unite economique du produit (spec §4.4)
CREATE TABLE kadran.outing (
    tenant_id                 UUID        NOT NULL REFERENCES kadran.tenant (tenant_id),
    id                        UUID        NOT NULL,
    outing_date               DATE        NOT NULL,
    window_from               TIMESTAMPTZ,
    window_to                 TIMESTAMPTZ,
    spans_midnight            BOOLEAN     NOT NULL,
    distance_meters           BIGINT      NOT NULL,
    purpose                   VARCHAR(16) NOT NULL,
    mileage_allowance_cents   BIGINT,
    source                    VARCHAR(16) NOT NULL,
    linked_revenue_record_id  UUID,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_outing PRIMARY KEY (tenant_id, id)
);
-- Journee d'exploitation : la lecture la plus frequente (WorkDay, spec §7.3).
CREATE INDEX idx_outing_business_day ON kadran.outing (tenant_id, outing_date);
--rollback DROP TABLE kadran.outing;
