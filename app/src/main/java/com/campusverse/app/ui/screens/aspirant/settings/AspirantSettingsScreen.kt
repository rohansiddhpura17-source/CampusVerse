package com.campusverse.app.ui.screens.aspirant.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.campusverse.app.domain.auth.AuthenticatedUser
import com.campusverse.app.ui.components.CampusVerseButton
import com.campusverse.app.ui.components.CampusVerseOutlinedButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspirantSettingsScreen(
    currentUser: AuthenticatedUser? = null,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var admissionAlerts by remember { mutableStateOf(true) }
    var scholarshipReminders by remember { mutableStateOf(true) }
    var emailNotifications by remember { mutableStateOf(true) }
    var biometricLogin by remember { mutableStateOf(true) }
    var twoFactorAuth by remember { mutableStateOf(true) }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var aspirantProfile by remember { mutableStateOf<com.campusverse.app.data.model.AspirantProfileData?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        aspirantProfile = com.campusverse.app.data.repository.NetworkAspirantRepository.instance.getAspirantProfile().getOrNull()
    }

    val displayName = aspirantProfile?.fullName?.takeIf { it.isNotBlank() } ?: currentUser?.name?.takeIf { it.isNotBlank() } ?: "Aspirant"
    val displayEmail = aspirantProfile?.email?.takeIf { it.isNotBlank() } ?: currentUser?.email?.takeIf { it.isNotBlank() } ?: "aspirant@campusverse.edu"

    if (showPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Change Password", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (passwordError != null) {
                        Text(text = passwordError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    OutlinedTextField(
                        value = currentPassword,
                        onValueChange = { currentPassword = it },
                        label = { Text("Current Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = { newPassword = it },
                        label = { Text("New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = confirmNewPassword,
                        onValueChange = { confirmNewPassword = it },
                        label = { Text("Confirm New Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                CampusVerseButton(
                    text = "Update Password",
                    onClick = {
                        if (newPassword.length < 8) {
                            passwordError = "Password must be at least 8 characters"
                        } else if (newPassword != confirmNewPassword) {
                            passwordError = "New passwords do not match"
                        } else {
                            showPasswordDialog = false
                            currentPassword = ""
                            newPassword = ""
                            confirmNewPassword = ""
                            passwordError = null
                            scope.launch {
                                snackbarHostState.showSnackbar("Password updated successfully")
                            }
                        }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to sign out of your Aspirant account?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    }
                ) {
                    Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section: Personal Information
            item {
                SettingsSectionHeader("Personal Information")
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsItemRow(
                            icon = Icons.Default.Person,
                            title = "Full Name",
                            subtitle = displayName,
                            onClick = onNavigateBack
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingsItemRow(
                            icon = Icons.Default.Mail,
                            title = "Contact Email",
                            subtitle = displayEmail,
                            onClick = {}
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingsItemRow(
                            icon = Icons.Default.School,
                            title = "Academic Target",
                            subtitle = "${aspirantProfile?.targetDegree ?: "B.Tech"} - ${aspirantProfile?.targetMajor ?: "Computer Science & AI"}",
                            onClick = {}
                        )
                    }
                }
            }

            // Section: Account Details
            item {
                SettingsSectionHeader("Account")
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsItemRow(
                            icon = Icons.Default.Badge,
                            title = "Account Role",
                            subtitle = "Aspirant",
                            hasArrow = false
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingsItemRow(
                            icon = Icons.Default.PhoneIphone,
                            title = "Email Verification",
                            subtitle = "Verified",
                            subtitleColor = MaterialTheme.colorScheme.primary,
                            hasArrow = false
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        val displayTargets = aspirantProfile?.targetUniversities?.ifBlank { "IIT Bombay, NIT Bangalore, BITS Pilani" } ?: "IIT Bombay, NIT Bangalore, BITS Pilani"
                        SettingsItemRow(
                            icon = Icons.AutoMirrored.Filled.MenuBook,
                            title = "Target Institutions",
                            subtitle = displayTargets,
                            hasArrow = false
                        )
                    }
                }
            }

            // Section: Password & Security
            item {
                SettingsSectionHeader("Password & Security")
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingsItemRow(
                            icon = Icons.Default.Password,
                            title = "Change Password",
                            subtitle = "Update security credentials",
                            onClick = { showPasswordDialog = true }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingToggleRow(
                            icon = Icons.Default.Fingerprint,
                            title = "Biometric Login",
                            subtitle = "Use fingerprint or face unlock",
                            checked = biometricLogin,
                            onCheckedChange = {
                                biometricLogin = it
                                scope.launch { snackbarHostState.showSnackbar("Biometric login ${if (it) "enabled" else "disabled"}") }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingToggleRow(
                            icon = Icons.Default.VerifiedUser,
                            title = "Two-Factor Authentication",
                            subtitle = "OTP verification on new sign-ins",
                            checked = twoFactorAuth,
                            onCheckedChange = {
                                twoFactorAuth = it
                                scope.launch { snackbarHostState.showSnackbar("2FA ${if (it) "enabled" else "disabled"}") }
                            }
                        )
                    }
                }
            }

            // Section: Notification Preferences
            item {
                SettingsSectionHeader("Notifications & Alerts")
                Spacer(modifier = Modifier.height(6.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingToggleRow(
                            icon = Icons.Default.School,
                            title = "Admission Alerts",
                            subtitle = "Cutoff & deadline notifications",
                            checked = admissionAlerts,
                            onCheckedChange = {
                                admissionAlerts = it
                                scope.launch { snackbarHostState.showSnackbar("Admission alerts updated") }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingToggleRow(
                            icon = Icons.Default.Lock,
                            title = "Scholarship Reminders",
                            subtitle = "New grants matching your profile",
                            checked = scholarshipReminders,
                            onCheckedChange = {
                                scholarshipReminders = it
                                scope.launch { snackbarHostState.showSnackbar("Scholarship alerts updated") }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        SettingToggleRow(
                            icon = Icons.Default.Mail,
                            title = "Email Notifications",
                            subtitle = "Weekly digest and webinar invites",
                            checked = emailNotifications,
                            onCheckedChange = {
                                emailNotifications = it
                                scope.launch { snackbarHostState.showSnackbar("Email notifications updated") }
                            }
                        )
                    }
                }
            }

            // Sign Out
            item {
                Spacer(modifier = Modifier.height(8.dp))
                CampusVerseOutlinedButton(
                    text = "Sign Out of CampusVerse",
                    onClick = { showLogoutDialog = true }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Composable
private fun SettingsItemRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleColor: Color = Color.Unspecified,
    hasArrow: Boolean = true,
    onClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasArrow, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (subtitleColor != Color.Unspecified) subtitleColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (hasArrow) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
