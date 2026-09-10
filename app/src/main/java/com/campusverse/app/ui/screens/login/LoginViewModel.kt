package com.campusverse.app.ui.screens.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.repository.AuthRepositoryImpl
import com.campusverse.app.domain.auth.AuthException
import com.campusverse.app.domain.auth.AuthRepository
import com.campusverse.app.domain.auth.AuthenticatedUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.Pattern

data class LoginUiState(
    val email: String = "",
    val emailError: String? = null,
    val password: String = "",
    val passwordError: String? = null,
    val generalError: String? = null,
    val isLoading: Boolean = false
) {
    val isFormValid: Boolean
        get() = email.isNotBlank() && password.isNotBlank() && emailError == null && passwordError == null
}

class LoginViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val emailPattern = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$"
    )

    fun onEmailChange(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                emailError = null,
                generalError = null
            )
        }
    }

    fun onPasswordChange(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                passwordError = null,
                generalError = null
            )
        }
    }

    fun login(onSuccess: (AuthenticatedUser) -> Unit) {
        // Prevent duplicate submissions
        if (_uiState.value.isLoading) return

        val email = _uiState.value.email.trim()
        val password = _uiState.value.password

        var hasError = false
        var emailErr: String? = null
        var passwordErr: String? = null

        if (email.isBlank()) {
            emailErr = "Email cannot be empty"
            hasError = true
        } else if (!emailPattern.matcher(email).matches()) {
            emailErr = "Please enter a valid email address"
            hasError = true
        }

        if (password.isBlank()) {
            passwordErr = "Password cannot be empty"
            hasError = true
        }

        if (hasError) {
            _uiState.update {
                it.copy(
                    emailError = emailErr,
                    passwordError = passwordErr
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, generalError = null) }

        viewModelScope.launch {
            val result = authRepository.login(email, password)
            result.fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess(user)
                },
                onFailure = { throwable ->
                    val errorMsg = when (throwable) {
                        is AuthException -> throwable.message
                        else -> throwable.message ?: "Authentication failed. Please check your credentials."
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generalError = errorMsg
                        )
                    }
                }
            )
        }
    }

    fun clearGeneralError() {
        _uiState.update { it.copy(generalError = null) }
    }
}
