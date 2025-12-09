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
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
private val USER_ID_KEY = stringPreferencesKey("user_id")

/**
 * 使用 Room + DataStore 的本地多账号认证仓库实现。
 * - Room 保存用户账号和密码哈希
 *
 * Local multi-account auth repository using Room + DataStore.
 *   Room stores user accounts and password hashes.
 *   DataStore keeps only current_user_id.
 */
class DefaultAuthRepository(private val context: Context) : AuthRepository {

    private val database = AppDatabase.getDatabase(context)
    private val userDao = database.userDao()

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
            if (username.isBlank() || password.isBlank()) {
                return@withContext Result.failure(Exception("Username and password must not be empty"))
            }

            val existing = userDao.getUserByUsername(username)
            if (existing != null) {
                return@withContext Result.failure(Exception("Username already exists"))
            }

            val now = System.currentTimeMillis()
            val userId = UUID.randomUUID().toString()
            val userEntity = UserEntity(
                id = userId,
                username = username,
                passwordHash = hashPassword(password),
                email = null,
                createdAt = now,
                updatedAt = now
            )
            userDao.insertUser(userEntity)
            context.authDataStore.edit { prefs -> prefs[USER_ID_KEY] = userId }

            Result.success(
                User(
                    id = userEntity.id,
                    username = userEntity.username,
                    email = userEntity.email
                )
            )
        }

    override suspend fun login(username: String, password: String): Result<User> =
        withContext(Dispatchers.IO) {
            if (username.isBlank() || password.isBlank()) {
                return@withContext Result.failure(Exception("Username and password must not be empty"))
            }

            val user = userDao.getUserByUsername(username)
                ?: return@withContext Result.failure(Exception("User not found"))

            if (user.isDeleted) {
                return@withContext Result.failure(Exception("User is deleted"))
            }

            if (!verifyPassword(password, user.passwordHash)) {
                return@withContext Result.failure(Exception("Invalid password"))
            }

            context.authDataStore.edit { prefs -> prefs[USER_ID_KEY] = user.id }

            Result.success(
                User(
                    id = user.id,
                    username = user.username,
                    email = user.email
                )
            )
        }

    override suspend fun logout() {
        context.authDataStore.edit { prefs ->
            prefs.remove(USER_ID_KEY)
        }
    }
}

// ---- Simple password hash helpers ----
private fun hashPassword(plain: String): String {
    val md = MessageDigest.getInstance("SHA-256")
    val bytes = md.digest(plain.toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun verifyPassword(plain: String, hash: String): Boolean =
    hashPassword(plain) == hash

