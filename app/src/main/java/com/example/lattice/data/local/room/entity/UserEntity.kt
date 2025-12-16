package com.example.lattice.data.local.room.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey


/**
 * Room数据库中的User实体。
 * User entity in Room database.
 */
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey
    val id: String,
    val username: String,
    val passwordHash: String,
    val email: String? = null,
    /* Status attributes fields */
    val isDeleted: Boolean = false,
    /* Sync attributes fields */
    val remoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val lastSyncedAt: Long? = null
)




