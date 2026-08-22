package io.korallis.kadran.identity.infrastructure.spi.persistence

import io.korallis.kadran.core.DriverId
import io.korallis.kadran.core.TenantId
import io.korallis.kadran.identity.domain.model.AccountId
import io.korallis.kadran.identity.domain.model.Membership
import io.korallis.kadran.identity.domain.model.MembershipId
import io.korallis.kadran.identity.domain.model.MembershipPeriod
import io.korallis.kadran.identity.domain.model.MembershipRole
import io.korallis.kadran.identity.domain.spi.MembershipRepository
import io.korallis.kadran.identity.infrastructure.spi.persistence.IdentityTables.MEMBERSHIP
import io.korallis.kadran.identity.infrastructure.spi.persistence.IdentityTables.MembershipColumns
import io.korallis.kadran.platform.persistence.TenantScopedQuery
import org.jooq.Field
import org.jooq.Record

/**
 * Adaptateur `membership`.
 *
 * Aucune méthode ne supprime : une révocation est un `save` d'une appartenance dont la
 * période est fermée. C'est la seule façon de tenir le critère d'acceptation « les deux
 * appartenances sont conservées et datées » — un `DELETE` effacerait le fait qu'il y en ait
 * eu une.
 *
 * [findByAccount] est la lecture que le port d'authentification de KDN-18 attend : d'un
 * compte, tirer les appartenances, donc l'exploitant et le rôle.
 */
class JooqMembershipRepository(
    private val query: TenantScopedQuery,
) : MembershipRepository {
    override fun findById(id: MembershipId): Membership? =
        query
            .selectFrom(MEMBERSHIP)
            .and(MembershipColumns.ID.eq(id.value))
            .fetchOne()
            ?.toMembership()

    override fun findByDriver(driverId: DriverId): List<Membership> =
        query
            .selectFrom(MEMBERSHIP)
            .and(MembershipColumns.DRIVER_ID.eq(driverId.value))
            .orderBy(MembershipColumns.VALID_FROM.desc())
            .fetch()
            .map { it.toMembership() }

    override fun findByAccount(accountId: AccountId): List<Membership> =
        query
            .selectFrom(MEMBERSHIP)
            .and(MembershipColumns.ACCOUNT_ID.eq(accountId.value))
            .orderBy(MembershipColumns.VALID_FROM.desc())
            .fetch()
            .map { it.toMembership() }

    override fun findOpen(): List<Membership> =
        query
            .selectFrom(MEMBERSHIP)
            .and(MembershipColumns.VALID_UNTIL.isNull())
            .orderBy(MembershipColumns.VALID_FROM.desc())
            .fetch()
            .map { it.toMembership() }

    override fun save(membership: Membership) {
        query
            .insertInto(MEMBERSHIP, insertValues(membership))
            .onConflict(MEMBERSHIP.tenantId, MembershipColumns.ID)
            .doUpdate()
            .set(mutableValues(membership))
            .execute()
    }

    private fun insertValues(membership: Membership): Map<Field<*>, Any?> =
        mutableValues(membership) +
            mapOf<Field<*>, Any?>(
                MEMBERSHIP.tenantId to membership.tenantId.value,
                MembershipColumns.ID to membership.id.value,
                MembershipColumns.DRIVER_ID to membership.driverId.value,
                MembershipColumns.VALID_FROM to membership.period.validFrom.toColumnValue(),
            )

    /**
     * `driver_id` et `valid_from` n'en font pas partie : réaffecter une appartenance à un
     * autre chauffeur, ou antidater sa prise d'effet, ce serait réécrire un fait passé. Ce
     * qui change, c'est le rôle, le compte rattaché, et la date de fermeture.
     */
    private fun mutableValues(membership: Membership): Map<Field<*>, Any?> =
        mapOf(
            MembershipColumns.ACCOUNT_ID to membership.accountId?.value,
            MembershipColumns.ROLE to membership.role.name,
            MembershipColumns.VALID_UNTIL to membership.period.validUntil?.toColumnValue(),
        )

    private fun Record.toMembership(): Membership =
        Membership(
            id = MembershipId(read(MembershipColumns.ID)),
            tenantId = TenantId(read(MEMBERSHIP.tenantId)),
            driverId = DriverId(read(MembershipColumns.DRIVER_ID)),
            accountId = readOrNull(MembershipColumns.ACCOUNT_ID)?.let(::AccountId),
            role = MembershipRole.valueOf(read(MembershipColumns.ROLE)),
            period =
                MembershipPeriod(
                    validFrom = read(MembershipColumns.VALID_FROM).toInstant(),
                    validUntil = readOrNull(MembershipColumns.VALID_UNTIL)?.toInstant(),
                ),
        )
}
