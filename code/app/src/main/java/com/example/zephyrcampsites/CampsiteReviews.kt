package com.example.zephyrcampsites

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.example.zephyrcampsites.ui.theme.ProfileMenuGreen

/**
 * Shows saved reviews and allows an entrant with a completed stay to write one.
 *
 * @param campsiteId Campsite whose reviews are displayed.
 * @param account Current entrant.
 * @param rating Rating data with the verified reservation, or null if ineligible.
 * @param controller Loads and saves written reviews.
 */
@Composable
internal fun CampsiteReviews(
    campsiteId: String, account: Account, rating: CampsiteRating?, controller: RatingController
) {
    var reviews by remember(campsiteId) { mutableStateOf<List<String>?>(null) }
    var loadError by remember(campsiteId) { mutableStateOf<String?>(null) }
    var editing by rememberSaveable(campsiteId, account.id) { mutableStateOf(false) }
    var draft by rememberSaveable(campsiteId, account.id) { mutableStateOf<String?>(null) }
    var saving by remember(campsiteId, account.id) { mutableStateOf(false) }
    var saved by remember(campsiteId, account.id) { mutableStateOf(false) }
    var saveError by remember(campsiteId, account.id) { mutableStateOf<String?>(null) }

    DisposableEffect(campsiteId, controller) {
        val listener = controller.observeReviews(campsiteId, { reviews = it }, { loadError = it })
        onDispose { listener.remove() }
    }

    Text(stringResource(R.string.campsite_reviews), style = MaterialTheme.typography.titleMedium)
    if (rating != null && !editing) {
        Button(
            onClick = {
                if (draft == null) draft = rating.review
                saved = false
                editing = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = ProfileMenuGreen, contentColor = Color.White)
        ) { Text(stringResource(R.string.campsite_write_review)) }
    }

    if (rating != null && editing) {
        val text = draft.orEmpty()
        OutlinedTextField(
            value = text,
            onValueChange = { draft = it },
            label = { Text(stringResource(R.string.campsite_review_text)) },
            modifier = Modifier.fillMaxWidth().testTag("CampsiteReviewText"),
            enabled = !saving,
            minLines = 3
        )
        Button(
            enabled = !saving && text.isNotBlank(),
            onClick = {
                saving = true
                saveError = null
                controller.submitReview(campsiteId, account, rating.reservationId, text,
                    onSuccess = {
                        saving = false
                        saved = true
                        editing = false
                    },
                    onError = {
                        saving = false
                        saveError = it
                    }
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = ProfileMenuGreen, contentColor = Color.White)
        ) { Text(stringResource(if (saving) R.string.rating_saving else R.string.campsite_submit_review)) }
    }

    if (saved) Text(stringResource(R.string.campsite_review_saved))
    saveError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    loadError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    reviews?.let { entries ->
        if (entries.isEmpty()) Text(stringResource(R.string.campsite_reviews_empty))
        entries.forEach { review -> Text(review) }
    }
}
