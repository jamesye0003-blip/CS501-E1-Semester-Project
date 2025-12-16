package com.example.lattice.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.lattice.domain.model.Priority
import com.example.lattice.data.local.room.entity.UserEntity


enum class TaskSyncStatus { SYNCED, CREATED, UPDATED, DELETED }

/**
 * Room数据库中的Task实体。
 * Task entity in Room database.
 *
 * 时间字段设计：
 * - dueAt: 存储UTC时刻（Instant转换为Long），可为null
 *          UTC instant millis, nullable
 * - hasSpecificTime:   标记是否包含具体时间（时分秒）
 *                      whether time-of-day exists
 * - sourceTimeZoneId:  记录创建时的源时区ID
 *                      source zone id when created
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["userId"]),
        Index(value = ["parentId"]),
        Index(value = ["remoteId"]),
    ]
)
data class TaskEntity(
    @PrimaryKey
    val id: String,                       // localTaskId (UUID) = remote docId
    val parentId: String? = null,         // Tree construction（for the same user）
    val userId: String,                   // localUserId foreign key

    /* Basic attributes fields */
    val title: String,
    val description: String = "",
    val priority: String = Priority.None.name, // data layer: String; domain layer mapping to Priority enum

    /* Time attributes fields */
    val dueAt: Long? = null,              // Absolute deadline UTC millis; null if unset.
    val hasSpecificTime: Boolean = false, // Whether time-of-day is present (true) or all-day (false).
    val sourceTimeZoneId: String? = null, // Source time zone id when created.

    /* Attachment attributes fields */
    val attachments: List<com.example.lattice.domain.model.Attachment>? = null, // stored as JSON via Converters

    /* Status attributes fields */
    val isDone: Boolean = false,
    val isPostponed: Boolean = false,
    val isCancelled: Boolean = false,

    /* Sync attributes fields */
    val remoteId: String = id,            // Fixed id：remoteId == id（not null）
    val createdAt: Long,                  // Creation timestamp UTC millis.
    val updatedAt: Long,                  // Last update timestamp UTC millis.
    val lastSyncedAt: Long? = null,       // Last sync timestamp UTC millis; null if never synced.
    val isDeleted: Boolean = false,       // Soft delete tombstone.
    val syncStatus: TaskSyncStatus = TaskSyncStatus.CREATED
)



