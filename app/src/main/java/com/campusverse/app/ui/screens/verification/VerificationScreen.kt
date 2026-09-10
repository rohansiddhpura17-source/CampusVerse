package com.campusverse.app.ui.screens.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusverse.app.domain.auth.VerificationStatus
import com.campusverse.app.ui.components.CampusVerseErrorMessage
import com.campusverse.app.ui.components.CampusVersePrimaryButton
import com.campusverse.app.ui.components.CampusVerseTopBar
import com.campusverse.app.ui.theme.CampusVerseTheme
import kotlinx.coroutines.delay

@Composable
fun VerificationScreen(
    email: String,
    onVerificationSuccess: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VerificationViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    var resendCooldown by remember { mutableIntStateOf(60) }

    LaunchedEffect(uiState.email) {
        if (uiState.email.isBlank() && email.isNotBlank()) {
            viewModel.setEmail(email)
        }
    }

    // Resend countdown timer
    LaunchedEffect(resendCooldown) {
        if (resendCooldown > 0) {
            delay(1000L)
            resendCooldown--
        }
    }

    // Auto-focus the OTP input field on screen launch
    LaunchedEffect(Unit) {
        delay(300L)
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

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
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerLow,
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
                            imageVector = Icons.Outlined.MarkEmailRead,
                            contentDescription = "Verify Email",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Verify Code",
                    style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Enter the 6-digit code sent to\n${uiState.email.ifBlank { email }}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Info Banner (e.g. after resend)
                if (!uiState.infoMessage.isNullOrBlank()) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = uiState.infoMessage!!,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                if (!uiState.errorMessage.isNullOrBlank()) {
                    CampusVerseErrorMessage(message = uiState.errorMessage!!)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 6-Digit Interactive PIN Boxes
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            focusRequester.requestFocus()
                        }
                ) {
                    // Hidden BasicTextField capturing keyboard inputs
                    BasicTextField(
                        value = uiState.otpCode,
                        onValueChange = { newCode ->
                            val digits = newCode.filter { it.isDigit() }.take(6)
                            viewModel.onOtpChange(digits)
                            if (digits.length == 6) {
                                focusManager.clearFocus()
                                viewModel.verifyOtp(onSuccess = onVerificationSuccess)
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                viewModel.verifyOtp(onSuccess = onVerificationSuccess)
                            }
                        ),
                        modifier = Modifier
                            .size(1.dp)
                            .focusRequester(focusRequester)
                    )

                    // Visual 6-Box Display
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (i in 0 until 6) {
                            val digit = uiState.otpCode.getOrNull(i)?.toString() ?: ""
                            val isFocused = uiState.otpCode.length == i || (i == 5 && uiState.otpCode.length == 6)
                            val isFilled = digit.isNotEmpty()

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(width = 46.dp, height = 56.dp)
                                    .background(
                                        color = if (isFilled) {
                                            MaterialTheme.colorScheme.surfaceContainerLowest
                                        } else {
                                            MaterialTheme.colorScheme.surfaceContainerLowest
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .border(
                                        width = if (isFocused) 2.dp else 1.dp,
                                        color = if (isFocused) {
                                            MaterialTheme.colorScheme.primary
                                        } else if (isFilled) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Text(
                                    text = digit,
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                CampusVersePrimaryButton(
                    text = "Verify & Proceed",
                    onClick = {
                        focusManager.clearFocus()
                        viewModel.verifyOtp(onSuccess = onVerificationSuccess)
                    },
                    enabled = uiState.otpCode.length == 6 && !uiState.isLoading,
                    isLoading = uiState.isLoading
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Didn't receive the code?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            resendCooldown = 30
                            viewModel.resendOtp()
                        },
                        enabled = !uiState.isLoading && resendCooldown == 0
                    ) {
                        Text(
                            text = if (resendCooldown > 0) "Resend in ${resendCooldown}s" else "Resend Code",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = if (resendCooldown > 0) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Preview(name = "Verification 390dp", widthDp = 390, heightDp = 844, showBackground = true)
@Composable
fun VerificationPreview390dp() {
    CampusVerseTheme {
        VerificationScreen(
            email = "alex@university.edu",
            onVerificationSuccess = {},
            onNavigateBack = {}
        )
    }
}
