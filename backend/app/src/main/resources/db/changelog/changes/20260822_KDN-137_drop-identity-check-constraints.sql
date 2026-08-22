--liquibase formatted sql

-- Retrait des CHECK des tables identity (KDN-137). Neuf contraintes, toutes redondantes avec
-- une validation applicative déjà en place et déjà éprouvée par les tests du domaine
-- (`context-identity/src/main/kotlin/.../identity/domain/model`) :
--
--   ck_tenant_legal_name_not_blank   -> LegalName.of
--   ck_tenant_siren_digits           -> Siren.of (longueur ET clé de Luhn, plus strict que le
--                                       CHECK, qui ne vérifiait que le format)
--   ck_tenant_onboarding_status      -> enum OnboardingStatus
--   ck_tenant_closed_after_creation  -> Tenant.close (créée pour KDN-137 : `createdAt` n'était
--                                       pas porté par l'agrégat avant, l'invariant n'existait
--                                       qu'en base)
--   ck_driver_display_name_not_blank -> DriverName.of
--   ck_membership_role               -> enum MembershipRole
--   ck_membership_period             -> MembershipPeriod (l'invariant vit dans son `init`,
--                                       ré-exécuté à chaque `copy`, donc à chaque fermeture)
--   ck_vehicle_energy                -> enum EnergySource
--   ck_vehicle_ownership_mode        -> enum OwnershipMode
--
-- Hors périmètre, comme le veut l'issue : PRIMARY KEY, FOREIGN KEY, NOT NULL, UNIQUE (dont les
-- index partiels `ux_membership_open` et `ux_vehicle_plate`) ne sont pas des CHECK et ne sont
-- pas touchés.
--
-- Les tables ciblées vivent dans `kadran` depuis KDN-136 : c'est pourquoi cette issue dépend de
-- la précédente et ne peut pas s'appliquer avant elle.

--changeset kadran:20260822_KDN-137_01 labels:identity context:all
--comment Retrait des neuf CHECK des tables tenant, driver, membership, vehicle
ALTER TABLE kadran.tenant DROP CONSTRAINT ck_tenant_legal_name_not_blank;
ALTER TABLE kadran.tenant DROP CONSTRAINT ck_tenant_siren_digits;
ALTER TABLE kadran.tenant DROP CONSTRAINT ck_tenant_onboarding_status;
ALTER TABLE kadran.tenant DROP CONSTRAINT ck_tenant_closed_after_creation;

ALTER TABLE kadran.driver DROP CONSTRAINT ck_driver_display_name_not_blank;

ALTER TABLE kadran.membership DROP CONSTRAINT ck_membership_role;
ALTER TABLE kadran.membership DROP CONSTRAINT ck_membership_period;

ALTER TABLE kadran.vehicle DROP CONSTRAINT ck_vehicle_energy;
ALTER TABLE kadran.vehicle DROP CONSTRAINT ck_vehicle_ownership_mode;

--rollback ALTER TABLE kadran.tenant ADD CONSTRAINT ck_tenant_legal_name_not_blank CHECK (btrim(legal_name) <> '');
--rollback ALTER TABLE kadran.tenant ADD CONSTRAINT ck_tenant_siren_digits CHECK (siren ~ '^[0-9]{9}$');
--rollback ALTER TABLE kadran.tenant ADD CONSTRAINT ck_tenant_onboarding_status CHECK (onboarding_status IN ('IDENTITY', 'FISCAL_PROFILE', 'VEHICLE', 'COST_MODEL', 'FIRST_IMPORT', 'COMPLETED'));
--rollback ALTER TABLE kadran.tenant ADD CONSTRAINT ck_tenant_closed_after_creation CHECK (closed_at IS NULL OR closed_at >= created_at);
--rollback ALTER TABLE kadran.driver ADD CONSTRAINT ck_driver_display_name_not_blank CHECK (btrim(display_name) <> '');
--rollback ALTER TABLE kadran.membership ADD CONSTRAINT ck_membership_role CHECK (role IN ('OWNER', 'MANAGER', 'DRIVER'));
--rollback ALTER TABLE kadran.membership ADD CONSTRAINT ck_membership_period CHECK (valid_until IS NULL OR valid_until > valid_from);
--rollback ALTER TABLE kadran.vehicle ADD CONSTRAINT ck_vehicle_energy CHECK (energy IN ('DIESEL', 'PETROL', 'HYBRID', 'PLUG_IN_HYBRID', 'ELECTRIC', 'LPG'));
--rollback ALTER TABLE kadran.vehicle ADD CONSTRAINT ck_vehicle_ownership_mode CHECK (ownership_mode IN ('OWNED_OUTRIGHT', 'OWNED_FINANCED', 'LEASE_LOA', 'LEASE_LLD', 'RENTAL'));
