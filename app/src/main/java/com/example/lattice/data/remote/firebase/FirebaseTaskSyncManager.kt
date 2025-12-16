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

class FirebaseTaskSyncManager(
    private val taskDao: TaskDao,
    private val userDao: UserDao,
    private val cursorStore: SyncCursorStore,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    private companion object {
        private const val USERS = "users"
        private const val TASKS = "tasks"

        // 时钟漂移安全窗口（与你文档一致思路）
        private const val SKEW_WINDOW_MS = 2 * 60 * 1000L

        // Firestore batch 限制 500，留余量
        private const val BATCH_LIMIT = 450
    }

    suspend fun sync(localUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val user = userDao.getUserById(localUserId)
                ?: error("Local user not found: $localUserId")

            val remoteUid = user.remoteId ?: error("User is not bound to remoteId (Firebase uid)")

            // 1) PULL
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

                // 0.5：远端 tombstone、但本地没有 -> 忽略
                if (local == null && remoteEntity.isDeleted) continue

                if (local == null) {
                    // 0.6：本地没有且远端未删除 -> 直接落库（SYNCED）
                    taskDao.insertTask(remoteEntity)
                    continue
                }

                // 冲突：本地 dirty + 远端在 lastSyncedAt 之后有变化
                val localDirty = local.syncStatus != TaskSyncStatus.SYNCED
                val remoteChangedSinceLastSync = when (val ls = local.lastSyncedAt) {
                    null -> true
                    else -> remoteUpdatedAt > ls
                }

                val applyRemote =
                    if (!localDirty) {
                        // 本地干净：谁 updatedAt 大谁赢
                        remoteUpdatedAt > local.updatedAt
                    } else {
                        // 本地脏：如果远端没变，就不覆盖本地
                        if (!remoteChangedSinceLastSync) false
                        else remoteUpdatedAt >= local.updatedAt
                    }

                if (applyRemote) {
                    taskDao.insertTask(
                        remoteEntity.copy(
                            // 重要：remote 覆盖时，syncStatus 归 SYNCED，lastSyncedAt 对齐 remoteUpdatedAt
                            syncStatus = TaskSyncStatus.SYNCED,
                            lastSyncedAt = remoteUpdatedAt
                        )
                    )
                }
            }

            if (maxRemoteUpdatedAt > cursor) {
                cursorStore.setCursor(localUserId, maxRemoteUpdatedAt)
            }

            // 2) PUSH
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

                    // 本地批量标记 SYNCED（lastSyncedAt 用 now）
                    taskDao.markTasksSynced(chunk.map { it.id }, now)
                }
            }
        }
    }

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

        // attachments：为了不破坏你当前 Room 结构，这里保留字段但允许空（如果你希望同步附件元数据，再细化）
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

    private fun entityToRemoteMap(t: TaskEntity, now: Long): Map<String, Any?> {
        // 只要 push，就刷新 updatedAt（用于增量 cursor）
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

        // CREATED：补齐 createdAt（远端只写一次也行，但这里写入不会有害）
        if (t.syncStatus == TaskSyncStatus.CREATED) {
            base["createdAt"] = t.createdAt
        }

        return base
    }
}
