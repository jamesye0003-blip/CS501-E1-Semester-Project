package com.example.lattice.data.local.room.mapper

import com.example.lattice.data.local.room.entity.TaskEntity
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
     */
    fun toEntity(task: Task, ownerUserId: String): TaskEntity {
        return TaskEntity(
            id = task.id,
            userId = ownerUserId,
            title = task.title,
            description = task.description,
            priority = task.priority.name,
            done = task.done,
            parentId = task.parentId,
            dueAt = task.dueAt?.toEpochMilli(),
            hasSpecificTime = task.hasSpecificTime,
            sourceTimeZoneId = task.sourceTimeZoneId
        )
    }
    
    /**
     * Convert Room entity to Task of Domain Layer.
     */
    fun toDomain(entity: TaskEntity): Task {
        return Task(
            id = entity.id,
            userId = entity.userId,
            title = entity.title,
            description = entity.description,
            priority = runCatching { Priority.valueOf(entity.priority) }
                .getOrDefault(Priority.None),
            done = entity.done,
            parentId = entity.parentId,
            dueAt = entity.dueAt?.let { Instant.ofEpochMilli(it) },
            hasSpecificTime = entity.hasSpecificTime,
            sourceTimeZoneId = entity.sourceTimeZoneId
        )
    }
    
    /**
     * Convert a list of entities to a list of Domain Layer.
     */
    fun toDomainList(entities: List<TaskEntity>): List<Task> {
        return entities.map { toDomain(it) }
    }
    
    /**
     * Convert a list of Domain Layer tasks to entity list.
     */
    fun toEntityList(tasks: List<Task>, ownerUserId: String): List<TaskEntity> {
        return tasks.map { toEntity(it, ownerUserId) }
    }
}




