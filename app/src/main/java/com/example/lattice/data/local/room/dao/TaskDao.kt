package com.example.lattice.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.lattice.data.local.room.entity.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Task实体的数据访问对象。
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

    @Query("SELECT * FROM tasks WHERE id = :id AND isDeleted = 0")
    suspend fun getTaskById(id: String): TaskEntity?

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

    // UPDATE functions
    @Update
    suspend fun updateTask(task: TaskEntity)

    // DELETE functions (soft delete)
    /**
     * Soft delete: mark task as deleted instead of physical deletion.
     */
    @Query("""
        UPDATE tasks 
        SET isDeleted = 1, 
            syncStatus = :syncStatus,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun softDeleteTaskById(id: String, syncStatus: String, updatedAt: Long)

    /**
     * Batch soft delete by ids.
     * 用于级联删除时一次性软删除所有子孙任务。
     */
    @Query("""
        UPDATE tasks 
        SET isDeleted = 1, 
            syncStatus = :syncStatus,
            updatedAt = :updatedAt
        WHERE id IN (:ids)
    """)
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





