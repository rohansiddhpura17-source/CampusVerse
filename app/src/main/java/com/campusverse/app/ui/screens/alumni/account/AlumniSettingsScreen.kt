package com.campusverse.app.ui.screens.alumni.account

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.campusverse.app.domain.auth.AuthenticatedUser
import com.campusverse.app.domain.session.SessionManager
import com.campusverse.app.navigation.Screen
import com.campusverse.app.ui.components.CampusVerseButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlumniSettingsScreen(
    currentUser: AuthenticatedUser? = null,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    sessionManager: SessionManager? = null,
    onLogout: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showPasswordDialog by remember { mutableStateOf(false) }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    var showLogoutDialog by remember { mutableStateOf(false) }

    var alumniProfile by remember { mutableStateOf<com.campusverse.app.data.model.AlumniProfileData?>(null) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        alumniProfile = com.campusverse.app.data.repository.NetworkAlumniRepository.instance.getMyProfile().getOrNull()
    }

    val displayName = alumniProfile?.fullName?.takeIf { it.isNotBlank() } ?: currentUser?.name?.takeIf { it.isNotBlank() } ?: "Alumni Member"
    val displayEmail = alumniProfile?.email?.takeIf { it.isNotBlank() } ?: currentUser?.email?.takeIf { it.isNotBlank() } ?: "alumni@campusverse.edu"

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
            text = { Text("Are you sure you want to sign out of your Alumni account?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        scope.launch {
                            sessionManager?.clearSession()
                            com.campusverse.app.data.repository.NetworkAlumniRepository.instance.clearCache()
                            onLogout()
                        }
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
                title = { Text("Settings & Preferences", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            // Account Group
            item {
                Text(text = "Account", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                SettingsGroupCard {
                    SettingsRow(
                        title = "Full Name",
                        subtitle = displayName,
                        icon = Icons.Default.Person,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigate(Screen.AlumniProfile.route) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsRow(
                        title = "Email Address",
                        subtitle = displayEmail,
                        icon = Icons.Default.Info,
                        color = MaterialTheme.colorScheme.secondary,
                        onClick = {}
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsRow(
                        title = "Current Position",
                        subtitle = "${alumniProfile?.designation ?: "Senior Software Engineer"} @ ${alumniProfile?.company ?: "Technology Corp"}",
                        icon = Icons.Default.Work,
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = { onNavigate(Screen.AlumniProfile.route) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    SettingsRow(
                        title = "Alma Mater",
                        subtitle = "${alumniProfile?.institution ?: "National Institute of Technology"} • ${alumniProfile?.degree ?: "B.Tech CS"}",
                        icon = Icons.Default.School,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigate(Screen.AlumniProfile.route) }
                    )
                }
            }

            // Preferences Group
            item {
                Text(text = "Career & Notifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                SettingsGroupCard {
                    SettingsRow(
                        title = "Career Preferences",
                        subtitle = "Target roles, locations, salary, remote filters",
                        icon = Icons.Default.Work,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigate(Screen.AlumniCareerPreferences.route) }
                    )
                    SettingsRow(
                        title = "Notifications & Alerts",
                        subtitle = "Job alerts, mentorship and network pings",
                        icon = Icons.Default.Notifications,
                        color = MaterialTheme.colorScheme.secondary,
                        onClick = { onNavigate(Screen.AlumniNotifications.route) }
                    )
                }
            }

            // Security & Privacy Group
            item {
                Text(text = "Privacy & Security", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                SettingsGroupCard {
                    SettingsRow(
                        title = "Change Password",
                        subtitle = "Update security password",
                        icon = Icons.Default.Lock,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { showPasswordDialog = true }
                    )
                    SettingsRow(
                        title = "Privacy Settings",
                        subtitle = "Contact info visibility, messaging & mentorship controls",
                        icon = Icons.Default.Security,
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = { onNavigate(Screen.AlumniPrivacySettings.route) }
                    )
                    SettingsRow(
                        title = "Security & 2FA",
                        subtitle = "Two-factor authentication, login alerts",
                        icon = Icons.Default.Security,
                        color = Color(0xFFFF9100),
                        onClick = { onNavigate(Screen.AlumniSecuritySettings.route) }
                    )
                    SettingsRow(
                        title = "Account Recovery",
                        subtitle = "Backup email and recovery security tokens",
                        icon = Icons.Default.Restore,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigate(Screen.AlumniAccountRecovery.route) }
                    )
                }
            }

            // Legal & Help Group
            item {
                Text(text = "Help & Compliance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            item {
                SettingsGroupCard {
                    SettingsRow(
                        title = "Help & Support",
                        subtitle = "FAQ, ticket submission & knowledge base",
                        icon = Icons.Default.Help,
                        color = MaterialTheme.colorScheme.primary,
                        onClick = { onNavigate(Screen.AlumniHelpSupport.route) }
                    )
                    SettingsRow(
                        title = "About CampusVerse",
                        subtitle = "Platform version, licenses & credits",
                        icon = Icons.Default.Info,
                        color = MaterialTheme.colorScheme.secondary,
                        onClick = { onNavigate(Screen.AlumniAbout.route) }
                    )
                    SettingsRow(
                        title = "Terms of Service",
                        subtitle = "CampusVerse platform terms & conditions",
                        icon = Icons.Default.Policy,
                        color = MaterialTheme.colorScheme.tertiary,
                        onClick = { onNavigate(Screen.AlumniTerms.route) }
                    )
                    SettingsRow(
                        title = "Community Guidelines",
                        subtitle = "Anti-harassment and professional standards",
                        icon = Icons.Default.Description,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { onNavigate(Screen.AlumniCommunityGuidelines.route) }
                    )
                }
            }

            // Logout Action
            item {
                Button(
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Icon(Icons.Default.Logout, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out from CampusVerse", fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
