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
 * Local auth (Room + DataStore) + Firebase binding using username/password only.
 *
 * 注意：Firebase Email/Password provider 必须使用 email。
 * 我们对外只暴露 username/password，并在内部把 username 映射为“内部 email”：
 *   internalEmail = sanitize(username) + "@lattice.local"
 * 用户不需要看到/输入 email。
 */
class DefaultAuthRepository(private val context: Context) : AuthRepository {

    private val database = AppDatabase.getDatabase(context)
    private val userDao = database.userDao()

    // 不用 Firebase.auth 扩展，直接用实例，避免 “auth” unresolved
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

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

    override suspend fun register(username: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(username.isNotBlank()) { "Username must not be empty" }
                require(password.isNotBlank()) { "Password must not be empty" }

                val existing = userDao.getUserByUsername(username)
                require(existing == null) { "Username already exists" }

                val now = System.currentTimeMillis()
                val internalEmail = toInternalEmail(username)

                // 1) Create Firebase user (username -> internalEmail)
                val authResult = firebaseAuth
                    .createUserWithEmailAndPassword(internalEmail, password)
                    .await()

                val uid = authResult.user?.uid ?: error("Firebase uid is null")

                // 2) Create local user, bind remoteId = Firebase uid
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

                // 3) Save session
                context.authDataStore.edit { prefs -> prefs[USER_ID_KEY] = localUserId }

                User(id = entity.id, username = entity.username, email = entity.email)
            }
        }

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

                // 1) Try Firebase sign-in first (cross-device)
                val uid = try {
                    val result = firebaseAuth
                        .signInWithEmailAndPassword(internalEmail, password)
                        .await()
                    result.user?.uid ?: error("Firebase uid is null")
                } catch (e: FirebaseAuthInvalidUserException) {
                    // Firebase user not found:
                    // if local user exists & password matches, migrate by creating Firebase account.
                    if (localPasswordOk) {
                        val created = firebaseAuth
                            .createUserWithEmailAndPassword(internalEmail, password)
                            .await()
                        created.user?.uid ?: error("Firebase uid is null")
                    } else {
                        throw e
                    }
                } catch (e: FirebaseAuthInvalidCredentialsException) {
                    // Wrong password (Firebase). If local matches, allow offline login.
                    if (localPasswordOk) {
                        context.authDataStore.edit { prefs -> prefs[USER_ID_KEY] = local!!.id }
                        return@runCatching User(id = local.id, username = local.username, email = local.email)
                    } else {
                        throw e
                    }
                }

                // 2) Upsert local user, bind remoteId=uid
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

                // 3) Save session
                context.authDataStore.edit { prefs -> prefs[USER_ID_KEY] = finalLocal.id }

                User(id = finalLocal.id, username = finalLocal.username, email = finalLocal.email)
            }
        }

    override suspend fun logout() {
        runCatching { firebaseAuth.signOut() }
        context.authDataStore.edit { prefs -> prefs.remove(USER_ID_KEY) }
    }

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

// -------- Firebase Task await helper (无需额外协程依赖) --------
private suspend fun <T> GmsTask<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result)
            else cont.resumeWithException(task.exception ?: Exception("Unknown Firebase Task exception"))
        }
    }

// -------- Password hash helpers (local offline auth) --------
private fun hashPassword(plain: String): String = sha256Hex(plain)

private fun verifyPassword(plain: String, hash: String): Boolean =
    hashPassword(plain) == hash

private fun sha256Hex(input: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(input.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}
