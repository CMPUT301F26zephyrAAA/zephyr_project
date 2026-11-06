package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.zephyrcampsites.ui.theme.ProfileMenuGreen

/**
 * Offers and edits ratings only after a completed stay has been verified.
 * The past-reservation screen must link to the rating route when that screen is implemented.
 */
class RatingView {
    /**
     * Shows the rating entry only when the entrant has a qualifying reservation.
     *
     * @param campsiteId Campsite whose completed stays are checked.
     * @param account Current entrant account.
     * @param controller Controller used to check eligibility.
     * @param onRate Opens the rating screen for the campsite.
     */
    @Composable
    fun RatingEntry(campsiteId: String, account: Account, controller: RatingController, onRate: (String) -> Unit) {
        var eligible by remember(campsiteId, account.id) { mutableStateOf(false) }
        var error by remember(campsiteId, account.id) { mutableStateOf<String?>(null) }
        var retry by remember { mutableStateOf(0) }
        DisposableEffect(campsiteId, account.id, retry) {
            var active = true
            eligible = false
            error = null
            controller.loadRating(campsiteId, account,
                onResult = { if (active) { eligible = it != null } },
                onError = { if (active) { error = it } }
            )
            onDispose { active = false }
        }
        if (eligible) {
            Button(onClick = { onRate(campsiteId) }, shape = RoundedCornerShape(2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ProfileMenuGreen, contentColor = Color.White),
                modifier = Modifier.testTag("RateCampsiteEntry")) {
                Text(stringResource(R.string.rating_title))
            }
        }
        val loadError = error
        if (loadError != null) {
            Text(loadError, color = MaterialTheme.colorScheme.error)
            Button(onClick = { retry++ }) { Text(stringResource(R.string.rating_retry)) }
        }
    }

    /**
     * Loads the saved rating, handles submissions, and returns only after a successful save.
     *
     * @param campsite Campsite being rated, or null if it is no longer available.
     * @param account Current entrant account.
     * @param controller Controller used to load and save the rating.
     * @param onSaved Returns to the previous screen after a successful save.
     * @param onBack Returns without submitting changes.
     */
    @Composable
    fun RatingScreen(
        campsite: Campsite?, account: Account?, controller: RatingController,
        onSaved: () -> Unit, onBack: () -> Unit
    ) {
        var rating by remember(campsite?.id, account?.id) { mutableStateOf<CampsiteRating?>(null) }
        var loading by remember(campsite?.id, account?.id) { mutableStateOf(true) }
        var saving by remember(campsite?.id, account?.id) { mutableStateOf(false) }
        var error by remember(campsite?.id, account?.id) { mutableStateOf<String?>(null) }
        var retry by remember { mutableStateOf(0) }
        var active by remember(campsite?.id, account?.id) { mutableStateOf(true) }
        DisposableEffect(campsite?.id, account?.id, retry) {
            active = true
            loading = true
            error = null
            controller.loadRating(campsite?.id.orEmpty(), account,
                onResult = { result ->
                    if (active) {
                        rating = result
                        loading = false
                    }
                },
                onError = { message ->
                    if (active) {
                        error = message
                        loading = false
                    }
                }
            )
            onDispose { active = false }
        }
        RatingForm(
            campsiteName = campsite?.name.orEmpty(), rating = rating, loading = loading, saving = saving,
            error = error, onRetry = { retry++ }, onBack = onBack,
            onSubmit = { score, review ->
                val currentRating = rating
                if (currentRating != null && !saving) {
                    saving = true
                    error = null
                    controller.submitRating(campsite?.id.orEmpty(), account,
                        currentRating.copy(score = score, review = review),
                        onSuccess = {
                            if (active) {
                                saving = false
                                onSaved()
                            }
                        },
                        onError = { message ->
                            if (active) {
                                saving = false
                                error = message
                            }
                        }
                    )
                }
            }
        )
    }

    /**
     * Displays star selection, an optional review, and submission feedback.
     *
     * @param campsiteName Name shown above the rating controls.
     * @param rating Saved rating, or null when the entrant cannot rate.
     * @param loading Whether eligibility is still being loaded.
     * @param saving Whether a submission is pending.
     * @param error Current load or save error.
     * @param onRetry Retries loading eligibility after an error.
     * @param onSubmit Submits the selected score and optional review.
     * @param onBack Returns to the previous screen.
     */
    @Composable
    fun RatingForm(
        campsiteName: String, rating: CampsiteRating?, loading: Boolean = false, saving: Boolean = false,
        error: String? = null, onRetry: () -> Unit = {}, onSubmit: (Int, String) -> Unit, onBack: () -> Unit
    ) {
        var score by rememberSaveable(rating) { mutableStateOf(rating?.score ?: 0) }
        var review by rememberSaveable(rating) { mutableStateOf(rating?.review.orEmpty()) }
        Scaffold(
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (rating != null && !loading) {
                        Button(
                            onClick = { onSubmit(score, review) }, enabled = score in 1..5 && !saving,
                            modifier = Modifier.width(139.dp).height(47.dp).testTag("SubmitRating"),
                            shape = RoundedCornerShape(2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ProfileMenuGreen, contentColor = Color.White)
                        ) { Text(stringResource(if (saving) R.string.rating_saving else R.string.rating_submit)) }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    Button(
                        onClick = onBack, modifier = Modifier.width(104.dp).testTag("RatingBack"),
                        shape = RoundedCornerShape(4.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) { Text(stringResource(R.string.rating_back)) }
                }
            }
        ) { contentPadding ->
            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 8.dp)
                .testTag("RateCampsiteScreen")) {
                Text(stringResource(R.string.rating_title), fontSize = 12.sp, lineHeight = 18.sp)
                Text(campsiteName.ifBlank { stringResource(R.string.rating_missing_campsite) }, fontSize = 32.sp, lineHeight = 47.sp)
                Spacer(modifier = Modifier.height(12.dp))
                if (loading) {
                    Text(stringResource(R.string.rating_loading))
                } else if (rating == null) {
                    if (error == null) {
                        Text(stringResource(R.string.rating_not_eligible))
                    }
                } else {
                    Text(stringResource(R.string.rating_your_rating), color = Color(0xFF237B67), fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (value in 1..5) {
                            IconToggleButton(
                                checked = value <= score, onCheckedChange = { score = value }, enabled = !saving,
                                modifier = Modifier.size(48.dp).testTag("RatingStar$value")
                            ) {
                                Icon(if (value <= score) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = stringResource(R.string.rating_star_description, value),
                                    tint = Color(0xFFFF9500), modifier = Modifier.size(44.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(stringResource(R.string.rating_review_label), color = Color(0xFF237B67), fontSize = 16.sp)
                    TextField(
                        value = review, onValueChange = { review = it }, enabled = !saving,
                        placeholder = { Text(stringResource(R.string.rating_review_placeholder), fontSize = 14.sp) },
                        modifier = Modifier.fillMaxWidth().height(120.dp).testTag("RatingReview"),
                        shape = RoundedCornerShape(4.dp),
                        colors = TextFieldDefaults.colors(unfocusedContainerColor = Color(0xFFE8E8E8),
                            focusedContainerColor = Color(0xFFE8E8E8), focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent)
                    )
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("RatingError"))
                    if (rating == null) {
                        Button(onClick = onRetry) { Text(stringResource(R.string.rating_retry)) }
                    }
                }
            }
        }
    }
}
