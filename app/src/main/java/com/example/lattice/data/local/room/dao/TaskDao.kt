package com.example.lattice.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lattice.data.local.room.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for Task entities.
 */
@Dao
interface TaskDao {
    // CREATE functions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    // READ functions
    @Query(
        """
        SELECT * FROM tasks 
        WHERE isDeleted = 0
        ORDER BY 
            CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END,
            dueAt ASC,
            title ASC
        """
    )
    fun getAllTasksFlow(): Flow<List<TaskEntity>>

    /**
     * Normal read (exclude soft-deleted).
     */
    @Query("SELECT * FROM tasks WHERE id = :id AND isDeleted = 0")
    suspend fun getTaskById(id: String): TaskEntity?

    /**
     * Sync read: include soft-deleted tombstone.
     * Used by incremental sync merge logic.
     */
    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getTaskByIdAny(id: String): TaskEntity?

    @Query(
        """
        SELECT * FROM tasks 
        WHERE parentId = :parentId AND isDeleted = 0
        ORDER BY 
            CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END,
            dueAt ASC,
            title ASC
        """
    )
    fun getTasksByParentId(parentId: String?): Flow<List<TaskEntity>>

    @Query(
        """
        SELECT * FROM tasks 
        WHERE userId = :userId AND isDeleted = 0
        ORDER BY 
            CASE WHEN dueAt IS NULL THEN 1 ELSE 0 END,
            dueAt ASC,
            title ASC
        """
    )
    fun getTasksByUserId(userId: String): Flow<List<TaskEntity>>

    /**
     * Sync: return all dirty tasks (CREATED/UPDATED/DELETED) for a user.
     * Note: include soft-deleted tombstones so remote can receive deletions.
     */
    @Query("SELECT * FROM tasks WHERE userId = :userId AND syncStatus != 'SYNCED'")
    suspend fun getDirtyTasksByUserId(userId: String): List<TaskEntity>

    // UPDATE functions
    @Update
    suspend fun updateTask(task: TaskEntity)

    /**
     * Batch update isPostponed field for specific task ids.
     * Used when postponing tasks to tomorrow.
     *
     * Important for incremental sync:
     * - If a task is already CREATED (never synced), keep it CREATED.
     * - Otherwise mark as UPDATED.
     */
    @Query(
        """
        UPDATE tasks 
        SET isPostponed = :isPostponed,
            updatedAt = :updatedAt,
            syncStatus = CASE 
                WHEN syncStatus = 'CREATED' THEN 'CREATED'
                WHEN syncStatus = 'DELETED' THEN 'DELETED'
                ELSE 'UPDATED'
            END
        WHERE id IN (:ids)
        """
    )
    suspend fun updatePostponedStatus(ids: List<String>, isPostponed: Boolean, updatedAt: Long)

    /**
     *  Sync: after successful push, mark as SYNCED and write lastSyncedAt.
     */
    @Query(
        """
        UPDATE tasks 
        SET syncStatus = 'SYNCED',
            lastSyncedAt = :lastSyncedAt
        WHERE id IN (:ids)
        """
    )
    suspend fun markTasksSynced(ids: List<String>, lastSyncedAt: Long)

    /**
     * Get count of on-time completed tasks (isDone=1, isPostponed=0).
     */
    @Query(
        """
        SELECT COUNT(*) 
        FROM tasks 
        WHERE userId = :userId 
            AND isDone = 1 
            AND isPostponed = 0
            AND isDeleted = 0
        """
    )
    suspend fun getOnTimeCompletedCount(userId: String): Int

    /**
     * Get count of postponed completed tasks (isDone=1, isPostponed=1).
     */
    @Query(
        """
        SELECT COUNT(*) 
        FROM tasks 
        WHERE userId = :userId 
            AND isDone = 1 
            AND isPostponed = 1
            AND isDeleted = 0
        """
    )
    suspend fun getPostponedCompletedCount(userId: String): Int

    // DELETE functions (soft delete)
    /**
     * Soft delete: mark task as deleted instead of physical deletion.
     * For sync, pass TaskSyncStatus.DELETED.name as syncStatus.
     */
    @Query(
        """
        UPDATE tasks 
        SET isDeleted = 1, 
            syncStatus = :syncStatus,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun softDeleteTaskById(id: String, syncStatus: String, updatedAt: Long)

    /**
     * Batch soft delete by ids.
     * For sync, pass TaskSyncStatus.DELETED.name as syncStatus.
     */
    @Query(
        """
        UPDATE tasks 
        SET isDeleted = 1, 
            syncStatus = :syncStatus,
            updatedAt = :updatedAt
        WHERE id IN (:ids)
        """
    )
    suspend fun softDeleteTasksByIds(ids: List<String>, syncStatus: String, updatedAt: Long)

    // Physical delete functions (for cleanup, not used in normal flow)
    @Delete
    suspend fun deleteTask(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: String)

    @Query("DELETE FROM tasks WHERE id IN (:ids)")
    suspend fun deleteTasksByIds(ids: List<String>)

    @Query("DELETE FROM tasks WHERE parentId = :parentId")
    suspend fun deleteTasksByParentId(parentId: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()
}





