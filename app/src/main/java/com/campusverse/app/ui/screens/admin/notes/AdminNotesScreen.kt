package com.campusverse.app.ui.screens.admin.notes

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.campusverse.app.data.model.NoteItem
import com.campusverse.app.ui.theme.AdminTheme

/**
 * ADM — Notes Moderation Screen
 * Platform administration screen for reviewing student-uploaded notes, approving,
 * rejecting, removing, restoring, and permanently deleting notes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminNotesScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdminNotesViewModel = viewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedNoteForReject by remember { mutableStateOf<NoteItem?>(null) }
    var rejectReason by remember { mutableStateOf("") }

    var selectedNoteForRemove by remember { mutableStateOf<NoteItem?>(null) }
    var removeReason by remember { mutableStateOf("") }

    var selectedNoteForDelete by remember { mutableStateOf<NoteItem?>(null) }

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is AdminNotesUiState.Success && state.feedbackMessage != null) {
            snackbarHostState.showSnackbar(state.feedbackMessage)
            viewModel.clearFeedbackMessage()
        }
    }

    // Rejection Dialog
    if (selectedNoteForReject != null) {
        AlertDialog(
            onDismissRequest = {
                selectedNoteForReject = null
                rejectReason = ""
            },
            title = { Text("Reject Study Note") },
            text = {
                Column {
                    Text("Provide a reason for rejecting '${selectedNoteForReject?.title}':")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = rejectReason,
                        onValueChange = { rejectReason = it },
                        placeholder = { Text("e.g. Inappropriate content, copyright infringement, incomplete material") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (rejectReason.isNotBlank() && selectedNoteForReject != null) {
                            viewModel.moderateNote(selectedNoteForReject!!.id, "REJECT", rejectReason.trim())
                            selectedNoteForReject = null
                            rejectReason = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    enabled = rejectReason.isNotBlank()
                ) { Text("Reject Note") }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedNoteForReject = null
                    rejectReason = ""
                }) { Text("Cancel") }
            }
        )
    }

    // Removal Dialog
    if (selectedNoteForRemove != null) {
        AlertDialog(
            onDismissRequest = {
                selectedNoteForRemove = null
                removeReason = ""
            },
            title = { Text("Remove Published Note") },
            text = {
                Column {
                    Text("Provide a reason for removing '${selectedNoteForRemove?.title}' from the catalog:")
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = removeReason,
                        onValueChange = { removeReason = it },
                        placeholder = { Text("e.g. Obsolete curriculum, duplicate material") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedNoteForRemove != null) {
                            viewModel.moderateNote(selectedNoteForRemove!!.id, "REMOVE", removeReason.trim().ifBlank { null })
                            selectedNoteForRemove = null
                            removeReason = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Confirm Removal") }
            },
            dismissButton = {
                TextButton(onClick = {
                    selectedNoteForRemove = null
                    removeReason = ""
                }) { Text("Cancel") }
            }
        )
    }

    // Permanent Delete Confirmation Dialog
    if (selectedNoteForDelete != null) {
        AlertDialog(
            onDismissRequest = { selectedNoteForDelete = null },
            title = { Text("Permanently Delete Note?") },
            text = {
                Text("Are you sure you want to permanently delete '${selectedNoteForDelete?.title}'? This action cannot be undone and will be logged in the audit log.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (selectedNoteForDelete != null) {
                            viewModel.deletePermanently(selectedNoteForDelete!!.id)
                            selectedNoteForDelete = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete Permanently") }
            },
            dismissButton = {
                TextButton(onClick = { selectedNoteForDelete = null }) { Text("Cancel") }
            }
        )
    }

    AdminTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.MenuBook,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Notes Moderation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.loadNotes() }) {
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
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            when (val state = uiState) {
                is AdminNotesUiState.Loading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is AdminNotesUiState.Error -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { viewModel.loadNotes() }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                is AdminNotesUiState.Success -> {
                    val statusFilters = listOf(
                        "ALL" to "All",
                        "PENDING_REVIEW" to "Pending Review",
                        "PUBLISHED" to "Published",
                        "REJECTED" to "Rejected",
                        "REMOVAL_REQUESTED" to "Removal Requests",
                        "REMOVED" to "Removed"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Status Filter Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            statusFilters.forEach { (statusKey, label) ->
                                val isSelected = state.selectedStatus == statusKey
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onStatusFilterSelected(statusKey) },
                                    label = { Text(label) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                            }
                        }

                        // Search Field
                        OutlinedTextField(
                            value = state.searchQuery,
                            onValueChange = { viewModel.searchNotes(it) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            placeholder = { Text("Search by title, author, or subject...") },
                            leadingIcon = {
                                Icon(Icons.Outlined.Search, contentDescription = null)
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        // Notes Counter
                        Text(
                            text = "Showing ${state.notes.size} notes",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )

                        // Notes List
                        if (state.notes.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No notes found for current filter.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.notes, key = { it.id }) { note ->
                                    AdminNoteCard(
                                        note = note,
                                        onApprove = { viewModel.moderateNote(note.id, "APPROVE") },
                                        onReject = { selectedNoteForReject = note },
                                        onRemove = { selectedNoteForRemove = note },
                                        onRestore = { viewModel.moderateNote(note.id, "RESTORE") },
                                        onDeletePermanently = { selectedNoteForDelete = note }
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(32.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminNoteCard(
    note: NoteItem,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onRemove: () -> Unit,
    onRestore: () -> Unit,
    onDeletePermanently: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Title & Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = note.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Author: ${note.authorName} (${note.courseCode ?: "General"})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                val (badgeText, badgeColor, badgeBg) = when (note.status) {
                    "PENDING_REVIEW" -> Triple("Pending Review", Color(0xFFE65100), Color(0xFFFFF3E0))
                    "PUBLISHED" -> Triple("Published", Color(0xFF2E7D32), Color(0xFFE8F5E9))
                    "REJECTED" -> Triple("Rejected", Color(0xFFC62828), Color(0xFFFFEBEE))
                    "REMOVAL_REQUESTED" -> Triple("Removal Requested", Color(0xFFE65100), Color(0xFFFFF8E1))
                    "REMOVED" -> Triple("Removed", Color(0xFF424242), Color(0xFFEEEEEE))
                    else -> Triple(note.status, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (!note.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = note.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Reason displays
            if (note.status == "REJECTED" && !note.rejectionReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Rejection Reason: ${note.rejectionReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (note.status == "REMOVAL_REQUESTED" && !note.removalReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFFFF8E1),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Student Removal Reason: ${note.removalReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE65100),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (note.status == "REMOVED" && !note.removalReason.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFEEEEEE),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Removal Reason: ${note.removalReason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF424242),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons based on status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (note.status) {
                    "PENDING_REVIEW" -> {
                        OutlinedButton(
                            onClick = onReject,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reject")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve")
                        }
                    }
                    "PUBLISHED" -> {
                        OutlinedButton(
                            onClick = onRemove,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }
                    "REMOVAL_REQUESTED" -> {
                        OutlinedButton(
                            onClick = onApprove, // Reject removal request by re-approving / keeping note
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Text("Keep Note")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onRemove, // Approve removal
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Outlined.RemoveCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Approve Removal")
                        }
                    }
                    "REJECTED", "REMOVED" -> {
                        OutlinedButton(
                            onClick = onRestore,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Outlined.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onDeletePermanently,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Icon(Icons.Outlined.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }
    }
}
