// Displays campsite ratings and reviews for administrator moderation.
package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class CampsiteFeedback(val accountId: String, val score: Long?, val review: String)

/** Connects the moderation screen to the campsite feedback model. */
internal class AdminCampsiteFeedbackController(private val model: RatingModel = RatingModel()) {
    fun observe(campsiteId: String, onResult: (List<CampsiteFeedback>) -> Unit, onError: (String) -> Unit) =
        model.observeFeedback(campsiteId, onResult, onError)

    fun remove(campsiteId: String, accountId: String, onError: (String) -> Unit) =
        model.removeFeedback(campsiteId, accountId, onError)
}

// AI Citation [AI-39] (Bhav Saluja). Verified admin feedback removal. See the team AI Documentation Google Doc.
@Composable
internal fun AdminCampsiteFeedback(campsiteId: String) {
    val controller = remember { AdminCampsiteFeedbackController() }
    var feedback by remember(campsiteId) { mutableStateOf<List<CampsiteFeedback>>(emptyList()) }
    var error by remember(campsiteId) { mutableStateOf<String?>(null) }
    var selected by remember(campsiteId) { mutableStateOf<CampsiteFeedback?>(null) }
    DisposableEffect(campsiteId, controller) {
        val listener = controller.observe(campsiteId, { feedback = it }, { error = it })
        onDispose { listener.remove() }
    }

    Column(Modifier.padding(horizontal = 20.dp)) {
        Text("Ratings and reviews", style = MaterialTheme.typography.titleMedium)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        feedback.forEach { entry ->
            entry.score?.let { Text("Rating: $it out of 5") }
            if (entry.review.isNotBlank()) {
                Text(entry.review)
            }
            TextButton(onClick = { selected = entry }) { Text("Remove feedback") }
        }
    }
    selected?.let { entry ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Remove this feedback?") },
            text = { Text("This removes the submitted rating and written review.") },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    error = null
                    controller.remove(campsiteId, entry.accountId) { error = it }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Cancel") } }
        )
    }
}
