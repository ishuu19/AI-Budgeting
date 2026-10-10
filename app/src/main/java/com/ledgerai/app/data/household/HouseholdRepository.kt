package com.ledgerai.app.data.household

import com.ledgerai.app.domain.household.Household
import com.ledgerai.app.domain.household.HouseholdMember
import com.ledgerai.app.domain.household.HouseholdMembership
import com.ledgerai.app.domain.household.HouseholdRole
import com.ledgerai.app.domain.household.MembershipDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.util.UUID

class HouseholdRepository(
    private val dao: HouseholdDao,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val nowDateTime: () -> LocalDateTime = { LocalDateTime.now() },
) {

    fun observeMine(userId: String): Flow<List<Household>> =
        combine(dao.observeHouseholds(), dao.observeMembers()) { households, members ->
            HouseholdMembership.mine(
                userId,
                households.map { it.toDomain() },
                members.map { it.toDomain() },
            )
        }

    suspend fun listMine(userId: String): List<Household> = observeMine(userId).first()

    fun observeMembers(householdId: String): Flow<List<HouseholdMember>> =
        dao.observeMembers().map { rows ->
            HouseholdMembership.activeMembers(rows.map { it.toDomain() })
                .filter { it.householdId == householdId }
        }

    suspend fun createHousehold(name: String, userId: String): String? {
        val checked = HouseholdMembership.checkedName(name) ?: return null
        val now = nowMillis()
        return dao.insertHouseholdWithOwner(
            household = HouseholdEntity(
                id = UUID.randomUUID().toString(),
                name = checked,
                createdBy = userId,
                updatedAt = now,
            ),
            ownerUserId = userId,
            joinedAt = nowDateTime(),
            updatedAt = now,
        )
    }

    /** 013 has no invite-status column, so this inserts an active member. */
    suspend fun addMember(
        householdId: String,
        actorUserId: String,
        memberUserId: String,
    ): MembershipDecision {
        val rows = dao.membersOf(householdId).map { it.toDomain() }
        val active = HouseholdMembership.activeMembers(rows)
        val decision = HouseholdMembership.addMember(
            actor = active.find { it.userId == actorUserId },
            targetUserId = memberUserId,
            existing = rows.find { it.userId == memberUserId.trim() },
        )
        if (decision !is MembershipDecision.Allow) return decision
        val now = nowMillis()
        dao.upsertMember(
            HouseholdMemberEntity(
                householdId = householdId,
                userId = memberUserId.trim(),
                role = HouseholdRole.MEMBER.stored,
                joinedAt = nowDateTime(),
                updatedAt = now,
            )
        )
        return decision
    }

    suspend fun leave(householdId: String, userId: String): MembershipDecision {
        val active = HouseholdMembership.activeMembers(dao.membersOf(householdId).map { it.toDomain() })
        val decision = HouseholdMembership.leave(
            actor = active.find { it.userId == userId },
            activeOwnerCount = active.count { it.role == HouseholdRole.OWNER },
        )
        if (decision !is MembershipDecision.Allow) return decision
        val now = nowMillis()
        dao.softDeleteMember(householdId, userId, deletedAt = now, updatedAt = now)
        return decision
    }

    suspend fun removeMember(
        householdId: String,
        actorUserId: String,
        memberUserId: String,
    ): MembershipDecision {
        val active = HouseholdMembership.activeMembers(dao.membersOf(householdId).map { it.toDomain() })
        val decision = HouseholdMembership.remove(
            actor = active.find { it.userId == actorUserId },
            target = active.find { it.userId == memberUserId },
        )
        if (decision !is MembershipDecision.Allow) return decision
        val now = nowMillis()
        dao.softDeleteMember(householdId, memberUserId, deletedAt = now, updatedAt = now)
        return decision
    }
}
