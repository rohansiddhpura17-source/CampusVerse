package com.campusverse.app.ui.screens.createaccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.UserRole
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

data class RegisterUiState(
    // Step 1: Account Details
    val name: String = "",
    val nameError: String? = null,
    val email: String = "",
    val emailError: String? = null,
    val password: String = "",
    val passwordError: String? = null,
    val confirmPassword: String = "",
    val confirmPasswordError: String? = null,

    // Step 2: Role Selection - MUST start null
    val selectedRole: UserRole? = null,
    val canContinueRoleSelection: Boolean = false,

    // Submission state
    val generalError: String? = null,
    val isLoading: Boolean = false
)

class RegisterViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val emailPattern = Pattern.compile(
        "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,64}$"
    )

    fun onNameChange(name: String) {
        _uiState.update {
            it.copy(
                name = name,
                nameError = null,
                generalError = null
            )
        }
    }

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

    fun onConfirmPasswordChange(confirmPassword: String) {
        _uiState.update {
            it.copy(
                confirmPassword = confirmPassword,
                confirmPasswordError = null,
                generalError = null
            )
        }
    }

    /**
     * Validates account creation form fields.
     * Returns true if valid, false otherwise with updated field errors.
     */
    fun validateAccountDetails(): Boolean {
        val state = _uiState.value
        val name = state.name.trim()
        val email = state.email.trim()
        val password = state.password
        val confirmPassword = state.confirmPassword

        var isValid = true
        var nameErr: String? = null
        var emailErr: String? = null
        var passwordErr: String? = null
        var confirmPasswordErr: String? = null

        if (name.isBlank()) {
            nameErr = "Full name is required"
            isValid = false
        }

        if (email.isBlank()) {
            emailErr = "Email address is required"
            isValid = false
        } else if (!emailPattern.matcher(email).matches()) {
            emailErr = "Please enter a valid email address"
            isValid = false
        }

        if (password.length < 8) {
            passwordErr = "Password must be at least 8 characters"
            isValid = false
        } else if (!password.any { it.isLetter() } || !password.any { it.isDigit() }) {
            passwordErr = "Password must contain both letters and numbers"
            isValid = false
        }

        if (confirmPassword.isBlank()) {
            confirmPasswordErr = "Please confirm your password"
            isValid = false
        } else if (password != confirmPassword) {
            confirmPasswordErr = "Passwords do not match"
            isValid = false
        }

        _uiState.update {
            it.copy(
                nameError = nameErr,
                emailError = emailErr,
                passwordError = passwordErr,
                confirmPasswordError = confirmPasswordErr
            )
        }

        return isValid
    }

    /**
     * Selects a role during the role selection step.
     * Enforces single role selection.
     */
    fun onRoleSelected(role: UserRole) {
        _uiState.update {
            it.copy(
                selectedRole = role,
                canContinueRoleSelection = true,
                generalError = null
            )
        }
    }

    /**
     * Clears role selection back to null.
     */
    fun clearRoleSelection() {
        _uiState.update {
            it.copy(
                selectedRole = null,
                canContinueRoleSelection = false
            )
        }
    }

    /**
     * Completes registration with the validated credentials and chosen role.
     */
    fun completeRegistration(onSuccess: (AuthenticatedUser) -> Unit) {
        if (_uiState.value.isLoading) return

        val state = _uiState.value
        val role = state.selectedRole

        if (role == null) {
            _uiState.update { it.copy(generalError = "Please select a role to proceed.") }
            return
        }

        if (!validateAccountDetails()) {
            _uiState.update { it.copy(generalError = "Please resolve account form errors.") }
            return
        }

        _uiState.update { it.copy(isLoading = true, generalError = null) }

        viewModelScope.launch {
            val result = authRepository.register(
                name = state.name.trim(),
                email = state.email.trim(),
                password = state.password,
                role = role
            )

            result.fold(
                onSuccess = { user ->
                    _uiState.update { it.copy(isLoading = false) }
                    onSuccess(user)
                },
                onFailure = { throwable ->
                    val message = when (throwable) {
                        is AuthException -> throwable.message
                        else -> throwable.message ?: "Registration failed. Please try again."
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generalError = message
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
