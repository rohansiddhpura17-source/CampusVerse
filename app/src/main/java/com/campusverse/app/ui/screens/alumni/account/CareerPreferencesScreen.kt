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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusverse.app.data.model.CareerPreferenceData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareerPreferencesScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: CareerPreferencesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Career Preferences", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
                is CareerPreferencesUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is CareerPreferencesUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = state.message, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadPreferences() }) { Text("Retry") }
                    }
                }
                is CareerPreferencesUiState.Success -> {
                    val p = state.preferences
                    var rolesText by remember(p) { mutableStateOf(p.preferredRoles.joinToString(", ")) }
                    var locsText by remember(p) { mutableStateOf(p.preferredLocations.joinToString(", ")) }
                    var targetSalary by remember(p) { mutableStateOf(p.targetSalary) }
                    var remotePref by remember(p) { mutableStateOf(p.remotePreference) }
                    var jobAlerts by remember(p) { mutableStateOf(p.jobAlerts) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
                    ) {
                        if (!state.saveMessage.isNullOrBlank()) {
                            item {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.saveMessage,
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
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(text = "Target Roles & Locations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                    OutlinedTextField(
                                        value = rolesText,
                                        onValueChange = { rolesText = it },
                                        label = { Text("Preferred Roles (Comma-separated)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = locsText,
                                        onValueChange = { locsText = it },
                                        label = { Text("Preferred Locations (Comma-separated)") },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    OutlinedTextField(
                                        value = targetSalary,
                                        onValueChange = { targetSalary = it },
                                        label = { Text("Target Compensation Range") },
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Text(text = "Workplace Model Preference", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf("REMOTE" to "Remote", "HYBRID" to "Hybrid", "ONSITE" to "On-site", "ANY" to "Any").forEach { (key, label) ->
                                            FilterChip(
                                                selected = remotePref == key,
                                                onClick = { remotePref = key },
                                                label = { Text(label) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Email & Push Job Alerts", style = MaterialTheme.typography.bodyMedium)
                                        Switch(checked = jobAlerts, onCheckedChange = { jobAlerts = it }, colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary))
                                    }
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    val rList = rolesText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    val lList = locsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    viewModel.savePreferences(
                                        p.copy(
                                            preferredRoles = rList,
                                            preferredLocations = lList,
                                            targetSalary = targetSalary,
                                            remotePreference = remotePref,
                                            jobAlerts = jobAlerts
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Save Preferences")
                            }
                        }
                    }
                }
            }
        }
    }
}
