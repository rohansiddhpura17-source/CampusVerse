package com.campusverse.app.ui.screens.roleselection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusverse.app.data.model.UserRole

import com.campusverse.app.ui.components.CampusVerseErrorMessage
import com.campusverse.app.ui.components.CampusVersePrimaryButton
import com.campusverse.app.ui.components.CampusVerseTopBar
import com.campusverse.app.ui.components.RoleOptionCard
import com.campusverse.app.ui.screens.createaccount.RegisterViewModel
import com.campusverse.app.ui.theme.CampusVerseTheme

/**
 * Stateful Role Selection Screen.
 * Can be used in standalone mode or as Step 2 of the Registration flow.
 */
@Composable
fun RoleSelectionScreen(
    onNavigateToRoute: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    registerViewModel: RegisterViewModel? = null,
    standaloneViewModel: RoleSelectionViewModel = viewModel()
) {
    if (registerViewModel != null) {
        val registerState by registerViewModel.uiState.collectAsStateWithLifecycle()

        RoleSelectionContent(
            uiState = RoleSelectionUiState(
                selectedRole = registerState.selectedRole,
                canContinue = registerState.canContinueRoleSelection
            ),
            isLoading = registerState.isLoading,
            generalError = registerState.generalError,
            onSelectRole = { role -> registerViewModel.onRoleSelected(role) },
            onContinueClick = {
                registerViewModel.completeRegistration { _ ->
                    // Always go to verification after registration — email must be confirmed
                    onNavigateToRoute("verification")
                }
            },
            onNavigateBack = onNavigateBack,
            modifier = modifier
        )
    } else {
        val uiState by standaloneViewModel.uiState.collectAsStateWithLifecycle()

        RoleSelectionContent(
            uiState = uiState,
            isLoading = false,
            generalError = null,
            onSelectRole = { role -> standaloneViewModel.selectRole(role) },
            onContinueClick = {
                standaloneViewModel.getDestinationForSelectedRole()?.let { route ->
                    onNavigateToRoute(route)
                }
            },
            onNavigateBack = onNavigateBack,
            modifier = modifier
        )
    }
}

/**
 * Stateless Role Selection Content.
 * Responsive layout supporting 360dp, 390dp, and 412dp screen widths.
 */
@Composable
fun RoleSelectionContent(
    uiState: RoleSelectionUiState,
    isLoading: Boolean = false,
    generalError: String? = null,
    onSelectRole: (UserRole) -> Unit,
    onContinueClick: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val roles = listOf(
        UserRole.ASPIRANT,
        UserRole.STUDENT,
        UserRole.ALUMNI,
        UserRole.ADMIN
    )

    Scaffold(
        topBar = {
            CampusVerseTopBar(
                title = "",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(36.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        CampusVersePrimaryButton(
                            text = "Continue",
                            onClick = onContinueClick,
                            enabled = uiState.canContinue && !isLoading
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerLow,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "Select Your Role",
                            style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Choose how you'll use CampusVerse",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!generalError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            CampusVerseErrorMessage(message = generalError)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                items(
                    items = roles,
                    key = { it.name }
                ) { role ->
                    RoleOptionCard(
                        role = role,
                        isSelected = uiState.selectedRole == role,
                        onSelect = { if (!isLoading) onSelectRole(role) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Previews for Responsive Screen Sizes (360dp, 390dp, 412dp)
// -----------------------------------------------------------------------------

@Preview(name = "Small Screen 360dp - No Selection", widthDp = 360, heightDp = 640, showBackground = true)
@Composable
fun RoleSelectionPreview360dpNull() {
    CampusVerseTheme {
        RoleSelectionContent(
            uiState = RoleSelectionUiState(selectedRole = null, canContinue = false),
            onSelectRole = {},
            onContinueClick = {},
            onNavigateBack = {}
        )
    }
}

@Preview(name = "Standard Screen 390dp - Student Selected", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun RoleSelectionPreview390dpStudent() {
    CampusVerseTheme {
        RoleSelectionContent(
            uiState = RoleSelectionUiState(selectedRole = UserRole.STUDENT, canContinue = true),
            onSelectRole = {},
            onContinueClick = {},
            onNavigateBack = {}
        )
    }
}

@Preview(name = "Large Screen 412dp - Admin Selected (Dark)", widthDp = 412, heightDp = 915, showBackground = true)
@Composable
fun RoleSelectionPreview412dpAdminDark() {
    CampusVerseTheme(darkTheme = true) {
        RoleSelectionContent(
            uiState = RoleSelectionUiState(selectedRole = UserRole.ADMIN, canContinue = true),
            onSelectRole = {},
            onContinueClick = {},
            onNavigateBack = {}
        )
    }
}
