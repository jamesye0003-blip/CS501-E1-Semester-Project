package com.example.lattice.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.lattice.data.local.datastore.authDataStore
import com.example.lattice.data.local.room.db.AppDatabase
import com.example.lattice.data.local.room.entity.UserEntity
import com.example.lattice.domain.model.AuthState
import com.example.lattice.domain.model.User
import com.example.lattice.domain.repository.AuthRepository
import com.google.android.gms.tasks.Task as GmsTask
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import java.security.MessageDigest
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private val USER_ID_KEY = stringPreferencesKey("user_id")

/**
 * 本地认证（Room + DataStore）+ Firebase 绑定，仅使用用户名/密码
 * 
 * 注意：Firebase Email/Password provider 必须使用 email。
 * 我们对外只暴露 username/password，并在内部把 username 映射为"内部 email"：
 * 用户不需要看到/输入 email。
 * 
 * Local auth (Room + DataStore) + Firebase binding using username/password only
 * 
 * Note: Firebase Email/Password provider must use email.
 * We only expose username/password externally, and internally map username to "internal email":
 *   internalEmail = sanitize(username) + "@lattice.local"
 * Users don't need to see/input email.
 */
class DefaultAuthRepository(private val context: Context) : AuthRepository {

    private val database = AppDatabase.getDatabase(context)
    private val userDao = database.userDao()

    // Use FirebaseAuth instance directly instead of extension to avoid "auth" unresolved
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

    /**
     * 认证状态流 / Authentication state flow
     * 
     * 观察认证状态变化，当用户 ID 变化时自动切换观察的用户数据。
     * 如果用户 ID 为空，返回未认证状态；否则观察对应用户的实体数据。
     * 
     * Observes authentication state changes, automatically switches observed user data when user ID changes.
     * Returns unauthenticated state if user ID is blank; otherwise observes corresponding user entity data.
     */
    @kotlinx.coroutines.ExperimentalCoroutinesApi
    override val authState: Flow<AuthState> =
        context.authDataStore.data.flatMapLatest { prefs ->
            val userId = prefs[USER_ID_KEY]
            if (userId.isNullOrBlank()) {
                flowOf(AuthState(isAuthenticated = false))
            } else {
                userDao.observeUserById(userId).map { entity ->
                    if (entity != null && !entity.isDeleted) {
                        AuthState(
                            isAuthenticated = true,
                            user = User(
                                id = entity.id,
                                username = entity.username,
                                email = entity.email
                            )
                        )
                    } else {
                        AuthState(isAuthenticated = false)
                    }
                }
            }
        }

