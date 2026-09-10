package com.campusverse.app.ui.screens.student.marketplace

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
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
fun CreateListingScreen(
    onNavigateBack: () -> Unit,
    viewModel: MarketplaceViewModel,
    modifier: Modifier = Modifier
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("TEXTBOOK") }
    var selectedCondition by remember { mutableStateOf("LIKE_NEW") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSubmitting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CampusVerseTopBar(
                title = "List Item for Sale",
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
                text = "Sell to other verified students on campus",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage != null) {
                CampusVerseErrorMessage(message = errorMessage!!)
                Spacer(modifier = Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; errorMessage = null },
                label = { Text("Item Title *") },
                placeholder = { Text("e.g. Casio FX-991EX Scientific Calculator") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = priceText,
                onValueChange = { priceText = it },
                label = { Text("Price in INR (₹) *") },
                placeholder = { Text("650") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text("Category:", style = MaterialTheme.typography.labelLarge)
            val categories = listOf("TEXTBOOK", "ELECTRONICS", "NOTES", "FURNITURE", "OTHER")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                items(categories) { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Text("Condition:", style = MaterialTheme.typography.labelLarge)
            val conditions = listOf("NEW", "LIKE_NEW", "GOOD", "FAIR")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                items(conditions) { cond ->
                    FilterChip(
                        selected = selectedCondition == cond,
                        onClick = { selectedCondition = cond },
                        label = { Text(cond) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description *") },
                placeholder = { Text("Provide details about usage, edition, working state...") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3
            )
            Spacer(modifier = Modifier.height(24.dp))

            CampusVerseButton(
                text = if (isSubmitting) "Listing..." else "Post Listing",
                onClick = {
                    val price = priceText.toDoubleOrNull()
                    if (title.isBlank()) {
                        errorMessage = "Please enter an item title."
                        return@CampusVerseButton
                    }
                    if (price == null || price <= 0) {
                        errorMessage = "Please enter a valid price."
                        return@CampusVerseButton
                    }
                    if (description.isBlank()) {
                        errorMessage = "Please enter an item description."
                        return@CampusVerseButton
                    }

                    isSubmitting = true
                    viewModel.createListing(
                        title = title.trim(),
                        description = description.trim(),
                        price = price,
                        category = selectedCategory,
                        condition = selectedCondition,
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
