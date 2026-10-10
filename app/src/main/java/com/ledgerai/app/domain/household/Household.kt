package com.ledgerai.app.domain.household

import java.time.LocalDateTime

enum class HouseholdRole {
    OWNER,
    MEMBER;

    /** Stored value matches data-model.md: owner | member. */
    val stored: String
        get() = when (this) {
            OWNER -> "owner"
            MEMBER -> "member"
        }

    companion object {
        fun fromStored(value: String): HouseholdRole = when (value) {
            "owner" -> OWNER
            "member" -> MEMBER
            else -> throw IllegalArgumentException("Unknown household role: $value")
        }
    }
}

data class Household(
    val id: String,
    val name: String,
    val createdBy: String,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

data class HouseholdMember(
    val householdId: String,
    val userId: String,
    val role: HouseholdRole,
    val joinedAt: LocalDateTime,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

sealed class MembershipDecision {
    data object Allow : MembershipDecision()
    data class Deny(val reason: String) : MembershipDecision()
}

object HouseholdMembership {
    /** 013 states no length. 300 matches the text checks in 007_jobs.sql. */
    const val NAME_MAX = 300

    fun checkedName(raw: String): String? {
        val name = raw.trim()
        if (name.isEmpty() || name.length > NAME_MAX) return null
        return name
    }

    fun activeMembers(members: List<HouseholdMember>): List<HouseholdMember> =
        members.filter { it.deletedAt == null }

    fun mine(
        userId: String,
        households: List<Household>,
        members: List<HouseholdMember>,
    ): List<Household> {
        val ids = activeMembers(members)
            .filter { it.userId == userId }
            .map { it.householdId }
            .toSet()
        return households.filter { it.deletedAt == null && it.id in ids }
    }

    fun addMember(
        actor: HouseholdMember?,
        targetUserId: String,
        existing: HouseholdMember?,
    ): MembershipDecision {
        val userId = targetUserId.trim()
        if (userId.isEmpty()) return MembershipDecision.Deny("Enter a member")
        if (actor == null || actor.deletedAt != null || actor.role != HouseholdRole.OWNER) {
            return MembershipDecision.Deny("Only an owner can add a member")
        }
        if (userId == actor.userId) return MembershipDecision.Deny("You are already in this household")
        if (existing != null && existing.deletedAt == null) {
            return MembershipDecision.Deny("Already a member")
        }
        return MembershipDecision.Allow
    }

    fun leave(actor: HouseholdMember?, activeOwnerCount: Int): MembershipDecision {
        if (actor == null || actor.deletedAt != null) {
            return MembershipDecision.Deny("You are not in this household")
        }
        if (actor.role == HouseholdRole.OWNER && activeOwnerCount <= 1) {
            return MembershipDecision.Deny("The owner cannot leave")
        }
        return MembershipDecision.Allow
    }

    fun remove(actor: HouseholdMember?, target: HouseholdMember?): MembershipDecision {
        if (actor == null || actor.deletedAt != null || actor.role != HouseholdRole.OWNER) {
            return MembershipDecision.Deny("Only an owner can remove a member")
        }
        if (target == null || target.deletedAt != null || target.householdId != actor.householdId) {
            return MembershipDecision.Deny("That person is not a member")
        }
        if (target.userId == actor.userId) {
            return MembershipDecision.Deny("Leave instead of removing yourself")
        }
        if (target.role == HouseholdRole.OWNER) {
            return MembershipDecision.Deny("An owner cannot be removed")
        }
        return MembershipDecision.Allow
    }
}
