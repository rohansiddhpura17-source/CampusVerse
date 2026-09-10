package com.campusverse.app.ui.screens.student.notes

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.campusverse.app.ui.components.CampusVerseButton
import com.campusverse.app.ui.components.CampusVerseErrorMessage
import com.campusverse.app.ui.components.CampusVerseTopBar

@Composable
fun CreateNoteScreen(
    onNavigateBack: () -> Unit,
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var fileUrl by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CampusVerseTopBar(
                title = "Upload Study Notes",
                canNavigateBack = true,
                onNavigateBack = onNavigateBack
            )
        },
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "Share with your Campus Peers",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                CampusVerseErrorMessage(message = errorMessage!!)
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; errorMessage = null },
                label = { Text("Note Title *") },
                placeholder = { Text("e.g. Distributed Systems Raft Consensus Notes") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (Optional)") },
                placeholder = { Text("Summary of topics covered, diagrams, key takeaways...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = fileUrl,
                onValueChange = { fileUrl = it },
                label = { Text("Document / PDF URL *") },
                placeholder = { Text("https://docs.campusverse.edu/notes/sample.pdf") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (Comma-separated)") },
                placeholder = { Text("distributed-systems, raft, semester-6, cse") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))

            CampusVerseButton(
                text = if (isSubmitting) "Publishing..." else "Publish Study Note",
                onClick = {
                    if (title.isBlank()) {
                        errorMessage = "Please enter a note title."
                        return@CampusVerseButton
                    }
                    val urlToUse = if (fileUrl.isNotBlank()) fileUrl.trim() else "https://docs.campusverse.edu/notes/sample.pdf"
                    isSubmitting = true
                    viewModel.createNote(
                        title = title.trim(),
                        description = description.ifBlank { null },
                        fileUrl = urlToUse,
                        tags = tags.ifBlank { null },
                        onSuccess = {
                            isSubmitting = false
                            onNavigateBack()
                        }
                    )
                },
                enabled = !isSubmitting
            )
        }
    }
}
