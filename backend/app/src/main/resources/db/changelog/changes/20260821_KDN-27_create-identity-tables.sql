--liquibase formatted sql

-- Contexte `identity` (KDN-27) — spec §9.3 « Modèle relationnel anticipant la flotte ».
--
-- Quatre tables, quatre changesets : `tenant`, `driver`, `membership`, `vehicle`. La v1 est
-- mono-chauffeur, mais rien ici ne suppose qu'il n'y en ait qu'un — l'ajout du gestionnaire
-- de flotte (persona v2) est une insertion de lignes, pas une migration structurante.
--
-- **Toutes les tables métier sont scopées, `tenant` comprise.** La question était posée par
-- l'issue : `tenant` est-elle elle-même une table scopée ? Elle l'est, et sans colonne
-- supplémentaire — sa clé primaire *est* `tenant_id`. Une table `tenant (id, …)` aurait
-- exigé une exception dans `TenantScopedTable`, donc un chemin d'accès non scopé, donc
-- exactement le trou que l'ADR-001 nous oblige à fermer par le code. Ici, la lecture
-- `SELECT … FROM tenant WHERE tenant_id = ?` rend au plus une ligne : celle de l'appelant.
--
-- **Le `tenant_id` ouvre chaque clé primaire composite.** Ce n'est pas seulement l'ordre
-- d'index qu'impose `CLAUDE.md` §2.3 : c'est aussi ce qui rend indicible la lecture d'une
-- ligne par son seul identifiant, sans dire de quel exploitant elle relève.
--
-- **Aucune donnée personnelle « PII_HIGH » n'est introduite ici** — ni adresse, ni date de
-- naissance, ni numéro de permis. Le chiffrement enveloppe de la spec §8.2 (DEK par tenant)
-- n'existe pas encore ; ces champs viendront avec le contexte capable de les protéger.
-- L'adresse de l'étape 1 de l'onboarding (§9.4) est dans ce cas : pour une entreprise
-- individuelle, elle est le domicile du chauffeur.

--changeset kadran:20260821_KDN-27_01 labels:identity context:all
--comment Table tenant, dont la cle primaire est la cle d'isolation
CREATE TABLE tenant (
    tenant_id         UUID         PRIMARY KEY,
    legal_name        VARCHAR(200) NOT NULL,
    siren             VARCHAR(9)   NOT NULL,
    onboarding_status VARCHAR(24)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    closed_at         TIMESTAMPTZ,
    CONSTRAINT ck_tenant_legal_name_not_blank CHECK (btrim(legal_name) <> ''),
    -- La clé de contrôle du SIREN est vérifiée par le domaine (`Siren`, algorithme de Luhn).
    -- La base ne garde que l'invariant qu'elle peut tenir sans dupliquer la règle métier.
    CONSTRAINT ck_tenant_siren_digits CHECK (siren ~ '^[0-9]{9}$'),
    CONSTRAINT ck_tenant_onboarding_status CHECK (
        onboarding_status IN ('IDENTITY', 'FISCAL_PROFILE', 'VEHICLE', 'COST_MODEL', 'FIRST_IMPORT', 'COMPLETED')
    ),
    CONSTRAINT ck_tenant_closed_after_creation CHECK (closed_at IS NULL OR closed_at >= created_at)
);
-- Un SIREN identifie une entité juridique : deux exploitants ouverts ne peuvent pas le
-- partager. La restriction aux tenants ouverts laisse une réinscription possible après
-- clôture, sans jamais réécrire l'historique de la précédente.
CREATE UNIQUE INDEX ux_tenant_siren_open ON tenant (siren) WHERE closed_at IS NULL;
--rollback DROP TABLE tenant;

