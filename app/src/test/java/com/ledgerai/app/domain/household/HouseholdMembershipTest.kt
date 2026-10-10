package com.ledgerai.app.domain.household

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDateTime

class HouseholdMembershipTest {

    @Test
    fun checkedName_trimsAndRejectsBlankOrOver300() {
        val valid = HouseholdMembership.checkedName("  Home  ")
        assertEquals("Home", valid)
        assertTrue(!valid.isNullOrBlank())
        assertNull(HouseholdMembership.checkedName(""))
        assertNull(HouseholdMembership.checkedName("   "))
        assertNull(HouseholdMembership.checkedName("a".repeat(301)))
        assertEquals("a".repeat(300), HouseholdMembership.checkedName("a".repeat(300)))
    }

    @Test
    fun role_storesDataModelValues() {
        assertEquals("owner", HouseholdRole.OWNER.stored)
        assertEquals("member", HouseholdRole.MEMBER.stored)
        assertEquals(HouseholdRole.OWNER, HouseholdRole.fromStored("owner"))
        assertEquals(HouseholdRole.MEMBER, HouseholdRole.fromStored("member"))
    }

    @Test
    fun role_unknownStoredValueIsRejected() {
        try {
            HouseholdRole.fromStored("admin")
            fail("unknown role was accepted")
        } catch (e: IllegalArgumentException) {
            assertEquals("Unknown household role: admin", e.message)
        }
    }

    @Test
    fun activeMembers_dropsSoftDeletedRows() {
        val live = member(userId = "a")
        val gone = member(userId = "b", deletedAt = 1L)
        assertEquals(listOf("a"), HouseholdMembership.activeMembers(listOf(live, gone)).map { it.userId })
    }

    @Test
    fun mine_keepsOnlyActiveHouseholdsTheUserStillBelongsTo() {
        val households = listOf(
            household("hh-1", "Home"),
            household("hh-2", "Gone", deletedAt = 5L),
            household("hh-3", "Other"),
        )
        val members = listOf(
            member(householdId = "hh-1", userId = "me", role = HouseholdRole.OWNER),
            member(householdId = "hh-2", userId = "me", role = HouseholdRole.OWNER),
            member(householdId = "hh-3", userId = "me", role = HouseholdRole.MEMBER, deletedAt = 9L),
            member(householdId = "hh-3", userId = "them", role = HouseholdRole.OWNER),
        )
        val mine = HouseholdMembership.mine("me", households, members)
        assertEquals(listOf("hh-1"), mine.map { it.id })
        assertEquals("Home", mine.single().name)
        assertEquals("me", mine.single().createdBy)
        assertNull(mine.single().deletedAt)
    }

    @Test
    fun mine_doesNotInventAHouseholdForSomeoneWhoIsNotAMember() {
        val households = listOf(household("hh-1", "Home"))
        val members = listOf(member(householdId = "hh-1", userId = "me", role = HouseholdRole.OWNER))
        assertTrue(HouseholdMembership.mine("stranger", households, members).isEmpty())
        assertTrue(HouseholdMembership.mine("me", households, emptyList()).isEmpty())
    }

    @Test
    fun addMember_ownerMayAddSomeoneNew() {
        val decision = HouseholdMembership.addMember(
            actor = member(userId = "me", role = HouseholdRole.OWNER),
            targetUserId = " sam ",
            existing = null,
        )
        assertTrue(decision is MembershipDecision.Allow)
    }

    @Test
    fun addMember_rejectsMemberActorBlankIdAndActiveDuplicate() {
        val owner = member(userId = "me", role = HouseholdRole.OWNER)
        val member = member(userId = "sam", role = HouseholdRole.MEMBER)
        assertTrue(HouseholdMembership.addMember(member, "new", null) is MembershipDecision.Deny)
        assertTrue(HouseholdMembership.addMember(null, "new", null) is MembershipDecision.Deny)
        assertTrue(HouseholdMembership.addMember(owner, "  ", null) is MembershipDecision.Deny)
        assertTrue(
            HouseholdMembership.addMember(owner, "sam", member) is MembershipDecision.Deny
        )
        assertTrue(
            HouseholdMembership.addMember(owner, "me", null) is MembershipDecision.Deny
        )
    }

    @Test
    fun addMember_allowsRejoinAfterSoftDelete() {
        val decision = HouseholdMembership.addMember(
            actor = member(userId = "me", role = HouseholdRole.OWNER),
            targetUserId = "sam",
            existing = member(userId = "sam", deletedAt = 4L),
        )
        assertTrue(decision is MembershipDecision.Allow)
    }

    @Test
    fun leave_memberMayLeaveAndSoleOwnerMayNot() {
        assertTrue(
            HouseholdMembership.leave(member(role = HouseholdRole.MEMBER), activeOwnerCount = 1)
                is MembershipDecision.Allow
        )
        assertTrue(
            HouseholdMembership.leave(member(role = HouseholdRole.OWNER), activeOwnerCount = 1)
                is MembershipDecision.Deny
        )
        assertTrue(
            HouseholdMembership.leave(member(role = HouseholdRole.OWNER), activeOwnerCount = 2)
                is MembershipDecision.Allow
        )
        assertTrue(HouseholdMembership.leave(null, activeOwnerCount = 1) is MembershipDecision.Deny)
    }

    @Test
    fun remove_onlyOwnerRemovesAMember() {
        val owner = member(userId = "me", role = HouseholdRole.OWNER)
        val otherOwner = member(userId = "pat", role = HouseholdRole.OWNER)
        val member = member(userId = "sam", role = HouseholdRole.MEMBER)
        assertTrue(HouseholdMembership.remove(owner, member) is MembershipDecision.Allow)
        assertTrue(HouseholdMembership.remove(member, owner) is MembershipDecision.Deny)
        assertTrue(HouseholdMembership.remove(owner, otherOwner) is MembershipDecision.Deny)
        assertTrue(HouseholdMembership.remove(owner, owner) is MembershipDecision.Deny)
        assertTrue(HouseholdMembership.remove(owner, null) is MembershipDecision.Deny)
        assertTrue(
            HouseholdMembership.remove(owner, member.copy(deletedAt = 3L)) is MembershipDecision.Deny
        )
    }

    @Test
    fun remove_rejectsAMemberFromAnotherHousehold() {
        val owner = member(householdId = "hh-1", userId = "me", role = HouseholdRole.OWNER)
        val outsider = member(householdId = "hh-2", userId = "sam", role = HouseholdRole.MEMBER)
        assertTrue(HouseholdMembership.remove(owner, outsider) is MembershipDecision.Deny)
    }

    @Test
    fun addMember_softDeletedOwnerIsDenied() {
        val decision = HouseholdMembership.addMember(
            actor = member(userId = "me", role = HouseholdRole.OWNER, deletedAt = 2L),
            targetUserId = "sam",
            existing = null,
        )
        assertTrue(decision is MembershipDecision.Deny)
    }

    private fun household(id: String, name: String, deletedAt: Long? = null) = Household(
        id = id,
        name = name,
        createdBy = "me",
        deletedAt = deletedAt,
    )

    private fun member(
        householdId: String = "hh-1",
        userId: String = "sam",
        role: HouseholdRole = HouseholdRole.MEMBER,
        deletedAt: Long? = null,
    ) = HouseholdMember(
        householdId = householdId,
        userId = userId,
        role = role,
        joinedAt = LocalDateTime.of(2026, 10, 11, 0, 0),
        deletedAt = deletedAt,
    )
}
