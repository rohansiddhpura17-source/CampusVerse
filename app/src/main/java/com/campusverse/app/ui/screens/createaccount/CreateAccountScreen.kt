package com.campusverse.app.ui.screens.createaccount

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campusverse.app.ui.components.CampusVerseErrorMessage
import com.campusverse.app.ui.components.CampusVersePrimaryButton
import com.campusverse.app.ui.components.CampusVerseTextField
import com.campusverse.app.ui.components.CampusVerseTopBar
import com.campusverse.app.ui.theme.CampusVerseTheme

@Composable
fun CreateAccountScreen(
    onNavigateBack: () -> Unit,
    onNavigateToRoleSelection: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: RegisterViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    var termsAccepted by remember { mutableStateOf(true) }

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
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Create Account",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Join the CampusVerse ecosystem",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (!uiState.generalError.isNullOrBlank()) {
                    CampusVerseErrorMessage(message = uiState.generalError!!)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Full Name
                CampusVerseTextField(
                    value = uiState.name,
                    onValueChange = { viewModel.onNameChange(it) },
                    label = "Full Name",
                    placeholder = "Jane Doe",
                    leadingIcon = Icons.Outlined.Person,
                    isError = uiState.nameError != null,
                    errorMessage = uiState.nameError ?: "",
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

                // College Email
                CampusVerseTextField(
                    value = uiState.email,
                    onValueChange = { viewModel.onEmailChange(it) },
                    label = "College Email",
                    placeholder = "jane@university.edu",
                    leadingIcon = Icons.Outlined.Email,
                    isError = uiState.emailError != null,
                    errorMessage = uiState.emailError ?: "",
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password
                CampusVerseTextField(
                    value = uiState.password,
                    onValueChange = { viewModel.onPasswordChange(it) },
                    label = "Password",
                    placeholder = "••••••••",
                    leadingIcon = Icons.Outlined.Lock,
                    isPassword = true,
                    isError = uiState.passwordError != null,
                    errorMessage = uiState.passwordError ?: "",
                    enabled = !uiState.isLoading,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    )
                )

                // Password Strength Indicator Bar
                Spacer(modifier = Modifier.height(6.dp))
                PasswordStrengthIndicator(password = uiState.password)

                Spacer(modifier = Modifier.height(16.dp))

                // Confirm Password
                CampusVerseTextField(
                    value = uiState.confirmPassword,
                    onValueChange = { viewModel.onConfirmPasswordChange(it) },
                    label = "Confirm Password",
                    placeholder = "••••••••",
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
                            if (viewModel.validateAccountDetails()) {
                                onNavigateToRoleSelection()
                            }
                        }
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Terms & Conditions Checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { termsAccepted = !termsAccepted }
                ) {
                    Checkbox(
                        checked = termsAccepted,
                        onCheckedChange = { termsAccepted = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "I agree to the Terms & Conditions and Privacy Policy",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                CampusVersePrimaryButton(
                    text = "Continue",
                    onClick = {
                        focusManager.clearFocus()
                        if (viewModel.validateAccountDetails()) {
                            onNavigateToRoleSelection()
                        }
                    },
                    enabled = !uiState.isLoading && termsAccepted,
                    isLoading = uiState.isLoading
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    Text(
                        text = "Already have an account? ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = onNavigateToLogin,
                        enabled = !uiState.isLoading
                    ) {
                        Text(
                            text = "Log In",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PasswordStrengthIndicator(password: String) {
    val level = when {
        password.isEmpty() -> 0
        password.length < 8 -> 1
        password.any { it.isDigit() } && password.any { it.isLetter() } -> 3
        else -> 2
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        val activeColor = if (level == 1) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        val inactiveColor = MaterialTheme.colorScheme.surfaceVariant

        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .background(if (level >= 1) activeColor else inactiveColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .background(if (level >= 2) activeColor else inactiveColor, CircleShape)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(4.dp)
                .background(if (level >= 3) activeColor else inactiveColor, CircleShape)
        )
    }
}

@Preview(name = "Create Account 390dp", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun CreateAccountPreview390dp() {
    CampusVerseTheme {
        CreateAccountScreen(
            onNavigateBack = {},
            onNavigateToRoleSelection = {},
            onNavigateToLogin = {},
            viewModel = RegisterViewModel()
        )
    }
}

