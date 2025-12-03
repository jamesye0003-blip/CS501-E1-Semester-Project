package com.example.lattice.domain.repository

import com.example.lattice.domain.model.AuthState
import com.example.lattice.domain.model.User
import kotlinx.coroutines.flow.Flow

/**
 * 领域层认证仓库契约。
 * UI / ViewModel 只依赖这个接口，而不关心具体是 DataStore、网络还是别的实现。
 */
interface AuthRepository {

    /** 持续推送当前认证状态（已登录/未登录 + 用户信息等） */
    val authState: Flow<AuthState>

    /** 尝试登录，成功返回 User，失败返回 Result.failure。 */
    suspend fun login(username: String, password: String): Result<User>

    /** 注销登录并清空本地凭证。 */
    suspend fun logout()
}

