package com.campusverse.app.ui.screens.alumni.account

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusverse.app.data.model.AlumniProfileData
import com.campusverse.app.navigation.Screen
import com.campusverse.app.ui.screens.alumni.components.AlumniBottomBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlumniMyProfileScreen(
    currentUser: com.campusverse.app.domain.auth.AuthenticatedUser? = null,
    onNavigate: (String) -> Unit,
    viewModel: AlumniMyProfileViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isEditing by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Alumni Profile", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                actions = {
                    IconButton(onClick = { onNavigate(Screen.AlumniSettings.route) }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        bottomBar = {
            AlumniBottomBar(
                currentRoute = Screen.AlumniProfile.route,
                onNavigate = onNavigate
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is AlumniMyProfileUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is AlumniMyProfileUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadMyProfile() }) { Text("Retry") }
                    }
                }
                is AlumniMyProfileUiState.Success -> {
                    MyProfileContent(
                        currentUser = currentUser,
                        profile = state.profile,
                        isEditing = isEditing,
                        isSaving = state.isSaving,
                        saveMessage = state.saveMessage,
                        onToggleEdit = { isEditing = !isEditing },
                        onSave = { updated ->
                            viewModel.saveProfile(updated)
                            isEditing = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MyProfileContent(
    currentUser: com.campusverse.app.domain.auth.AuthenticatedUser? = null,
    profile: AlumniProfileData,
    isEditing: Boolean,
    isSaving: Boolean,
    saveMessage: String?,
    onToggleEdit: () -> Unit,
    onSave: (AlumniProfileData) -> Unit
) {
    val resolvedName = profile.fullName.takeIf { it.isNotBlank() } ?: currentUser?.name ?: "Alumni Member"
    var fullName by remember(resolvedName) { mutableStateOf(resolvedName) }
    var headline by remember(profile) { mutableStateOf(profile.headline ?: "") }
    var bio by remember(profile) { mutableStateOf(profile.bio ?: "") }
    var company by remember(profile) { mutableStateOf(profile.company) }
    var designation by remember(profile) { mutableStateOf(profile.designation) }
    var industry by remember(profile) { mutableStateOf(profile.industry) }
    var yearsOfExpText by remember(profile) { mutableStateOf(profile.yearsOfExperience.toString()) }
    var degree by remember(profile) { mutableStateOf(profile.degree) }
    var gradYearText by remember(profile) { mutableStateOf(profile.graduationYear.toString()) }
    var skillsText by remember(profile) { mutableStateOf(profile.skills.joinToString(", ")) }
    var location by remember(profile) { mutableStateOf(profile.location ?: "") }
    var phone by remember(profile) { mutableStateOf(profile.phone ?: "") }
    var linkedin by remember(profile) { mutableStateOf(profile.linkedin ?: "") }
    var website by remember(profile) { mutableStateOf(profile.website ?: "") }
    var github by remember(profile) { mutableStateOf(profile.github ?: "") }
    var willingToMentor by remember(profile) { mutableStateOf(profile.willingToMentor) }
    var willingToRefer by remember(profile) { mutableStateOf(profile.willingToRefer) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
    ) {
        if (!saveMessage.isNullOrBlank()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = saveMessage,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = fullName.take(2).uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(text = fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(text = "$designation @ $company", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                Text(text = "$degree • Class of ${profile.graduationYear}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = onToggleEdit) {
                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        if (isEditing) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "Edit Profile Information", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                        OutlinedTextField(value = fullName, onValueChange = { fullName = it }, label = { Text("Full Name") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = headline, onValueChange = { headline = it }, label = { Text("Headline") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = designation, onValueChange = { designation = it }, label = { Text("Designation") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = company, onValueChange = { company = it }, label = { Text("Company") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = industry, onValueChange = { industry = it }, label = { Text("Industry") }, modifier = Modifier.fillMaxWidth())

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value = degree, onValueChange = { degree = it }, label = { Text("Degree") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = gradYearText, onValueChange = { gradYearText = it }, label = { Text("Graduation Year") }, modifier = Modifier.weight(1f))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(value = yearsOfExpText, onValueChange = { yearsOfExpText = it }, label = { Text("Years of Exp.") }, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("Location") }, modifier = Modifier.weight(1f))
                        }

                        OutlinedTextField(value = skillsText, onValueChange = { skillsText = it }, label = { Text("Skills (Comma-separated)") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = linkedin, onValueChange = { linkedin = it }, label = { Text("LinkedIn URL") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = website, onValueChange = { website = it }, label = { Text("Portfolio Website") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = github, onValueChange = { github = it }, label = { Text("GitHub Profile") }, modifier = Modifier.fillMaxWidth())
                        OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text("Bio") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Available to Mentor", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = willingToMentor, onCheckedChange = { willingToMentor = it }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.tertiary))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Open to Referrals", style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = willingToRefer, onCheckedChange = { willingToRefer = it }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary))
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(onClick = onToggleEdit, shape = RoundedCornerShape(8.dp)) { Text("Cancel") }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val yoe = yearsOfExpText.toIntOrNull() ?: profile.yearsOfExperience
                                    val gy = gradYearText.toIntOrNull() ?: profile.graduationYear
                                    val sList = skillsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    onSave(
                                        profile.copy(
                                            fullName = fullName,
                                            headline = headline,
                                            bio = bio,
                                            company = company,
                                            designation = designation,
                                            industry = industry,
                                            yearsOfExperience = yoe,
                                            degree = degree,
                                            graduationYear = gy,
                                            skills = sList,
                                            location = location,
                                            phone = phone,
                                            linkedin = linkedin,
                                            website = website,
                                            github = github,
                                            willingToMentor = willingToMentor,
                                            willingToRefer = willingToRefer
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Save Changes")
                            }
                        }
                    }
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(text = "About", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = profile.bio ?: "No bio added yet.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(text = "Campus Education", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = "${profile.institution} • ${profile.degree} (${profile.graduationYear})", style = MaterialTheme.typography.bodyMedium)
                        if (profile.skills.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = "Skills", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(text = profile.skills.joinToString(" • "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(30.dp)) }
    }
}
