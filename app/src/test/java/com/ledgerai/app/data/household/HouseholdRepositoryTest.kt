package com.ledgerai.app.data.household

import com.ledgerai.app.domain.household.HouseholdRole
import com.ledgerai.app.domain.household.MembershipDecision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class HouseholdRepositoryTest {

    private val dao = MemoryHouseholdDao()
    private var now = 1_000L
    private var joinedAt = LocalDateTime.of(2026, 10, 11, 2, 0)
    private val repository = HouseholdRepository(
        dao,
        nowMillis = { now },
        nowDateTime = { joinedAt },
    )

    @Test
    fun createHousehold_rejectsBlankOrOverlongAndWritesNothing() = runBlocking {
        assertNull(repository.createHousehold("", "me"))
        assertNull(repository.createHousehold("   ", "me"))
        assertNull(repository.createHousehold("a".repeat(301), "me"))
        assertTrue(dao.households.isEmpty())
        assertTrue(dao.members.isEmpty())
        assertTrue(repository.listMine("me").isEmpty())
    }

    @Test
    fun createHousehold_returnsNonBlankStringIdForAValidName() = runBlocking {
        val id = repository.createHousehold("  Home  ", "me")
        assertTrue(!id.isNullOrBlank())
        val stored = dao.households.single()
        assertEquals(id, stored.id)
        assertEquals("Home", stored.name)
        assertEquals("me", stored.createdBy)
        assertEquals(1_000L, stored.updatedAt)
        assertNull(stored.deletedAt)
        val owner = dao.members.single()
        assertEquals(id, owner.householdId)
        assertEquals("me", owner.userId)
        assertEquals("owner", owner.role)
        assertEquals(joinedAt, owner.joinedAt)
        assertNull(owner.deletedAt)
        val listed = repository.listMine("me").single()
        assertEquals(id, listed.id)
        assertEquals("Home", listed.name)
        assertEquals("me", listed.createdBy)
        assertNull(listed.deletedAt)
    }

    @Test
    fun createHousehold_doesNotInventMembershipOrAPlaceholderName() = runBlocking {
        val id = repository.createHousehold("Home", "me")!!
        assertEquals(listOf("me"), dao.members.map { it.userId })
        assertEquals("owner", dao.members.single().role)
        assertTrue(repository.listMine("stranger").isEmpty())
        assertNull(repository.createHousehold("   ", "stranger"))
        assertEquals(listOf(id), dao.households.map { it.id })
        assertEquals(listOf("me"), dao.members.map { it.userId })
        assertEquals(listOf("Home"), dao.households.map { it.name })
    }

    @Test
    fun listMine_excludesSoftDeletedHouseholdsAndMemberships() = runBlocking {
        dao.insertHousehold(HouseholdEntity(id = "hh-live", name = "Home", createdBy = "me", updatedAt = 1))
        dao.insertHousehold(
            HouseholdEntity(id = "hh-gone", name = "Gone", createdBy = "me", updatedAt = 1, deletedAt = 5),
        )
        dao.insertHousehold(HouseholdEntity(id = "hh-other", name = "Other", createdBy = "them", updatedAt = 1))
        dao.upsertMember(member("hh-live", "me", HouseholdRole.OWNER.stored))
        dao.upsertMember(member("hh-gone", "me", HouseholdRole.OWNER.stored))
        dao.upsertMember(member("hh-other", "me", HouseholdRole.MEMBER.stored, deletedAt = 9))
        dao.upsertMember(member("hh-other", "them", HouseholdRole.OWNER.stored))

        assertEquals(listOf("hh-live"), repository.listMine("me").map { it.id })
        assertEquals(listOf("them"), repository.observeMembers("hh-other").first().map { it.userId })
        assertEquals(listOf("hh-other"), repository.listMine("them").map { it.id })
    }

    @Test
    fun leave_soleOwnerCannotLeaveAndAMemberCan() = runBlocking {
        val id = repository.createHousehold("Home", "me")!!
        assertTrue(repository.leave(id, "me") is MembershipDecision.Deny)
        assertNull(dao.members.single().deletedAt)

        now = 2_000L
        joinedAt = LocalDateTime.of(2026, 10, 11, 3, 0)
        assertTrue(repository.addMember(id, "me", " sam ") is MembershipDecision.Allow)
        assertEquals("member", dao.members.single { it.userId == "sam" }.role)
        assertTrue(repository.addMember(id, "sam", "lee") is MembershipDecision.Deny)
        assertTrue(dao.members.none { it.userId == "lee" })
        assertTrue(repository.leave(id, "me") is MembershipDecision.Deny)

        now = 3_000L
        assertTrue(repository.leave(id, "sam") is MembershipDecision.Allow)
        assertEquals(3_000L, dao.members.single { it.userId == "sam" }.deletedAt)
        assertEquals(listOf("me"), repository.observeMembers(id).first().map { it.userId })
        assertEquals(listOf(id), repository.listMine("me").map { it.id })
        assertTrue(repository.listMine("sam").isEmpty())

        dao.upsertMember(member(id, "pat", HouseholdRole.OWNER.stored))
        now = 4_000L
        assertTrue(repository.leave(id, "me") is MembershipDecision.Allow)
        assertEquals(4_000L, dao.members.single { it.userId == "me" }.deletedAt)
        assertTrue(repository.listMine("me").isEmpty())
        assertEquals(listOf(id), repository.listMine("pat").map { it.id })
    }

    @Test
    fun removeMember_ownerCannotRemoveAnotherOwner() = runBlocking {
        val id = repository.createHousehold("Home", "me")!!
        assertTrue(repository.addMember(id, "me", "sam") is MembershipDecision.Allow)
        dao.upsertMember(member(id, "pat", HouseholdRole.OWNER.stored))
        dao.insertHousehold(HouseholdEntity(id = "hh-other", name = "Theirs", createdBy = "them", updatedAt = 1))
        dao.upsertMember(member("hh-other", "lee", HouseholdRole.MEMBER.stored))

        assertTrue(repository.removeMember(id, "me", "pat") is MembershipDecision.Deny)
        assertNull(dao.members.single { it.userId == "pat" }.deletedAt)
        assertTrue(repository.removeMember(id, "sam", "pat") is MembershipDecision.Deny)
        assertTrue(repository.removeMember(id, "me", "me") is MembershipDecision.Deny)
        assertTrue(repository.removeMember(id, "me", "lee") is MembershipDecision.Deny)
        assertNull(dao.members.single { it.userId == "lee" }.deletedAt)

        now = 5_000L
        assertTrue(repository.removeMember(id, "me", "sam") is MembershipDecision.Allow)
        assertEquals(5_000L, dao.members.single { it.userId == "sam" }.deletedAt)
        assertEquals(listOf("me", "pat"), repository.observeMembers(id).first().map { it.userId })
    }

    private fun member(
        householdId: String,
        userId: String,
        role: String,
        deletedAt: Long? = null,
    ) = HouseholdMemberEntity(
        householdId = householdId,
        userId = userId,
        role = role,
        joinedAt = LocalDateTime.of(2026, 10, 11, 0, 0),
        updatedAt = 1L,
        deletedAt = deletedAt,
    )
}

