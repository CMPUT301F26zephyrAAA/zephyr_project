package com.example.zephyrcampsites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

// Hiral - [AI-03] OpenAI, ChatGPT, US-03.09.01
/**
 * Displays a list of ratings for a campsite.
 *
 * @param campsiteName Name of the campsite.
 * @param ratings List of ratings.
 * @param loadError Error message, if any.
 * @param removeRating Called when a rating is removed.
 * @param onBack Called when BACK is selected.
 */
@Composable
internal fun AdminRatingsScreen(
    campsiteName: String,
    ratings: List<CampsiteRating>,
    loadError: String?,
    removeRating: (CampsiteRating, (Boolean) -> Unit, (String) -> Unit) -> Unit,
    onBack: () -> Unit,
) {
    var selected by remember { mutableStateOf<CampsiteRating?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    var successCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(successCount) {
        if (successCount > 0) {
            delay(10_000)
            successCount = 0
        }
    }

    BackHandler {
        if (!deleting) onBack()
    }

    Scaffold(
        bottomBar = {
            Column(
                modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (successCount > 0) {
                    Text("Rating Deleted", style = MaterialTheme.typography.bodySmall)
                }

                OutlinedButton(
                    onClick = onBack,
                    enabled = !deleting,
                    modifier = Modifier.fillMaxWidth().testTag("AdminRatingsBack"),
                ) {
                    Text("BACK")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Campsite ratings", style = MaterialTheme.typography.headlineMedium)
            Text(campsiteName)

            (loadError ?: deleteError)?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error)
            }
            if (deleting) Text("Deleting rating…")
            if (ratings.isEmpty() && loadError == null) {
                Text(
                    "No ratings available for this campsite.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().testTag("AdminRatingsList"),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(ratings, key = { requireNotNull(it.accountId) }) { rating ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(if (rating.score in 1..5) "${rating.score} / 5" else "N/A")
                            if (rating.review.isNotBlank()) Text(rating.review)
                        }

                        IconButton(
                            onClick = { selected = rating },
                            enabled = !deleting && loadError == null,
                            modifier = Modifier.testTag("RemoveRating_${rating.accountId}"),
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove Rating")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    selected?.let { rating ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Remove Rating?") },
            text = { Text("Are you sure you want to delete this rating?") },
            dismissButton = {
                TextButton(onClick = { selected = null }) {
                    Text("No, Cancel")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !deleting && loadError == null,
                    onClick = {
                        selected = null
                        deleting = true
                        deleteError = null
                        removeRating(
                            rating,
                            { removed ->
                                deleting = false
                                if (removed) {
                                    successCount++
                                } else {
                                    deleteError = "This rating has already been removed."
                                }
                            },
                            { message ->
                                deleting = false
                                deleteError = message
                            },
                        )
                    },
                ) {
                    Text("Yes, delete")
                }
            },
        )
    }
}
