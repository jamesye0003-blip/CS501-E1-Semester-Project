package com.example.lattice.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.lattice.domain.model.AuthState
import com.example.lattice.domain.model.User
import com.example.lattice.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth")
private val USER_ID_KEY = stringPreferencesKey("user_id")
private val USERNAME_KEY = stringPreferencesKey("username")
private val EMAIL_KEY = stringPreferencesKey("email")
private val TOKEN_KEY = stringPreferencesKey("token")

/**
 * DataStore 上的默认认证仓库实现。
 * 通过实现 domain.repository.AuthRepository，使 ViewModel 只依赖接口。
 */
class DefaultAuthRepository(private val context: Context) : AuthRepository {

    override val authState: Flow<AuthState> = context.authDataStore.data.map { prefs ->
        val userId = prefs[USER_ID_KEY]
        val username = prefs[USERNAME_KEY]
        val token = prefs[TOKEN_KEY]

        if (token != null && userId != null && username != null) {
            AuthState(
                isAuthenticated = true,
                user = User(
                    id = userId,
                    username = username,
                    email = prefs[EMAIL_KEY]
                )
            )
        } else {
            AuthState(isAuthenticated = false)
        }
    }

    override suspend fun login(username: String, password: String): Result<User> {
        return try {
            // 简单的本地验证（实际项目中应该调用 API）
            if (username.isNotBlank() && password.isNotBlank()) {
                val user = User(
                    id = "user_${System.currentTimeMillis()}",
                    username = username,
                    email = null
                )

                // 保存认证信息
                context.authDataStore.edit { prefs ->
                    prefs[USER_ID_KEY] = user.id
                    prefs[USERNAME_KEY] = user.username
                    prefs[TOKEN_KEY] = "token_${user.id}"
                    user.email?.let { prefs[EMAIL_KEY] = it }
                }

                Result.success(user)
            } else {
                Result.failure(Exception("Username and password are required"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        context.authDataStore.edit { prefs ->
            prefs.remove(USER_ID_KEY)
            prefs.remove(USERNAME_KEY)
            prefs.remove(EMAIL_KEY)
            prefs.remove(TOKEN_KEY)
        }
    }
}
