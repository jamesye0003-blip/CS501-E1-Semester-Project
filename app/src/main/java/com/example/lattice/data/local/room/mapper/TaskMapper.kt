package com.example.lattice.data.local.room.mapper

import com.example.lattice.data.local.room.entity.TaskEntity
import com.example.lattice.data.local.room.entity.TaskSyncStatus
import com.example.lattice.domain.model.Priority
import com.example.lattice.domain.model.Task
import java.time.Instant

/**
 * Task实体和Domain模型之间的映射器。
 * Mapper between Task entity and domain model.
 */
object TaskMapper {
    
    /**
     * Convert Task of Domain Layer to Room entity.
     * 
     * Note: Domain layer Task doesn't have sync fields, so we set defaults:
     * - For new tasks: syncStatus=CREATED, createdAt/updatedAt=now()
     * - For existing tasks: syncStatus=UPDATED, updatedAt=now()
     */
    fun toEntity(task: Task, ownerUserId: String, isNew: Boolean = false): TaskEntity {
        val now = System.currentTimeMillis()
        return TaskEntity(
            id = task.id,
            parentId = task.parentId,
            userId = ownerUserId,
            title = task.title,
            description = task.description,
            priority = task.priority.name,
            dueAt = task.dueAt?.toEpochMilli(),
            hasSpecificTime = task.hasSpecificTime,
            sourceTimeZoneId = task.sourceTimeZoneId,
            isDone = task.done,
            isPostponed = false,  // Domain layer doesn't have this, default to false
            isCancelled = false,  // Domain layer doesn't have this, default to false
            remoteId = task.id,  // According to doc: remoteId == id
            createdAt = now,  // For new tasks, will be set properly; for updates, should preserve original
            updatedAt = now,
            lastSyncedAt = null,  // Will be set after sync
            isDeleted = false,
            syncStatus = if (isNew) TaskSyncStatus.CREATED else TaskSyncStatus.UPDATED
        )
    }
    
    /**
     * Convert Room entity to Task of Domain Layer.
     * Only converts non-deleted tasks (isDeleted=false).
     */
    fun toDomain(entity: TaskEntity): Task {
        return Task(
            id = entity.id,
            userId = entity.userId,
            title = entity.title,
            description = entity.description,
            priority = runCatching { Priority.valueOf(entity.priority) }
                .getOrDefault(Priority.None),
            done = entity.isDone,  // Map isDone -> done
            parentId = entity.parentId,
            dueAt = entity.dueAt?.let { Instant.ofEpochMilli(it) },
            hasSpecificTime = entity.hasSpecificTime,
            sourceTimeZoneId = entity.sourceTimeZoneId
        )
    }
    
    /**
     * Convert a list of entities to a list of Domain Layer.
     * Filters out deleted tasks (isDeleted=true).
     */
    fun toDomainList(entities: List<TaskEntity>): List<Task> {
        return entities
            .filter { !it.isDeleted }  // Only return non-deleted tasks
            .map { toDomain(it) }
    }
    
    /**
     * Convert a list of Domain Layer tasks to entity list.
     */
    fun toEntityList(tasks: List<Task>, ownerUserId: String, isNew: Boolean = false): List<TaskEntity> {
        return tasks.map { toEntity(it, ownerUserId, isNew) }
    }
}




