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
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
 * 基于 Room 的任务仓库 / Task repository backed by Room
 * 
 * 当当前用户绑定到远程 uid 时，添加 Firestore 增量同步（拉取 + 推送）。
 * 
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

    /**
     * 任务列表流 / Task list flow
     * 
     * 观察当前用户的任务列表变化。
     * 当用户 ID 变化时自动切换观察的用户任务数据。
     * 首次观察时自动触发同步（例如登录后）。
     * 
     * Observes current user's task list changes.
     * Automatically switches observed user task data when user ID changes.
     * Auto-triggers sync when first observed (e.g., after login).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val tasksFlow: Flow<List<Task>> =
        currentUserIdFlow.flatMapLatest { userId ->
            if (userId.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                taskDao
                    .getTasksByUserId(userId)
                    .onStart {
                        // Auto sync when tasks are first observed for this user (e.g., after login)
                        syncIfPossible(localUserId = userId)
                    }
                    .map { entities -> TaskMapper.toDomainList(entities) }
            }
        }

    /**
     * 手动同步入口点 / Manual sync entrypoint
     * 
     * TaskRepository 要求的手动同步入口点。
     * 调用与自动同步相同的增量拉取 + 尽力推送逻辑。
     * 
     * Manual sync entrypoint required by TaskRepository.
     * Calls the same incremental pull + best-effort push used by auto sync.
     * 
     * @return 同步结果 / Sync result
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

    /**
     * 保存任务列表 / Save task list
     * 
     * 保存任务列表到本地数据库，并尝试推送到远程（如果用户已绑定）。
     * 只更新新任务或实际发生变化的任务，避免为所有任务更新 updatedAt。
     * 
     * Saves task list to local database and attempts to push to remote (if user is bound).
     * Only updates new tasks or tasks that actually changed, to avoid bumping updatedAt for all tasks.
     * 
     * @param tasks 要保存的任务列表 / Task list to save
     */
    override suspend fun saveTasks(tasks: List<Task>) {
        withContext(Dispatchers.IO) {
            val userId = currentUserIdFlow.first()
            if (userId.isNullOrBlank()) return@withContext

            // Existing (non-deleted) tasks snapshot
            val existingTasks = taskDao.getTasksByUserId(userId).first()
            val existingTaskMap = existingTasks.associateBy { it.id }

            val now = System.currentTimeMillis()

            // Only upsert tasks that are new or actually changed, to avoid bumping updatedAt for everything
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
                        // No real change; keep DB row untouched
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

            // Push only changed rows (best-effort; will no-op if user not bound to remote)
            pushIfPossible(localUserId = userId, dirtyEntities = dirtyEntities)
        }
    }

    /**
     * 删除单个任务（软删除）/ Delete single task (soft delete)
     * 
     * 便捷包装函数。
     * 
     * Convenience wrapper.
     * 
     * @param id 任务 ID / Task ID
     */
    suspend fun deleteTask(id: String) {
        deleteTasks(listOf(id))
    }

    /**
     * 删除多个任务（软删除）/ Delete multiple tasks (soft delete)
     * 
     * 根据 ID 列表删除多个任务（软删除），如果可用则推送删除标记到远程。
     * 
     * Delete multiple tasks by ids (soft delete) and push tombstones to remote if available.
     * 
     * @param ids 任务 ID 列表 / List of task IDs
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
     * 更新任务的延期状态 / Update isPostponed status for specific tasks
     * 
     * 更新指定任务的延期状态，并确保其变为"脏"状态以便同步。
     * 
     * Update isPostponed status for specific tasks, and ensure it becomes "dirty" for sync.
     * 
     * @param ids 任务 ID 列表 / List of task IDs
     * @param isPostponed 是否延期 / Whether tasks are postponed
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
     * 获取已完成任务的统计信息 / Get completed task statistics
     * 
     * @param userId 用户 ID / User ID
     * @return 按时完成数量和延期完成数量的配对 / Pair of on-time completed count and postponed completed count
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

    /**
     * 如果可能则执行同步 / Sync if possible
     * 
     * 执行增量拉取和尽力推送同步。
     * 使用互斥锁确保同步操作的线程安全。
     * 
     * Performs incremental pull and best-effort push sync.
     * Uses mutex to ensure thread safety of sync operations.
     * 
     * @param localUserId 本地用户 ID / Local user ID
     */
    private suspend fun syncIfPossible(localUserId: String) {
        withContext(Dispatchers.IO) {
            syncMutex.withLock {
                val remoteUid = userDao.getUserById(localUserId)?.remoteId
                if (remoteUid.isNullOrBlank()) return@withLock

                // Pull (incremental)
                pullRemoteIncremental(localUserId = localUserId, remoteUid = remoteUid)

                // Push (best-effort): push current dirty tasks in DB snapshot
                val current = taskDao.getTasksByUserId(localUserId).first()
                val dirty = current.filter { it.syncStatus != TaskSyncStatus.SYNCED && !it.isDeleted }
                pushIfPossible(localUserId = localUserId, dirtyEntities = dirty)
            }
        }
    }

    /**
     * 从远程增量拉取任务 / Pull tasks incrementally from remote
     * 
     * 使用游标机制从 Firestore 增量拉取任务更新。
     * 只拉取 updatedAt 大于游标的任务，避免重复拉取。
     * 
     * Uses cursor mechanism to pull task updates incrementally from Firestore.
     * Only pulls tasks with updatedAt greater than cursor to avoid duplicate pulls.
     * 
     * @param localUserId 本地用户 ID / Local user ID
     * @param remoteUid 远程用户 ID / Remote user ID
     */
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

        // Compare only with current active tasks snapshot (good enough for v1)
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

        // Advance cursor
        context.settingsDataStore.edit { prefs ->
            prefs[cursorKey] = maxRemoteUpdatedAt
        }
    }

    /**
     * 如果可能则推送任务到远程 / Push tasks to remote if possible
     * 
     * 将脏任务推送到 Firestore，并在成功后标记为已同步。
     * 如果用户未绑定到远程，则不执行任何操作。
     * 
     * Pushes dirty tasks to Firestore and marks them as synced on success.
     * No-op if user is not bound to remote.
     * 
     * @param localUserId 本地用户 ID / Local user ID
     * @param dirtyEntities 需要推送的脏任务实体列表 / List of dirty task entities to push
     */
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

    /**
     * 如果可能则推送删除标记到远程 / Push delete tombstones to remote if possible
     * 
     * 将任务的删除标记推送到 Firestore。
     * 如果用户未绑定到远程，则不执行任何操作。
     * 
     * Pushes task delete tombstones to Firestore.
     * No-op if user is not bound to remote.
     * 
     * @param localUserId 本地用户 ID / Local user ID
     * @param taskIds 要删除的任务 ID 列表 / List of task IDs to delete
     * @param deletedAt 删除时间戳 / Deletion timestamp
     */
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
        // v1: don't force local tombstone syncStatus back to SYNCED (avoids additional DAO query for deleted rows)
    }

    // ---------------------------
    // Mapping helpers
    // ---------------------------

    /**
     * 检查任务是否有有意义的变更 / Check if task has meaningful changes
     * 
     * 比较两个任务实体，判断是否有实际业务意义的变更。
     * 
     * Compares two task entities to determine if there are meaningful business changes.
     * 
     * @param a 第一个任务实体 / First task entity
     * @param b 第二个任务实体 / Second task entity
     * @return 是否有有意义的变更 / Whether there are meaningful changes
     */
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

    /**
     * 将任务实体转换为 Firestore 文档 / Convert task entity to Firestore document
     * 
     * @param e 任务实体 / Task entity
     * @return Firestore 文档数据映射 / Firestore document data map
     */
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

    /**
     * 将 Firestore 文档转换为任务实体 / Convert Firestore document to task entity
     * 
     * @param docId 文档 ID / Document ID
     * @param localUserId 本地用户 ID / Local user ID
     * @param data 文档数据 / Document data
     * @return 任务实体，如果数据无效则返回 null / Task entity, returns null if data is invalid
     */
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
