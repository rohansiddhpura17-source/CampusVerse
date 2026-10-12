package com.campusverse.app.ui.screens.student.questionbank

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClearAll
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.campusverse.app.domain.student.questionbank.QuestionBankQuestion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionBankScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuestionBankViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val subjects = remember(state.questions) { state.questions.map { it.subject }.distinct().sorted() }
    val courses = remember(state.questions) { state.questions.map { it.course }.distinct().sorted() }
    val units = remember(state.questions) { state.questions.map { it.unit }.distinct().sorted() }
    val topics = remember(state.questions) { state.questions.map { it.topic }.distinct().sorted() }
    val difficulties = remember(state.questions) { state.questions.map { it.difficulty.label }.distinct().sorted() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Question Bank") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::clearFilters) {
                        Icon(Icons.Outlined.ClearAll, contentDescription = "Clear search and filters")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Find a question, review its answer, and revise one topic at a time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Demo question set · Not synced to a live course API",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::setSearchQuery,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search question, answer, topic, subject…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) }
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterDropdown("Subject", state.filters.subject, subjects, viewModel::setSubject)
                FilterDropdown("Course", state.filters.course, courses, viewModel::setCourse)
                FilterDropdown("Unit", state.filters.unit, units, viewModel::setUnit)
                FilterDropdown("Topic", state.filters.topic, topics, viewModel::setTopic)
                FilterDropdown("Difficulty", state.filters.difficulty, difficulties, viewModel::setDifficulty)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    state.visibleQuestions.size.toString() + " questions",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = viewModel::clearFilters) { Text("Reset filters") }
            }
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                state.errorMessage != null -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(state.errorMessage ?: "Unable to load questions.", color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::loadQuestions) { Text("Retry") }
                }
                state.visibleQuestions.isEmpty() -> Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No questions match your search.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Try another term or reset the filters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = viewModel::clearFilters) { Text("Clear search and filters") }
                }
                else -> LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.visibleQuestions, key = { it.id }) { question ->
                        QuestionCard(
                            question = question,
                            expanded = question.id in state.expandedQuestionIds,
                            onToggle = { viewModel.toggleExpanded(question.id) }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun FilterDropdown(
    label: String,
    selectedValue: String?,
    options: List<String>,
    onSelect: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedValue ?: label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("All " + label) },
                onClick = { onSelect(null); expanded = false }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(
    question: QuestionBankQuestion,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    question.subject,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        question.difficulty.label,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                question.questionText,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                question.course + " · " + question.unit + " · " + question.topic,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onToggle, modifier = Modifier.align(Alignment.Start)) {
                Text(if (expanded) "Hide answer" else "Show answer")
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null
                )
            }
            if (expanded) {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("Answer", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(question.answerText, style = MaterialTheme.typography.bodyMedium)
                        question.explanation?.takeIf { it.isNotBlank() }?.let { explanation ->
                            Spacer(Modifier.height(10.dp))
                            Text("Explanation", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(explanation, style = MaterialTheme.typography.bodyMedium)
                        }
                        question.codeExample?.takeIf { it.isNotBlank() }?.let { code ->
                            Spacer(Modifier.height(10.dp))
                            Text("Example", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(code, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        }
                        question.sourceReference?.takeIf { it.isNotBlank() }?.let { source ->
                            Spacer(Modifier.height(8.dp))
                            Text("Reference: " + source, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
