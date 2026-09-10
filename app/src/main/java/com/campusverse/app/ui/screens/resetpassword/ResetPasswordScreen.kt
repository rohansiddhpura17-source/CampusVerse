package com.campusverse.app.ui.screens.resetpassword

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusverse.app.ui.components.CampusVerseErrorMessage
import com.campusverse.app.ui.components.CampusVersePrimaryButton
import com.campusverse.app.ui.components.CampusVerseTextField
import com.campusverse.app.ui.components.CampusVerseTopBar
import com.campusverse.app.ui.screens.forgotpassword.ForgotPasswordViewModel
import com.campusverse.app.ui.theme.CampusVerseTheme

@Composable
fun ResetPasswordScreen(
    onNavigateBack: () -> Unit,
    onResetSuccess: () -> Unit,
    viewModel: ForgotPasswordViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            CampusVerseTopBar(
                title = "",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Password,
                            contentDescription = "New Password",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Reset Password",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Create a new secure password for your account.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                if (!uiState.generalError.isNullOrBlank()) {
                    CampusVerseErrorMessage(message = uiState.generalError!!)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (!uiState.successMessage.isNullOrBlank()) {
                    Text(
                        text = uiState.successMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Reset Token Code
                CampusVerseTextField(
                    value = uiState.token,
                    onValueChange = { viewModel.onTokenChange(it) },
                    label = "Reset Code",
                    placeholder = "123456",
                    leadingIcon = Icons.Outlined.Key,
                    isError = uiState.tokenError != null,
                    errorMessage = uiState.tokenError ?: "",
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // New Password
                CampusVerseTextField(
                    value = uiState.newPassword,
                    onValueChange = { viewModel.onNewPasswordChange(it) },
                    label = "New Password",
                    placeholder = "Min 8 chars, letters & numbers",
                    leadingIcon = Icons.Outlined.Lock,
                    isPassword = true,
                    isError = uiState.newPasswordError != null,
                    errorMessage = uiState.newPasswordError ?: "",
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Confirm New Password
                CampusVerseTextField(
                    value = uiState.confirmPassword,
                    onValueChange = { viewModel.onConfirmPasswordChange(it) },
                    label = "Confirm New Password",
                    placeholder = "Re-enter new password",
                    leadingIcon = Icons.Outlined.Lock,
                    isPassword = true,
                    isError = uiState.confirmPasswordError != null,
                    errorMessage = uiState.confirmPasswordError ?: "",
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            viewModel.resetPassword(onSuccess = onResetSuccess)
                        }
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                CampusVersePrimaryButton(
                    text = "Reset & Log In",
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.resetPassword(onSuccess = onResetSuccess)
                    },
                    enabled = !uiState.isLoading,
                    isLoading = uiState.isLoading
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Preview(name = "Reset Password 390dp", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun ResetPasswordPreview390dp() {
    CampusVerseTheme {
        ResetPasswordScreen(
            onNavigateBack = {},
            onResetSuccess = {},
            viewModel = ForgotPasswordViewModel()
        )
    }
}
