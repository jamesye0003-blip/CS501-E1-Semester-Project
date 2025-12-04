package com.example.lattice.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lattice.data.DefaultAuthRepository
import com.example.lattice.domain.model.AuthState
import com.example.lattice.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AuthViewModel(app: Application) : AndroidViewModel(app) {

    // 通过 domain 层接口持有仓库，实现仍然是 data 层的 DefaultAuthRepository
    private val repo: AuthRepository = DefaultAuthRepository(app)

    private val _uiState = MutableStateFlow(AuthState(isLoading = true))
    val uiState: StateFlow<AuthState> = _uiState.asStateFlow()

    init {
        // 订阅认证状态
        viewModelScope.launch {
            repo.authState.collectLatest { state ->
                _uiState.value = state.copy(isLoading = false)
            }
        }
    }

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            repo.login(username, password)
                .onSuccess {
                    // 状态会通过 authState Flow 自动更新
                }
                .onFailure { exception ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = exception.message ?: "Login failed"
                    )
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repo.logout()
            // 状态会通过 authState Flow 自动更新
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
