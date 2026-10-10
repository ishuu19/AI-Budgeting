package com.ledgerai.app.data.household

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ledgerai.app.domain.household.Household
import com.ledgerai.app.domain.household.HouseholdMember
import com.ledgerai.app.domain.household.HouseholdRole
import java.time.LocalDateTime

// Columns match Room v12 and supabase/planned/migrations/013_households.sql.
// id is a String so inventory and receipts can store it in householdId: String?.

@Entity(tableName = "households")
data class HouseholdEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdBy: String,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

@Entity(
    tableName = "household_members",
    primaryKeys = ["householdId", "userId"],
)
data class HouseholdMemberEntity(
    val householdId: String,
    val userId: String,
    val role: String,
    val joinedAt: LocalDateTime,
    val updatedAt: Long = 0L,
    val deletedAt: Long? = null,
)

fun HouseholdEntity.toDomain() = Household(
    id = id,
    name = name,
    createdBy = createdBy,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)

fun HouseholdMemberEntity.toDomain() = HouseholdMember(
    householdId = householdId,
    userId = userId,
    role = HouseholdRole.fromStored(role),
    joinedAt = joinedAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
)
