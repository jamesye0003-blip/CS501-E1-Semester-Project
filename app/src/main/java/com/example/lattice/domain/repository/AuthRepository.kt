package com.example.lattice.domain.repository

import com.example.lattice.domain.model.AuthState
import com.example.lattice.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * 领域层认证仓库契约。
 * Domain contract for authentication repository. UI/ViewModel depends on interface only.
 */
interface AuthRepository {

    /** Emits current auth state (logged-in/out + user info). */
    val authState: Flow<AuthState>

    /** Register new user and sign in immediately. */
    suspend fun register(username: String, password: String): Result<User>

    /** Login with username/password; success returns User, failure returns Result.failure. */
    suspend fun login(username: String, password: String): Result<User>

    /** Logout and clear local credentials. */
    suspend fun logout()
}