    /**
     * 注册新用户 / Register new user
     * 
     * 创建本地用户和 Firebase 用户，并将它们绑定。
     * 对外只暴露 username/password，内部将 username 映射为内部 email。
     * 
     * Creates local user and Firebase user, and binds them together.
     * Only exposes username/password externally, internally maps username to internal email.
     * 
     * @param username 用户名 / Username
     * @param password 密码 / Password
     * @return 注册结果，成功时包含用户信息 / Registration result, contains user info on success
     */
    override suspend fun register(username: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(username.isNotBlank()) { "Username must not be empty" }
                require(password.isNotBlank()) { "Password must not be empty" }

                val existing = userDao.getUserByUsername(username)
                require(existing == null) { "Username already exists" }

                val now = System.currentTimeMillis()
                val internalEmail = toInternalEmail(username)

                // Create Firebase user (username -> internalEmail)
                val authResult = firebaseAuth
                    .createUserWithEmailAndPassword(internalEmail, password)
                    .await()

                val uid = authResult.user?.uid ?: error("Firebase uid is null")

                // Create local user, bind remoteId = Firebase uid
                val localUserId = UUID.randomUUID().toString()
                val entity = UserEntity(
                    id = localUserId,
                    username = username,
                    passwordHash = hashPassword(password),
                    email = null,
                    isDeleted = false,
                    remoteId = uid,
                    createdAt = now,
                    updatedAt = now,
                    lastSyncedAt = now
                )
                userDao.insertUser(entity)

                // Save session
                context.authDataStore.edit { prefs -> prefs[USER_ID_KEY] = localUserId }

                User(id = entity.id, username = entity.username, email = entity.email)
            }
        }

    /**
     * 用户登录 / User login
     * 
     * 支持离线登录和在线登录。
     * 先尝试本地验证（支持离线和快速登录），如果本地验证失败，再尝试远程 Firebase 验证。
     * 适用于首次在本设备登录，或本地数据被清空后的重新登录。
     * 
     * Supports offline and online login.
     * First attempts local verification (supports offline and fast login), if local verification fails, then attempts remote Firebase verification.
     * Suitable for first-time login on this device, or re-login after local data is cleared.
     * 
     * @param username 用户名 / Username
     * @param password 密码 / Password
     * @return 登录结果，成功时包含用户信息 / Login result, contains user info on success
     */
    override suspend fun login(username: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(username.isNotBlank()) { "Username must not be empty" }
                require(password.isNotBlank()) { "Password must not be empty" }

                val now = System.currentTimeMillis()
                val internalEmail = toInternalEmail(username)

                val local = userDao.getUserByUsername(username)
                val localPasswordOk =
                    local != null && !local.isDeleted && verifyPassword(password, local.passwordHash)

                // Try local verification first (supports offline & fast login)
                if (localPasswordOk && local != null) {
                    // Local user exists and password matches, login directly
                    context.authDataStore.edit { prefs -> prefs[USER_ID_KEY] = local.id }
                    return@runCatching User(id = local.id, username = local.username, email = local.email)
                }

                // Local user doesn't exist or password doesn't match, try remote Firebase verification
                // Suitable for: first-time login on this device, or re-login after local data is cleared
                val uid = try {
                    val result = firebaseAuth
                        .signInWithEmailAndPassword(internalEmail, password)
                        .await()
                    result.user?.uid ?: error("Firebase uid is null")
                } catch (e: FirebaseAuthInvalidUserException) {
                    // Remote account doesn't exist: if local has no valid user, throw error directly
                    throw e
                } catch (e: FirebaseAuthInvalidCredentialsException) {
                    // Remote password error, and local also failed (because localPasswordOk was already false)
                    throw e
                }

                // Remote verification succeeded: create or update user locally, and bind remoteId = Firebase uid
                val finalLocal = if (local == null) {
                    val localUserId = UUID.randomUUID().toString()
                    val entity = UserEntity(
                        id = localUserId,
                        username = username,
                        passwordHash = hashPassword(password),
                        email = null,
                        isDeleted = false,
                        remoteId = uid,
                        createdAt = now,
                        updatedAt = now,
                        lastSyncedAt = now
                    )
                    userDao.insertUser(entity)
                    entity
                } else {
                    val updated = local.copy(
                        passwordHash = hashPassword(password),
                        remoteId = uid,
                        updatedAt = now,
                        lastSyncedAt = now
                    )
                    userDao.insertUser(updated)
                    updated
                }

                // Save session
                context.authDataStore.edit { prefs -> prefs[USER_ID_KEY] = finalLocal.id }

                User(id = finalLocal.id, username = finalLocal.username, email = finalLocal.email)
            }
        }

    /**
     * 用户登出 / User logout
     * 
     * 登出 Firebase 并清除本地会话。
     * 
     * Signs out from Firebase and clears local session.
     */
    override suspend fun logout() {
        runCatching { firebaseAuth.signOut() }
        context.authDataStore.edit { prefs -> prefs.remove(USER_ID_KEY) }
    }

    /**
     * 将用户名转换为内部 email / Convert username to internal email
     * 
     * 将用户名清理并转换为符合 Firebase Email/Password provider 要求的内部 email 格式。
     * 格式：sanitized_username@lattice.local
     * 
     * Sanitizes username and converts it to internal email format required by Firebase Email/Password provider.
     * Format: sanitized_username@lattice.local
     * 
     * @param username 用户名 / Username
     * @return 内部 email 地址 / Internal email address
     */
    private fun toInternalEmail(username: String): String {
        val raw = username.trim().lowercase()
        val sanitized = raw
            .replace(Regex("\\s+"), "_")
            .replace(Regex("[^a-z0-9._-]"), "_")
            .trim('_', '.', '-')

        val localPart = when {
            sanitized.isNotBlank() -> sanitized.take(60)
            else -> "u_" + sha256Hex(raw).take(24)
        }

        return "$localPart@lattice.local"
    }
}

/**
 * Firebase Task await 辅助函数 / Firebase Task await helper
 * 
 * 将 Firebase Task 转换为协程挂起函数，无需额外协程依赖。
 * 
 * Converts Firebase Task to suspend function, no additional coroutine dependency required.
 */
private suspend fun <T> GmsTask<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result)
            else cont.resumeWithException(task.exception ?: Exception("Unknown Firebase Task exception"))
        }
    }

/**
 * 密码哈希辅助函数（用于本地离线认证）/ Password hash helpers (for local offline auth)
 * 
 * 使用 SHA-256 对密码进行哈希处理，支持本地离线认证。
 * 
 * Uses SHA-256 to hash passwords, supports local offline authentication.
 */

/**
 * 对密码进行哈希处理 / Hash password
 * 
 * @param plain 明文密码 / Plain password
 * @return 哈希后的密码 / Hashed password
 */
private fun hashPassword(plain: String): String = sha256Hex(plain)

/**
 * 验证密码 / Verify password
 * 
 * @param plain 明文密码 / Plain password
 * @param hash 哈希值 / Hash value
 * @return 是否匹配 / Whether password matches
 */
private fun verifyPassword(plain: String, hash: String): Boolean =
    hashPassword(plain) == hash

/**
 * SHA-256 哈希并返回十六进制字符串 / SHA-256 hash and return hex string
 * 
 * @param input 输入字符串 / Input string
 * @return 十六进制哈希值 / Hex hash value
 */
private fun sha256Hex(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
