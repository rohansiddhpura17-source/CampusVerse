package com.campusverse.app.ui.screens.admin.reports

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusverse.app.data.model.AdminReportItem
import com.campusverse.app.navigation.Screen
import com.campusverse.app.ui.screens.admin.components.AdminBottomBar
import com.campusverse.app.ui.theme.AdminTheme

/**
 * ADM — Reports & Moderation Screen
 * Safety report investigations, warning issuance, content removals, and resolutions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminReportsScreen(
    onNavigateToDashboard: () -> Unit,
    onNavigateToUsers: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: AdminReportsViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    AdminTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Safety & Moderation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    actions = {
                        IconButton(onClick = { viewModel.loadReports() }) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = {
                AdminBottomBar(
                    currentRoute = Screen.AdminReports.route,
                    onNavigateToDashboard = onNavigateToDashboard,
                    onNavigateToUsers = onNavigateToUsers,
                    onNavigateToReports = {},
                    onNavigateToSettings = onNavigateToSettings
                )
            },
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            when (val state = uiState) {
                is AdminReportsUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.secondary)
                    }
                }
                is AdminReportsUiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.loadReports() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is AdminReportsUiState.Success -> {
                    ReportsContent(
                        state = state,
                        paddingValues = innerPadding,
                        onStatusFilter = { viewModel.onStatusFilterSelected(it) },
                        onTargetFilter = { viewModel.onTargetTypeFilterSelected(it) },
                        onStartReview = { viewModel.onStartReview(it) },
                        onResolve = { id, st, act, n -> viewModel.resolveReport(id, st, act, n) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReportsContent(
    state: AdminReportsUiState.Success,
    paddingValues: PaddingValues,
    onStatusFilter: (String) -> Unit,
    onTargetFilter: (String) -> Unit,
    onStartReview: (AdminReportItem?) -> Unit,
    onResolve: (String, String, String, String?) -> Unit
) {
    var resolutionNotesText by remember { mutableStateOf("") }
    var selectedAction by remember { mutableStateOf("WARN") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        if (!state.feedbackMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = state.feedbackMessage,
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Target type filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val targets = listOf("ALL", "USER", "MARKETPLACE_ITEM", "COMMUNITY_POST", "MENTORSHIP")
            targets.forEach { target ->
                FilterChip(
                    selected = state.selectedTargetType == target,
                    onClick = { onTargetFilter(target) },
                    label = { Text(target.replace('_', ' ')) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                        selectedLabelColor = MaterialTheme.colorScheme.secondary
                    )
                )
            }
        }

        if (state.reports.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No safety reports matching current filter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.reports) { report ->
                    ReportCard(
                        report = report,
                        onReviewClick = { onStartReview(report) }
                    )
                }
            }
        }
    }

    // Resolution Modal Dialog
    if (state.reviewingReport != null) {
        val rep = state.reviewingReport
        AlertDialog(
            onDismissRequest = { onStartReview(null) },
            title = {
                Text(text = "Resolve Report: ${rep.targetType.replace('_', ' ')}", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(text = "Reporter: ${rep.reporterName} (${rep.reporterEmail})", style = MaterialTheme.typography.bodySmall)
                    Text(text = "Reason: ${rep.reason}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text(text = "Action To Apply:", style = MaterialTheme.typography.labelMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val actions = listOf("WARN", "REMOVE", "SUSPEND", "DISMISS", "RESOLVE")
                        actions.forEach { act ->
                            FilterChip(
                                selected = selectedAction == act,
                                onClick = { selectedAction = act },
                                label = { Text(act) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (act == "REMOVE" || act == "SUSPEND") MaterialTheme.colorScheme.error.copy(alpha = 0.15f) else MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                                    selectedLabelColor = if (act == "REMOVE" || act == "SUSPEND") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = resolutionNotesText,
                        onValueChange = { resolutionNotesText = it },
                        label = { Text("Resolution Notes") },
                        placeholder = { Text("Reason or action record...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalStatus = if (selectedAction == "DISMISS") "DISMISSED" else "RESOLVED"
                        onResolve(rep.id, finalStatus, selectedAction, resolutionNotesText)
                        resolutionNotesText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Apply & Close")
                }
            },
            dismissButton = {
                TextButton(onClick = { onStartReview(null) }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ReportCard(
    report: AdminReportItem,
    onReviewClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = report.targetType.replace('_', ' '),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Reported by ${report.reporterName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when (report.status) {
                        "RESOLVED" -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                        "DISMISSED" -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    }
                ) {
                    Text(
                        text = report.status,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = when (report.status) {
                            "RESOLVED" -> MaterialTheme.colorScheme.tertiary
                            "DISMISSED" -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Reason: ${report.reason}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!report.resolutionNotes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Resolution: ${report.resolutionNotes}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = report.createdAt.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onReviewClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Investigate", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