--changeset kadran:20260821_KDN-27_02 labels:identity context:all
--comment Table driver, le chauffeur tel que l'exploitant le connait
CREATE TABLE driver (
    tenant_id    UUID         NOT NULL REFERENCES tenant (tenant_id),
    id           UUID         NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_driver PRIMARY KEY (tenant_id, id),
    CONSTRAINT ck_driver_display_name_not_blank CHECK (btrim(display_name) <> '')
);
--rollback DROP TABLE driver;

--changeset kadran:20260821_KDN-27_03 labels:identity context:all
--comment Table membership, lien date entre un compte, un exploitant et un role
CREATE TABLE membership (
    tenant_id   UUID        NOT NULL,
    id          UUID        NOT NULL,
    driver_id   UUID        NOT NULL,
    -- Compte d'authentification, servi par KDN-18. Nullable tant que la table `account`
    -- n'existe pas, et parce qu'un chauffeur peut être enregistré avant d'être invité :
    -- l'appartenance précède l'accès. C'est cette colonne qui porte l'identité de la
    -- personne physique d'un exploitant à l'autre — voir la note du changeset 04.
    account_id  UUID,
    role        VARCHAR(16) NOT NULL,
    valid_from  TIMESTAMPTZ NOT NULL,
    -- `NULL` = appartenance en cours. Une révocation **ferme** la période, elle ne supprime
    -- pas la ligne : l'historique daté est un critère d'acceptation de l'issue, et un
    -- `DELETE` le perdrait.
    valid_until TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_membership PRIMARY KEY (tenant_id, id),
    -- Clé étrangère **composite** : une appartenance ne peut pas désigner le chauffeur d'un
    -- autre exploitant. L'isolation devient ici une contrainte d'intégrité, pas seulement un
    -- prédicat de requête.
    CONSTRAINT fk_membership_driver FOREIGN KEY (tenant_id, driver_id) REFERENCES driver (tenant_id, id),
    CONSTRAINT ck_membership_role CHECK (role IN ('OWNER', 'MANAGER', 'DRIVER')),
    CONSTRAINT ck_membership_period CHECK (valid_until IS NULL OR valid_until > valid_from)
);
CREATE INDEX idx_membership_driver ON membership (tenant_id, driver_id, valid_from DESC);
CREATE INDEX idx_membership_account ON membership (tenant_id, account_id);
-- Une seule appartenance ouverte par chauffeur : les rôles successifs se lisent dans
-- l'historique, ils ne coexistent pas.
CREATE UNIQUE INDEX ux_membership_open ON membership (tenant_id, driver_id) WHERE valid_until IS NULL;
--rollback DROP TABLE membership;

--changeset kadran:20260821_KDN-27_04 labels:identity context:all
--comment Table vehicle, rattachee a l'exploitant et non au chauffeur
CREATE TABLE vehicle (
    tenant_id           UUID        NOT NULL REFERENCES tenant (tenant_id),
    id                  UUID        NOT NULL,
    -- Immatriculation normalisée en majuscules sans séparateur. **Aucun format n'est imposé**
    -- : le SIV français, l'ancien FNI et les plaques étrangères coexistent, et un `CHECK`
    -- calqué sur le seul SIV rejetterait des véhicules parfaitement légitimes.
    plate               VARCHAR(16) NOT NULL,
    energy              VARCHAR(24) NOT NULL,
    ownership_mode      VARCHAR(24) NOT NULL,
    first_registered_on DATE        NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_vehicle PRIMARY KEY (tenant_id, id),
    CONSTRAINT ck_vehicle_energy CHECK (
        energy IN ('DIESEL', 'PETROL', 'HYBRID', 'PLUG_IN_HYBRID', 'ELECTRIC', 'LPG')
    ),
    CONSTRAINT ck_vehicle_ownership_mode CHECK (
        ownership_mode IN ('OWNED_OUTRIGHT', 'OWNED_FINANCED', 'LEASE_LOA', 'LEASE_LLD', 'RENTAL')
    )
);
CREATE UNIQUE INDEX ux_vehicle_plate ON vehicle (tenant_id, plate);
--rollback DROP TABLE vehicle;
