package com.example.lattice.data.remote.firebase

import com.example.lattice.data.local.datastore.SyncCursorStore
import com.example.lattice.data.local.room.dao.TaskDao
import com.example.lattice.data.local.room.dao.UserDao
import com.example.lattice.data.local.room.entity.TaskEntity
import com.example.lattice.data.local.room.entity.TaskSyncStatus
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import kotlin.math.max

/**
 * Firebase 任务同步管理器 / Firebase task sync manager
 * 
 * 负责本地任务与 Firestore 之间的双向同步。
 * 使用增量拉取（基于游标）和批量推送机制。
 * 
 * Manages bidirectional sync between local tasks and Firestore.
 * Uses incremental pull (cursor-based) and batch push mechanism.
 * 
 * @param taskDao 任务数据访问对象 / Task data access object
 * @param userDao 用户数据访问对象 / User data access object
 * @param cursorStore 同步游标存储 / Sync cursor store
 * @param firestore Firestore 实例，默认使用单例 / Firestore instance, defaults to singleton
 */
class FirebaseTaskSyncManager(
    private val taskDao: TaskDao,
    private val userDao: UserDao,
    private val cursorStore: SyncCursorStore,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private companion object {
        private const val USERS = "users"
        private const val TASKS = "tasks"

        // Clock skew safety window
        private const val SKEW_WINDOW_MS = 2 * 60 * 1000L

        // Firestore batch limit is 500, leave margin
        private const val BATCH_LIMIT = 450
    }

    /**
     * 执行同步操作 / Perform sync operation
     * 
     * 执行增量拉取和批量推送同步。
     * 先拉取远程更新（基于游标），然后推送本地脏任务到远程。
     * 
     * Performs incremental pull and batch push sync.
     * First pulls remote updates (cursor-based), then pushes local dirty tasks to remote.
     * 
     * @param localUserId 本地用户 ID / Local user ID
     * @return 同步结果 / Sync result
     */
    suspend fun sync(localUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = userDao.getUserById(localUserId)
                ?: error("Local user not found: $localUserId")

            val remoteUid = user.remoteId ?: error("User is not bound to remoteId (Firebase uid)")

            // 1) Pull: incremental sync from remote
            val cursor = cursorStore.getCursor(localUserId)
            val safeCursor = max(0L, cursor - SKEW_WINDOW_MS)

            val query = firestore.collection(USERS)
                .document(remoteUid)
                .collection(TASKS)
                .whereGreaterThan("updatedAt", safeCursor)
                .orderBy("updatedAt")

            val snapshot = query.get().await()

            var maxRemoteUpdatedAt = cursor

            for (doc in snapshot.documents) {
                val remote = doc.data ?: continue
                val remoteId = doc.id
                val remoteUpdatedAt = (remote["updatedAt"] as? Number)?.toLong() ?: 0L
                val remoteCreatedAt = (remote["createdAt"] as? Number)?.toLong()

                maxRemoteUpdatedAt = max(maxRemoteUpdatedAt, remoteUpdatedAt)

                val remoteEntity = remoteToEntity(
                    localUserId = localUserId,
                    id = remoteId,
                    createdAt = remoteCreatedAt,
                    updatedAt = remoteUpdatedAt,
                    map = remote
                ) ?: continue

                val local = taskDao.getTaskByIdAny(remoteId)

                // Remote tombstone but local doesn't exist -> ignore
                if (local == null && remoteEntity.isDeleted) continue

                if (local == null) {
                    // Local doesn't exist and remote not deleted -> insert directly (SYNCED)
                    taskDao.insertTask(remoteEntity)
                    continue
                }

                // Conflict: local dirty + remote changed after lastSyncedAt
                val localDirty = local.syncStatus != TaskSyncStatus.SYNCED
                val remoteChangedSinceLastSync = when (val ls = local.lastSyncedAt) {
                    null -> true
                    else -> remoteUpdatedAt > ls
                }

                val applyRemote =
                    if (!localDirty) {
                        // If local is clean: winner is the one with larger updatedAt
                        remoteUpdatedAt > local.updatedAt
                    } else {
                        // If local is dirty: don't overwrite if remote unchanged
                        if (!remoteChangedSinceLastSync) false
                        else remoteUpdatedAt >= local.updatedAt
                    }

                if (applyRemote) {
                    taskDao.insertTask(
                        remoteEntity.copy(
                            // IMPORTANT: when remote overwrites, syncStatus becomes SYNCED, lastSyncedAt aligns with remoteUpdatedAt
                            syncStatus = TaskSyncStatus.SYNCED,
                            lastSyncedAt = remoteUpdatedAt
                        )
                    )
                }
            }

            if (maxRemoteUpdatedAt > cursor) {
                cursorStore.setCursor(localUserId, maxRemoteUpdatedAt)
            }

            // 2) Push: batch push local dirty tasks to remote
            val dirty = taskDao.getDirtyTasksByUserId(localUserId)
            if (dirty.isNotEmpty()) {
                val now = System.currentTimeMillis()

                dirty.chunked(BATCH_LIMIT).forEach { chunk ->
                    val batch = firestore.batch()
                    for (t in chunk) {
                        val ref = firestore.collection(USERS)
                            .document(remoteUid)
                            .collection(TASKS)
                            .document(t.id)

                        val data = entityToRemoteMap(t, now)
                        batch.set(ref, data)
                    }
                    batch.commit().await()

                    // Mark tasks as SYNCED locally (use now for lastSyncedAt)
                    taskDao.markTasksSynced(chunk.map { it.id }, now)
                }
            }
        }
    }

    /**
     * 将远程 Firestore 文档转换为任务实体 / Convert remote Firestore document to task entity
     * 
     * @param localUserId 本地用户 ID / Local user ID
     * @param id 任务 ID / Task ID
     * @param createdAt 创建时间戳 / Creation timestamp
     * @param updatedAt 更新时间戳 / Update timestamp
     * @param map 文档数据映射 / Document data map
     * @return 任务实体，如果数据无效则返回 null / Task entity, returns null if data is invalid
     */
    private fun remoteToEntity(
        localUserId: String,
        id: String,
        createdAt: Long?,
        updatedAt: Long,
        map: Map<String, Any?>
    ): TaskEntity? {
        val title = map["title"] as? String ?: return null
        val description = map["description"] as? String ?: ""
        val priority = map["priority"] as? String ?: "None"

        val dueAt = (map["dueAt"] as? Number)?.toLong()
        val hasSpecificTime = map["hasSpecificTime"] as? Boolean ?: false
        val sourceTimeZoneId = map["sourceTimeZoneId"] as? String

        val isDone = map["isDone"] as? Boolean ?: false
        val isPostponed = map["isPostponed"] as? Boolean ?: false
        val isCancelled = map["isCancelled"] as? Boolean ?: false

        val parentId = map["parentId"] as? String
        val isDeleted = map["isDeleted"] as? Boolean ?: false

        // Attachments: keep field but allow empty to avoid breaking current Room structure
        // If you want to sync attachment metadata, refine this later
        @Suppress("UNCHECKED_CAST")
        val attachments = emptyList<com.example.lattice.domain.model.Attachment>()

        return TaskEntity(
            id = id,
            userId = localUserId,
            remoteId = id,
            title = title,
            description = description,
            priority = priority,
            dueAt = dueAt,
            hasSpecificTime = hasSpecificTime,
            sourceTimeZoneId = sourceTimeZoneId,
            isDone = isDone,
            isPostponed = isPostponed,
            isCancelled = isCancelled,
            parentId = parentId,
            isDeleted = isDeleted,

            syncStatus = TaskSyncStatus.SYNCED,
            lastSyncedAt = updatedAt,

            createdAt = createdAt ?: updatedAt,
            updatedAt = updatedAt,

            attachments = attachments
        )
    }

    /**
     * 将任务实体转换为远程 Firestore 文档映射 / Convert task entity to remote Firestore document map
     * 
     * @param t 任务实体 / Task entity
     * @param now 当前时间戳，用于更新 updatedAt / Current timestamp for updating updatedAt
     * @return Firestore 文档数据映射 / Firestore document data map
     */
    private fun entityToRemoteMap(t: TaskEntity, now: Long): Map<String, Any?> {
        // Refresh updatedAt whenever pushing (for incremental cursor)
        val base = mutableMapOf<String, Any?>(
            "title" to t.title,
            "description" to t.description,
            "priority" to t.priority,

            "dueAt" to t.dueAt,
            "hasSpecificTime" to t.hasSpecificTime,
            "sourceTimeZoneId" to t.sourceTimeZoneId,

            "isDone" to t.isDone,
            "isPostponed" to t.isPostponed,
            "isCancelled" to t.isCancelled,

            "parentId" to t.parentId,
            "isDeleted" to t.isDeleted,

            "updatedAt" to now
        )

        // CREATED: supplement createdAt (remote can write once, but writing here won't harm)
        if (t.syncStatus == TaskSyncStatus.CREATED) {
            base["createdAt"] = t.createdAt
        }

        return base
    }
}
