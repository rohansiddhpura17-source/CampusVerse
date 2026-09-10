package com.campusverse.app.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.UserRole
import com.campusverse.app.data.repository.AuthRepositoryImpl
import com.campusverse.app.domain.auth.AuthRepository
import com.campusverse.app.domain.auth.AuthenticatedUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminLoginUiState {
    data class Form(
        val email: String = "admin@campusverse.edu",
        val password: String = "Password123",
        val isLoading: Boolean = false,
        val errorMessage: String? = null
    ) : AdminLoginUiState
    data class Success(val user: AuthenticatedUser) : AdminLoginUiState
}

class AdminLoginViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminLoginUiState>(AdminLoginUiState.Form())
    val uiState: StateFlow<AdminLoginUiState> = _uiState.asStateFlow()

    fun onEmailChanged(email: String) {
        val current = _uiState.value
        if (current is AdminLoginUiState.Form) {
            _uiState.value = current.copy(email = email, errorMessage = null)
        }
    }

    fun onPasswordChanged(password: String) {
        val current = _uiState.value
        if (current is AdminLoginUiState.Form) {
            _uiState.value = current.copy(password = password, errorMessage = null)
        }
    }

    fun login() {
        val current = _uiState.value
        if (current !is AdminLoginUiState.Form) return

        if (current.email.isBlank() || current.password.isBlank()) {
            _uiState.value = current.copy(errorMessage = "Please provide both admin email and password.")
            return
        }

        viewModelScope.launch {
            _uiState.value = current.copy(isLoading = true, errorMessage = null)
            authRepository.login(current.email.trim(), current.password)
                .onSuccess { user ->
                    if (user.role == UserRole.ADMIN && user.isAdminAuthorized) {
                        _uiState.value = AdminLoginUiState.Success(user)
                    } else {
                        _uiState.value = current.copy(
                            isLoading = false,
                            errorMessage = "Access Denied: Account '${user.email}' is not an authorized administrator."
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.value = current.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Invalid admin credentials or server unreachable."
                    )
                }
        }
    }
}
