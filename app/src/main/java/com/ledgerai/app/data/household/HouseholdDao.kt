package com.ledgerai.app.data.household

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ledgerai.app.domain.household.HouseholdRole
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface HouseholdDao {

    @Query("SELECT * FROM households")
    fun observeHouseholds(): Flow<List<HouseholdEntity>>

    @Query("SELECT * FROM household_members")
    fun observeMembers(): Flow<List<HouseholdMemberEntity>>

    @Query("SELECT * FROM household_members WHERE householdId = :householdId")
    suspend fun membersOf(householdId: String): List<HouseholdMemberEntity>

    @Insert
    suspend fun insertHousehold(entity: HouseholdEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMember(entity: HouseholdMemberEntity)

    @Query(
        """
        UPDATE household_members
        SET deletedAt = :deletedAt, updatedAt = :updatedAt
        WHERE householdId = :householdId AND userId = :userId
        """
    )
    suspend fun softDeleteMember(
        householdId: String,
        userId: String,
        deletedAt: Long,
        updatedAt: Long,
    )

    @Transaction
    suspend fun insertHouseholdWithOwner(
        household: HouseholdEntity,
        ownerUserId: String,
        joinedAt: LocalDateTime,
        updatedAt: Long,
    ): String {
        insertHousehold(household)
        upsertMember(
            HouseholdMemberEntity(
                householdId = household.id,
                userId = ownerUserId,
                role = HouseholdRole.OWNER.stored,
                joinedAt = joinedAt,
                updatedAt = updatedAt,
            )
        )
        return household.id
    }
}
