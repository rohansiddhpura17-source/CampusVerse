package com.campusverse.app.ui.screens.admin.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campusverse.app.data.model.AdminPlatformSettingsData
import com.campusverse.app.data.repository.NetworkAdminRepository
import com.campusverse.app.domain.admin.AdminRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AdminSettingsUiState {
    object Loading : AdminSettingsUiState
    data class Success(
        val settings: AdminPlatformSettingsData,
        val feedbackMessage: String? = null
    ) : AdminSettingsUiState
    data class Error(val message: String) : AdminSettingsUiState
}

class AdminSettingsViewModel(
    private val repository: AdminRepository = NetworkAdminRepository.instance
) : ViewModel() {

    private val _uiState = MutableStateFlow<AdminSettingsUiState>(AdminSettingsUiState.Loading)
    val uiState: StateFlow<AdminSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            _uiState.value = AdminSettingsUiState.Loading
            repository.getPlatformSettings()
                .onSuccess { data ->
                    _uiState.value = AdminSettingsUiState.Success(settings = data)
                }
                .onFailure { err ->
                    _uiState.value = AdminSettingsUiState.Error(err.message ?: "Failed to load platform settings")
                }
        }
    }

    fun updateSetting(
        maintenanceMode: Boolean? = null,
        allowRegistrations: Boolean? = null,
        autoModeration: Boolean? = null,
        strictVerification: Boolean? = null,
        require2FA: Boolean? = null
    ) {
        val current = _uiState.value
        if (current !is AdminSettingsUiState.Success) return

        val newSettings = current.settings.copy(
            maintenanceMode = maintenanceMode ?: current.settings.maintenanceMode,
            allowNewRegistrations = allowRegistrations ?: current.settings.allowNewRegistrations,
            autoModeration = autoModeration ?: current.settings.autoModeration,
            strictVerification = strictVerification ?: current.settings.strictVerification,
            require2FAForAdmins = require2FA ?: current.settings.require2FAForAdmins
        )

        viewModelScope.launch {
            repository.updatePlatformSettings(newSettings)
                .onSuccess { updated ->
                    _uiState.value = AdminSettingsUiState.Success(
                        settings = updated,
                        feedbackMessage = "Platform settings saved."
                    )
                }
        }
    }
}