private class MemoryHouseholdDao : HouseholdDao {
    val households = mutableListOf<HouseholdEntity>()
    val members = mutableListOf<HouseholdMemberEntity>()
    private val householdTicks = MutableStateFlow(0)
    private val memberTicks = MutableStateFlow(0)

    override fun observeHouseholds(): Flow<List<HouseholdEntity>> =
        householdTicks.map { households.toList() }

    override fun observeMembers(): Flow<List<HouseholdMemberEntity>> =
        memberTicks.map { members.toList() }

    override suspend fun membersOf(householdId: String): List<HouseholdMemberEntity> =
        members.filter { it.householdId == householdId }.toList()

    override suspend fun insertHousehold(entity: HouseholdEntity) {
        households += entity
        householdTicks.value += 1
    }

    override suspend fun upsertMember(entity: HouseholdMemberEntity) {
        val index = members.indexOfFirst {
            it.householdId == entity.householdId && it.userId == entity.userId
        }
        if (index >= 0) members[index] = entity else members += entity
        memberTicks.value += 1
    }

    override suspend fun softDeleteMember(
        householdId: String,
        userId: String,
        deletedAt: Long,
        updatedAt: Long,
    ) {
        val index = members.indexOfFirst { it.householdId == householdId && it.userId == userId }
        if (index < 0) return
        members[index] = members[index].copy(deletedAt = deletedAt, updatedAt = updatedAt)
        memberTicks.value += 1
    }
}
