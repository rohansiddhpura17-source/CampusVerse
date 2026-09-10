package com.campusverse.app.ui.screens.alumni.careers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.ReferralItem
import com.campusverse.app.data.repository.NetworkAlumniRepository
import com.campusverse.app.domain.alumni.AlumniRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReferralsUiState {
    data object Loading : ReferralsUiState
    data class Success(val referrals: List<ReferralItem>) : ReferralsUiState
    data class Error(val message: String) : ReferralsUiState
}

class ReferralsViewModel(
    private val repository: AlumniRepository = NetworkAlumniRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReferralsUiState>(ReferralsUiState.Loading)
    val uiState: StateFlow<ReferralsUiState> = _uiState.asStateFlow()

    init {
        loadReferrals()
    }

    fun loadReferrals() {
        viewModelScope.launch {
            _uiState.value = ReferralsUiState.Loading
            repository.getReferrals()
                .onSuccess { list ->
                    _uiState.value = ReferralsUiState.Success(list)
                }
                .onFailure { err ->
                    _uiState.value = ReferralsUiState.Error(err.message ?: "Failed to load referrals.")
                }
        }
    }

    fun updateStatus(referralId: String, status: String, notes: String?) {
        viewModelScope.launch {
            repository.updateReferralStatus(referralId, status, notes)
            loadReferrals()
        }
    }
}
