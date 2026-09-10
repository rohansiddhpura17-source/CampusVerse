package com.campusverse.app.ui.screens.aspirant.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusverse.app.data.model.AspirantProfileData
import com.campusverse.app.navigation.Screen
import com.campusverse.app.ui.screens.aspirant.components.AspirantBottomBar

/**
 * ASP — Aspirant Profile Screen
 * Faithful to Stitch CampusVerse Executive Design System (projects/8967823161764305568/screens/cf067bd887bc465f998c5a2505c1cfb2).
 * Light background, white surface cards with 16dp radii, MaterialTheme.colorScheme.secondary primary accents,
 * identity hero card, verified badge, academic record bento tiles, and aspirant preferences tags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AspirantProfileScreen(
    currentUser: com.campusverse.app.domain.auth.AuthenticatedUser? = null,
    onNavigateToHome: () -> Unit,
    onNavigateToColleges: () -> Unit,
    onNavigateToScholarships: () -> Unit,
    onNavigateToPredictor: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: AspirantProfileViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var isEditing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Aspirant Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    TextButton(onClick = { isEditing = !isEditing }) {
                        Text(
                            text = if (isEditing) "Done" else "Edit Profile",
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            AspirantBottomBar(
                currentRoute = Screen.AspirantProfile.route,
                onNavigate = { route ->
                    when (route) {
                        Screen.AspirantHome.route -> onNavigateToHome()
                        Screen.AspirantCollegeExplorer.route -> onNavigateToColleges()
                        Screen.AspirantScholarships.route -> onNavigateToScholarships()
                        Screen.AspirantPredictor.route -> onNavigateToPredictor()
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is AspirantProfileUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    }
                }
                is AspirantProfileUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.loadProfile() }) {
                            Text("Retry")
                        }
                    }
                }
                is AspirantProfileUiState.Success -> {
                    if (isEditing) {
                        ProfileEditForm(
                            profile = state.profile,
                            isSaving = state.isSaving,
                            saveSuccess = state.saveSuccess,
                            onSave = { fullName, bio, deg, maj, unis, school, yr, scores ->
                                viewModel.saveProfile(fullName, bio, deg, maj, unis, school, yr, scores)
                            }
                        )
                    } else {
                        ProfileViewContent(
                            currentUser = currentUser,
                            profile = state.profile,
                            onEditClick = { isEditing = true }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileViewContent(
    currentUser: com.campusverse.app.domain.auth.AuthenticatedUser? = null,
    profile: AspirantProfileData,
    onEditClick: () -> Unit
) {
    val displayName = profile.fullName.takeIf { it.isNotBlank() } ?: currentUser?.name ?: "Aspirant"
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Identity Hero Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = displayName.take(1).uppercase(),
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Verified Aspirant",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        val ieltsScore = profile.entranceExamScores["IELTS"]
                        val ieltsBadge = if (ieltsScore != null) "IELTS Band $ieltsScore" else "IELTS Band 6.5"
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = ieltsBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${profile.targetDegree} ${profile.targetMajor} • Applicant (Winter 2026/27)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Academic Record Bento Grid
        item {
            val standingScore = profile.entranceExamScores["GPA"]?.let { "$it%" } ?: "88.5%"
            val langScore = profile.entranceExamScores["IELTS"]?.let { "IELTS Band $it (B2)" } ?: "IELTS Band 6.5 (B2)"
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Academic Record",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AcademicBentoTile(
                        label = "Academic Standing",
                        value = standingScore,
                        sub = "(12th Board / Cutoff)",
                        modifier = Modifier.weight(1f)
                    )
                    AcademicBentoTile(
                        label = "Intended Major",
                        value = profile.targetMajor.ifBlank { "Computer Science & IT" },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AcademicBentoTile(
                        label = "Language Proficiency",
                        value = langScore,
                        sub = "German (A1)",
                        modifier = Modifier.weight(1f)
                    )
                    AcademicBentoTile(
                        label = "Exchange Preference",
                        value = "Free Mover Program",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Aspirant Preferences Tags
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Aspirant Preferences",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PreferenceTag("Germany", isHighlight = true)
                        PreferenceTag("India", isHighlight = true)
                        PreferenceTag("Computer Science")
                        PreferenceTag("AI & ML")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PreferenceTag("Hardware Integration")
                        PreferenceTag("Merit-Based")
                        PreferenceTag("Low Tuition")
                    }
                }
            }
        }
    }
}

@Composable
fun AcademicBentoTile(
    label: String,
    value: String,
    sub: String? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (sub != null) {
                Text(
                    text = sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PreferenceTag(text: String, isHighlight: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isHighlight) MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
        border = if (isHighlight) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)) else null
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (isHighlight) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun ProfileEditForm(
    profile: AspirantProfileData,
    isSaving: Boolean,
    saveSuccess: Boolean,
    onSave: (String, String, String, String, String, String, Int, Map<String, Double>) -> Unit
) {
    var fullName by remember(profile) { mutableStateOf(profile.fullName) }
    var bio by remember(profile) { mutableStateOf(profile.bio ?: "") }
    var targetDegree by remember(profile) { mutableStateOf(profile.targetDegree) }
    var targetMajor by remember(profile) { mutableStateOf(profile.targetMajor) }
    var targetUniversities by remember(profile) { mutableStateOf(profile.targetUniversities) }
    var highSchool by remember(profile) { mutableStateOf(profile.highSchool) }
    var gradYear by remember(profile) { mutableStateOf(profile.expectedGradYear.toString()) }
    var ieltsScoreText by remember(profile) { mutableStateOf(profile.entranceExamScores["IELTS"]?.toString() ?: "6.5") }
    var satScoreText by remember(profile) { mutableStateOf(profile.entranceExamScores["SAT"]?.toString() ?: (profile.entranceExamScores["JEE_MAIN"]?.toString() ?: "1450.0")) }
    var gpaText by remember(profile) { mutableStateOf(profile.entranceExamScores["GPA"]?.toString() ?: "88.5") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Edit Profile Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        label = { Text("Full Name") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )

                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("Bio & Aspirations") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )

                    OutlinedTextField(
                        value = targetDegree,
                        onValueChange = { targetDegree = it },
                        label = { Text("Target Degree (e.g. B.Tech, BS, MS)") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )

                    OutlinedTextField(
                        value = targetMajor,
                        onValueChange = { targetMajor = it },
                        label = { Text("Target Major / Specialization") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )

                    OutlinedTextField(
                        value = targetUniversities,
                        onValueChange = { targetUniversities = it },
                        label = { Text("Target Universities") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )

                    OutlinedTextField(
                        value = highSchool,
                        onValueChange = { highSchool = it },
                        label = { Text("Current School / Institution") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )

                    OutlinedTextField(
                        value = gradYear,
                        onValueChange = { gradYear = it },
                        label = { Text("Target Graduation Year") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )

                    Text(
                        text = "Academic & Test Scores",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = ieltsScoreText,
                            onValueChange = { ieltsScoreText = it },
                            label = { Text("IELTS Band") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                        OutlinedTextField(
                            value = gpaText,
                            onValueChange = { gpaText = it },
                            label = { Text("Standing %") },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }

                    OutlinedTextField(
                        value = satScoreText,
                        onValueChange = { satScoreText = it },
                        label = { Text("Entrance Exam / SAT Score") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    )

                    if (saveSuccess) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Profile updated successfully!", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val yr = gradYear.toIntOrNull() ?: 2027
                            val scores = mutableMapOf<String, Double>()
                            ieltsScoreText.toDoubleOrNull()?.let { scores["IELTS"] = it }
                            satScoreText.toDoubleOrNull()?.let { scores["SAT"] = it }
                            gpaText.toDoubleOrNull()?.let { scores["GPA"] = it }
                            onSave(fullName, bio, targetDegree, targetMajor, targetUniversities, highSchool, yr, scores)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save Changes", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
