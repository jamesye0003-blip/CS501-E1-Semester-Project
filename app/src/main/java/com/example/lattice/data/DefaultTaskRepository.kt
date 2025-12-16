package com.example.lattice.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.lattice.data.local.datastore.authDataStore
import com.example.lattice.data.local.datastore.settingsDataStore
import com.example.lattice.data.local.room.dao.UserDao
import com.example.lattice.data.local.room.db.AppDatabase
import com.example.lattice.data.local.room.entity.TaskEntity
import com.example.lattice.data.local.room.entity.TaskSyncStatus
import com.example.lattice.data.local.room.mapper.TaskMapper
import com.example.lattice.domain.model.Attachment
import com.example.lattice.domain.model.AttachmentType
import com.example.lattice.domain.model.Task
import com.example.lattice.domain.repository.TaskRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.math.max

private val USER_ID_KEY = stringPreferencesKey("user_id")

private const val SKEW_WINDOW_MS: Long = 3 * 60 * 1000 // 3 minutes

/**
 * Task repository backed by Room.
 * Adds Firestore incremental sync (Pull + Push) when current user is bound to a remote uid.
 */
class DefaultTaskRepository(private val context: Context) : TaskRepository {

    private val database = AppDatabase.getDatabase(context)
    private val taskDao = database.taskDao()
    private val userDao: UserDao = database.userDao()

    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private val syncMutex = Mutex()

    private val currentUserIdFlow: Flow<String?> =
        context.authDataStore.data.map { prefs -> prefs[USER_ID_KEY] }

