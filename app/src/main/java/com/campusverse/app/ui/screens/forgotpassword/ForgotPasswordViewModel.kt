package com.campusverse.app.ui.screens.forgotpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.repository.AuthRepositoryImpl
import com.campusverse.app.domain.auth.AuthException
import com.campusverse.app.domain.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.regex.Pattern

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: String? = null,
    val token: String = "",
    val tokenError: String? = null,
    val newPassword: String = "",
    val newPasswordError: String? = null,
    val confirmPassword: String = "",
    val confirmPasswordError: String? = null,
    val generalError: String? = null,
    val successMessage: String? = null,
    val isLoading: Boolean = false,
    val isResetRequested: Boolean = false
)

class ForgotPasswordViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val emailPattern = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$"
    )

    fun onEmailChange(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                emailError = null,
                generalError = null,
                successMessage = null
            )
        }
    }

    fun onTokenChange(token: String) {
        _uiState.update {
            it.copy(
                token = token,
                tokenError = null,
                generalError = null
            )
        }
    }

    fun onNewPasswordChange(password: String) {
        _uiState.update {
            it.copy(
                newPassword = password,
                newPasswordError = null,
                generalError = null
            )
        }
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update {
            it.copy(
                confirmPassword = confirmPassword,
                confirmPasswordError = null,
                generalError = null
            )
        }
    }

    fun requestPasswordReset(onSuccess: () -> Unit) {
        if (_uiState.value.isLoading) return

        val email = _uiState.value.email.trim()

        if (email.isBlank()) {
            _uiState.update { it.copy(emailError = "Email is required") }
            return
        }

        if (!emailPattern.matcher(email).matches()) {
            _uiState.update { it.copy(emailError = "Please enter a valid email address") }
            return
        }

        _uiState.update { it.copy(isLoading = true, generalError = null) }

        viewModelScope.launch {
            val result = authRepository.requestPasswordReset(email)
            result.fold(
                onSuccess = {
                    val devOtp = authRepository.getActiveOtpForDisplay(email)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isResetRequested = true,
                            token = devOtp ?: "",
                            successMessage = if (devOtp != null) "Reset Code: $devOtp" else "Password reset instructions sent to $email"
                        )
                    }
                    onSuccess()
                },
                onFailure = { throwable ->
                    val errorMsg = when (throwable) {
                        is AuthException -> throwable.message
                        else -> throwable.message ?: "Failed to submit password reset request."
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

    fun resetPassword(onSuccess: () -> Unit) {
        if (_uiState.value.isLoading) return

        val state = _uiState.value
        val email = state.email.trim()
        val token = state.token.trim()
        val newPassword = state.newPassword
        val confirmPassword = state.confirmPassword

        var hasError = false
        var tokenErr: String? = null
        var passwordErr: String? = null
        var confirmErr: String? = null

        if (token.isBlank()) {
            tokenErr = "Reset code is required"
            hasError = true
        }

        if (newPassword.length < 8) {
            passwordErr = "Password must be at least 8 characters"
            hasError = true
        } else if (!newPassword.any { it.isDigit() } || !newPassword.any { it.isLetter() }) {
            passwordErr = "Password must contain both letters and numbers"
            hasError = true
        }

        if (confirmPassword.isBlank()) {
            confirmErr = "Please confirm your new password"
            hasError = true
        } else if (newPassword != confirmPassword) {
            confirmErr = "Passwords do not match"
            hasError = true
        }

        if (hasError) {
            _uiState.update {
                it.copy(
                    tokenError = tokenErr,
                    newPasswordError = passwordErr,
                    confirmPasswordError = confirmErr
                )
            }
            return
        }

        _uiState.update { it.copy(isLoading = true, generalError = null) }

        viewModelScope.launch {
            val result = authRepository.resetPassword(email, token, newPassword)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            successMessage = "Password has been successfully updated."
                        )
                    }
                    onSuccess()
                },
                onFailure = { throwable ->
                    val errorMsg = when (throwable) {
                        is AuthException -> throwable.message
                        else -> throwable.message ?: "Failed to reset password."
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
}
