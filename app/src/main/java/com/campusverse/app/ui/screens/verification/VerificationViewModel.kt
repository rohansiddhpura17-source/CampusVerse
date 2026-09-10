package com.campusverse.app.ui.screens.verification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.repository.AuthRepositoryImpl
import com.campusverse.app.domain.auth.AuthException
import com.campusverse.app.domain.auth.AuthRepository
import com.campusverse.app.domain.auth.VerificationStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VerificationUiState(
    val email: String = "",
    val otpCode: String = "",
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isLoading: Boolean = false
)

class VerificationViewModel(
    private val authRepository: AuthRepository = AuthRepositoryImpl.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow(VerificationUiState())
    val uiState: StateFlow<VerificationUiState> = _uiState.asStateFlow()

    fun setEmail(email: String) {
        val trimmed = email.trim()
        _uiState.update { it.copy(email = trimmed) }
        if (trimmed.isNotBlank()) {
            viewModelScope.launch {
                val devOtp = authRepository.getActiveOtpForDisplay(trimmed)
                if (devOtp != null) {
                    _uiState.update {
                        it.copy(infoMessage = "Verification Code: $devOtp")
                    }
                }
            }
        }
    }

    fun onOtpChange(otp: String) {
        val cleanOtp = otp.filter { it.isDigit() }.take(6)
        _uiState.update {
            it.copy(
                otpCode = cleanOtp,
                errorMessage = null
            )
        }
    }

    fun verifyOtp(onSuccess: () -> Unit) {
        if (_uiState.value.isLoading) return

        val state = _uiState.value
        val otp = state.otpCode.trim()
        val email = state.email.trim()

        if (otp.isBlank() || otp.length < 6) {
            _uiState.update {
                it.copy(errorMessage = "Please enter the complete 6-digit verification code.")
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                verificationStatus = VerificationStatus.PENDING,
                errorMessage = null
            )
        }

        viewModelScope.launch {
            val result = authRepository.verifyOtp(email, otp)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            verificationStatus = VerificationStatus.VERIFIED,
                            infoMessage = "Email verified successfully!"
                        )
                    }
                    onSuccess()
                },
                onFailure = { throwable ->
                    val errorMsg = when (throwable) {
                        is AuthException -> throwable.message
                        else -> throwable.message ?: "Verification failed. Please check the code."
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            verificationStatus = VerificationStatus.FAILED,
                            errorMessage = errorMsg
                        )
                    }
                }
            )
        }
    }

    fun resendOtp() {
        if (_uiState.value.isLoading) return

        val email = _uiState.value.email.trim()
        if (email.isBlank()) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null, infoMessage = null) }

        viewModelScope.launch {
            val result = authRepository.resendOtp(email)
            result.fold(
                onSuccess = {
                    val devOtp = authRepository.getActiveOtpForDisplay(email)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            otpCode = "",
                            infoMessage = if (devOtp != null) "Verification Code: $devOtp" else "A fresh 6-digit verification code has been sent to $email."
                        )
                    }
                },
                onFailure = { throwable ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = throwable.message ?: "Failed to resend code."
                        )
                    }
                }
            )
        }
    }
}