    override val tasksFlow: Flow<List<Task>> =
        currentUserIdFlow.flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                taskDao
                    .getTasksByUserId(userId)
                    .onStart {
                        // Auto sync when tasks are first observed for this user (e.g., after login).
                        syncIfPossible(localUserId = userId)
                    }
                    .map { entities -> TaskMapper.toDomainList(entities) }
            }
        }

    /**
     * Manual sync entrypoint required by TaskRepository.
     * Calls the same incremental pull + best-effort push used by auto sync.
     */
    override suspend fun syncNow(): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val userId = currentUserIdFlow.first()
                if (!userId.isNullOrBlank()) {
                    syncIfPossible(localUserId = userId)
                }
            }
        }

    override suspend fun saveTasks(tasks: List<Task>) {
        withContext(Dispatchers.IO) {
            val userId = currentUserIdFlow.first()
            if (userId.isNullOrBlank()) return@withContext

            // Existing (non-deleted) tasks snapshot
            val existingTasks = taskDao.getTasksByUserId(userId).first()
            val existingTaskMap = existingTasks.associateBy { it.id }

            val now = System.currentTimeMillis()

            // Only upsert tasks that are new or actually changed, to avoid bumping updatedAt for everything.
            val dirtyEntities = mutableListOf<TaskEntity>()

            for (task in tasks) {
                val existing = existingTaskMap[task.id]

                if (existing == null) {
                    // New task
                    val entity = TaskMapper.toEntity(task, userId, isNew = true).copy(
                        createdAt = now,
                        updatedAt = now,
                        lastSyncedAt = null,
                        remoteId = task.id, // docId = taskId
                        isDeleted = false,
                        syncStatus = TaskSyncStatus.CREATED
                    )
                    dirtyEntities.add(entity)
                } else {
                    // Candidate updated entity (preserve immutable / sync fields first)
                    val candidate = TaskMapper.toEntity(task, userId, isNew = false).copy(
                        createdAt = existing.createdAt,
                        lastSyncedAt = existing.lastSyncedAt,
                        remoteId = existing.remoteId ?: existing.id,
                        isPostponed = existing.isPostponed,
                        isCancelled = existing.isCancelled,
                        isDeleted = existing.isDeleted
                    )

                    if (!hasMeaningfulChanges(candidate, existing)) {
                        // No real change; keep DB row untouched.
                        continue
                    }

                    val nextStatus =
                        when (existing.syncStatus) {
                            TaskSyncStatus.CREATED -> TaskSyncStatus.CREATED
                            TaskSyncStatus.UPDATED -> TaskSyncStatus.UPDATED
                            TaskSyncStatus.SYNCED -> TaskSyncStatus.UPDATED
                            TaskSyncStatus.DELETED -> TaskSyncStatus.DELETED
                        }

                    dirtyEntities.add(
                        candidate.copy(
                            updatedAt = now,
                            syncStatus = nextStatus
                        )
                    )
                }
            }

            if (dirtyEntities.isNotEmpty()) {
                taskDao.insertTasks(dirtyEntities)
            }

            // Push only changed rows (best-effort; will no-op if user not bound to remote).
            pushIfPossible(localUserId = userId, dirtyEntities = dirtyEntities)
        }
    }

    /**
     * Delete single task (soft delete). Convenience wrapper.
     */
    suspend fun deleteTask(id: String) {
        deleteTasks(listOf(id))
    }

    /**
     * Delete multiple tasks by ids (soft delete) + push tombstones to remote if available.
     */
    override suspend fun deleteTasks(ids: List<String>) {
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext

            val userId = currentUserIdFlow.first()
            if (userId.isNullOrBlank()) return@withContext

            val now = System.currentTimeMillis()
            val syncStatus = TaskSyncStatus.DELETED.name

            // Local tombstones
            taskDao.softDeleteTasksByIds(ids, syncStatus, now)

            // Remote tombstones (best-effort)
            pushDeletesIfPossible(localUserId = userId, taskIds = ids, deletedAt = now)
        }
    }

    /**
     * Update isPostponed status for specific tasks, and ensure it becomes "dirty" for sync.
     */
    suspend fun updatePostponedStatus(ids: List<String>, isPostponed: Boolean) {
        withContext(Dispatchers.IO) {
            if (ids.isEmpty()) return@withContext

            val userId = currentUserIdFlow.first()
            if (userId.isNullOrBlank()) return@withContext

            val now = System.currentTimeMillis()

            val dirtyEntities = mutableListOf<TaskEntity>()
            for (id in ids) {
                val existing = taskDao.getTaskById(id) ?: continue
                val nextStatus =
                    when (existing.syncStatus) {
                        TaskSyncStatus.CREATED -> TaskSyncStatus.CREATED
                        TaskSyncStatus.UPDATED -> TaskSyncStatus.UPDATED
                        TaskSyncStatus.SYNCED -> TaskSyncStatus.UPDATED
                        TaskSyncStatus.DELETED -> TaskSyncStatus.DELETED
                    }
                dirtyEntities.add(
                    existing.copy(
                        isPostponed = isPostponed,
                        updatedAt = now,
                        syncStatus = nextStatus
                    )
                )
            }

            if (dirtyEntities.isNotEmpty()) {
                taskDao.insertTasks(dirtyEntities)
            }

            pushIfPossible(localUserId = userId, dirtyEntities = dirtyEntities)
        }
    }

    /**
     * Stats (unchanged).
     */
    suspend fun getCompletedTaskStats(userId: String): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            val onTimeCount = taskDao.getOnTimeCompletedCount(userId)
            val postponedCount = taskDao.getPostponedCompletedCount(userId)
            Pair(onTimeCount, postponedCount)
        }
    }

    // ---------------------------
    // Sync (Firestore) - internal
    // ---------------------------

    private suspend fun syncIfPossible(localUserId: String) {
        withContext(Dispatchers.IO) {
            syncMutex.withLock {
                val remoteUid = userDao.getUserById(localUserId)?.remoteId
                if (remoteUid.isNullOrBlank()) return@withLock

                // 1) Pull (incremental)
                pullRemoteIncremental(localUserId = localUserId, remoteUid = remoteUid)

                // 2) Push (best-effort): push current dirty tasks in DB snapshot
                val current = taskDao.getTasksByUserId(localUserId).first()
                val dirty = current.filter { it.syncStatus != TaskSyncStatus.SYNCED && !it.isDeleted }
                pushIfPossible(localUserId = localUserId, dirtyEntities = dirty)
            }
        }
    }

    private suspend fun pullRemoteIncremental(localUserId: String, remoteUid: String) {
        val cursorKey = longPreferencesKey("remoteSyncCursor_$localUserId")
        val cursor = context.settingsDataStore.data.first()[cursorKey] ?: 0L
        val cursorSafe = max(0L, cursor - SKEW_WINDOW_MS)

        val col = firestore.collection("users").document(remoteUid).collection("tasks")

        val snap = col
            .whereGreaterThan("updatedAt", cursorSafe)
            .orderBy("updatedAt", Query.Direction.ASCENDING)
            .get()
            .await()

        if (snap.isEmpty) return

        // Compare only with current active tasks snapshot (good enough for v1).
        val localActive = taskDao.getTasksByUserId(localUserId).first()
        val localMap = localActive.associateBy { it.id }

        var maxRemoteUpdatedAt = cursor
        val toApply = mutableListOf<TaskEntity>()

        for (doc in snap.documents) {
            val remoteEntity = docToTaskEntity(
                docId = doc.id,
                localUserId = localUserId,
                data = doc.data ?: emptyMap()
            ) ?: continue

            maxRemoteUpdatedAt = max(maxRemoteUpdatedAt, remoteEntity.updatedAt)

            val local = localMap[remoteEntity.id]
            val apply = when {
                local == null -> true
                local.syncStatus == TaskSyncStatus.SYNCED && remoteEntity.updatedAt > local.updatedAt -> true
                local.syncStatus != TaskSyncStatus.SYNCED && remoteEntity.updatedAt >= local.updatedAt -> true // simple LWW
                else -> false // local newer => keep local
            }

            if (apply) {
                toApply.add(
                    remoteEntity.copy(
                        syncStatus = TaskSyncStatus.SYNCED,
                        lastSyncedAt = remoteEntity.updatedAt
                    )
                )
            }
        }

        if (toApply.isNotEmpty()) {
            taskDao.insertTasks(toApply)
        }

        // advance cursor
        context.settingsDataStore.edit { prefs ->
            prefs[cursorKey] = maxRemoteUpdatedAt
        }
    }

    private suspend fun pushIfPossible(localUserId: String, dirtyEntities: List<TaskEntity>) {
        if (dirtyEntities.isEmpty()) return

        val remoteUid = userDao.getUserById(localUserId)?.remoteId
        if (remoteUid.isNullOrBlank()) return

        val col = firestore.collection("users").document(remoteUid).collection("tasks")
        val batch = firestore.batch()

        for (e in dirtyEntities) {
            val docId = e.remoteId ?: e.id
            batch.set(
                col.document(docId),
                taskEntityToDoc(e),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }

        batch.commit().await()

        // Mark as synced locally (best-effort; keep updatedAt as-is)
        val now = System.currentTimeMillis()
        val syncedCopies = dirtyEntities.map { it.copy(syncStatus = TaskSyncStatus.SYNCED, lastSyncedAt = now) }
        taskDao.insertTasks(syncedCopies)
    }

    private suspend fun pushDeletesIfPossible(localUserId: String, taskIds: List<String>, deletedAt: Long) {
        val remoteUid = userDao.getUserById(localUserId)?.remoteId
        if (remoteUid.isNullOrBlank()) return

        val col = firestore.collection("users").document(remoteUid).collection("tasks")
        val batch = firestore.batch()

        for (taskId in taskIds) {
            val docRef = col.document(taskId)
            batch.set(
                docRef,
                mapOf(
                    "id" to taskId,
                    "updatedAt" to deletedAt,
                    "isDeleted" to true
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }

        batch.commit().await()
        // v1：不强制把本地 tombstone 的 syncStatus 改回 SYNCED（避免需要额外 DAO 查询 deleted 行）。
    }

    // ---------------------------
    // Mapping helpers
    // ---------------------------

    private fun hasMeaningfulChanges(a: TaskEntity, b: TaskEntity): Boolean {
        return a.title != b.title ||
                a.description != b.description ||
                a.priority != b.priority ||
                a.parentId != b.parentId ||
                a.dueAt != b.dueAt ||
                a.hasSpecificTime != b.hasSpecificTime ||
                a.sourceTimeZoneId != b.sourceTimeZoneId ||
                a.isDone != b.isDone ||
                a.attachments != b.attachments
    }

    private fun taskEntityToDoc(e: TaskEntity): Map<String, Any?> {
        return mapOf(
            "id" to e.id,
            "parentId" to e.parentId,
            "title" to e.title,
            "description" to e.description,
            "priority" to e.priority,
            "dueAt" to e.dueAt,
            "hasSpecificTime" to e.hasSpecificTime,
            "sourceTimeZoneId" to e.sourceTimeZoneId,
            "isDone" to e.isDone,
            "isPostponed" to e.isPostponed,
            "isCancelled" to e.isCancelled,
            "isDeleted" to e.isDeleted,
            "createdAt" to e.createdAt,
            "updatedAt" to e.updatedAt,
            "attachments" to (e.attachments ?: emptyList()).map { att ->
                mapOf(
                    "id" to att.id,
                    "filePath" to att.filePath,
                    "fileName" to att.fileName,
                    "fileType" to att.fileType.name,
                    "mimeType" to att.mimeType,
                    "fileSize" to att.fileSize
                )
            }
        )
    }

    private fun docToTaskEntity(
        docId: String,
        localUserId: String,
        data: Map<String, Any?>
    ): TaskEntity? {
        val title = data["title"] as? String ?: return null

        val attachments = (data["attachments"] as? List<*>)?.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            val filePath = m["filePath"] as? String ?: return@mapNotNull null
            val fileName = m["fileName"] as? String ?: return@mapNotNull null
            val fileTypeStr = m["fileType"] as? String ?: AttachmentType.OTHER.name
            val mimeType = m["mimeType"] as? String
            val fileSize = (m["fileSize"] as? Number)?.toLong()
            Attachment(
                id = id,
                filePath = filePath,
                fileName = fileName,
                fileType = runCatching { AttachmentType.valueOf(fileTypeStr) }.getOrDefault(AttachmentType.OTHER),
                mimeType = mimeType,
                fileSize = fileSize
            )
        }

        val createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: createdAt

        return TaskEntity(
            id = docId,
            parentId = data["parentId"] as? String,
            userId = localUserId,
            title = title,
            description = data["description"] as? String ?: "",
            priority = data["priority"] as? String ?: "None",
            dueAt = (data["dueAt"] as? Number)?.toLong(),
            hasSpecificTime = data["hasSpecificTime"] as? Boolean ?: false,
            sourceTimeZoneId = data["sourceTimeZoneId"] as? String,
            attachments = attachments,
            isDone = data["isDone"] as? Boolean ?: false,
            isPostponed = data["isPostponed"] as? Boolean ?: false,
            isCancelled = data["isCancelled"] as? Boolean ?: false,
            remoteId = docId,
            createdAt = createdAt,
            updatedAt = updatedAt,
            lastSyncedAt = updatedAt,
            isDeleted = data["isDeleted"] as? Boolean ?: false,
            syncStatus = TaskSyncStatus.SYNCED
        )
    }
}
