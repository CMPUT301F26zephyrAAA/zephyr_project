
package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Hiral - [AI-05] OpenAI, ChatGPT, Testing Admin Rating Deletion.
/**
 * Compose UI tests for admin rating moderation.
 *
 * Uses production composables with a fake rating gateway.
 * No actual Firebase documents are modified.
 */
@RunWith(AndroidJUnit4::class)
class RatingModerationUITest {

    @get:Rule
    val compose = createComposeRule()

    private val admin = Account(
        "admin-1",
        userType = listOf("admin")
    )

    private val entrant = Account(
        "entrant-1",
        userType = listOf("entrant")
    )

    private val owner = Account(
        "owner-1",
        userType = listOf("owner")
    )

    private val flaggedRating = CampsiteRating(
        reservationId = "stay-1",
        score = 1,
        review = "Policy violation",
        accountId = "entrant-1"
    )

    private val validRating = CampsiteRating(
        reservationId = "stay-2",
        score = 5,
        review = "Lovely campsite",
        accountId = "entrant-2"
    )

    private val campsite = Campsite("1").apply {
        name = "Pine Lake"
    }

    private val gateway = FakeRatingGateway(
        listOf(flaggedRating, validRating)
    )

    /**
     * AC1:
     * Admin selects a campsite from the browse list
     * and views individual ratings.
     */
    @Test
    fun adminCanBrowseCampsiteAndViewIndividualRatings() {

        val selected = mutableStateOf<Campsite?>(null)

        compose.setContent {

            if (selected.value == null) {

                AdminCampsiteListView(
                    admin,
                    listOf(campsite),
                    false,
                    null,
                    { _, result -> result(null) },
                    { selected.value = it },
                    {}
                )

            } else {

                CampsiteDetailView().DetailView(
                    selected.value,
                    { _, _ -> ImageBitmap(1, 1) },
                    { selected.value = null },
                    account = admin,
                    showAdminActions = true,
                    adminRatingGateway = gateway
                )
            }
        }

        compose.onNodeWithTag("AdminCampsite_1")
            .performClick()

        openRatings()

        compose.onNodeWithText("Policy violation")
            .assertIsDisplayed()

        compose.onNodeWithText("Lovely campsite")
            .assertIsDisplayed()
    }

    /**
     * AC1:
     * Every individual rating can be accessed by scrolling.
     */
    @Test
    fun adminCanScrollThroughAllIndividualRatings() {

        gateway.rows = (1..30).map { index ->

            CampsiteRating(
                reservationId = "stay-$index",
                score = 3,
                review = "Review $index",
                accountId = "user-$index"
            )
        }

        showDetails()

        openRatings()

        for (index in 0 until 30) {

            compose.onNodeWithTag("AdminRatingsList")
                .performScrollToIndex(index)

            compose.onNodeWithText(
                "Review ${index + 1}"
            ).assertIsDisplayed()
        }
    }

    /**
     * AC2:
     * Admin can select the individual rating
     * that violates policy.
     */
    @Test
    fun adminCanSelectPolicyViolatingRating() {

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.onNodeWithText("Yes, delete")
            .performClick()

        compose.runOnIdle {

            assertEquals(
                flaggedRating,
                gateway.removedRating
            )

            assertEquals(1, gateway.requests)

            assertTrue(
                gateway.rows.contains(validRating)
            )
        }
    }

    /**
     * AC3:
     * Only scored entries have a Remove Rating option.
     */
    @Test
    fun adminHasRemoveRatingOptionOnlyForScoredEntries() {
        val reviewOnly = CampsiteRating(
            reservationId = "stay-3",
            review = "Review without a score",
            accountId = "entrant-3"
        )
        gateway.rows = gateway.rows + reviewOnly

        showDetails()

        openRatings()

        compose.onNodeWithTag("RemoveRating_entrant-1")
            .assertIsDisplayed()
            .assertIsEnabled()

        compose.onNodeWithTag("RemoveRating_entrant-2")
            .assertIsDisplayed()
            .assertIsEnabled()

        compose.onNodeWithText(reviewOnly.review).assertIsDisplayed()
        compose.onNodeWithTag("RemoveRating_entrant-3").assertDoesNotExist()
    }

    /**
     * AC4:
     * Selecting Remove Rating displays confirmation
     * without immediately requesting deletion.
     */
    @Test
    fun removalRequiresConfirmation() {

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.onNodeWithText("Remove Rating?")
            .assertIsDisplayed()

        compose.onNodeWithText("No, Cancel")
            .assertIsDisplayed()

        compose.onNodeWithText("Yes, delete")
            .assertIsDisplayed()

        compose.runOnIdle {

            assertEquals(0, gateway.requests)

            assertEquals(2, gateway.rows.size)
        }
    }

    /**
     * AC4:
     * Cancelling preserves the rating.
     */
    @Test
    fun cancellingRemovalPreservesRating() {

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.onNodeWithText("No, Cancel")
            .performClick()

        compose.onNodeWithText("Remove Rating?")
            .assertDoesNotExist()

        compose.onNodeWithText("Policy violation")
            .assertIsDisplayed()

        compose.onNodeWithText("Lovely campsite")
            .assertIsDisplayed()

        compose.runOnIdle {

            assertEquals(0, gateway.requests)

            assertEquals(2, gateway.rows.size)
        }
    }

    /**
     * AC5:
     * Confirming requests removal of the selected rating.
     *
     * Firebase deletion is simulated by the fake gateway.
     */
    @Test
    fun confirmingRemovalRequestsSelectedRatingDeletion() {

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.onNodeWithText("Yes, delete")
            .performClick()

        compose.runOnIdle {

            assertEquals(1, gateway.requests)

            assertEquals(
                flaggedRating,
                gateway.removedRating
            )

            assertFalse(
                gateway.rows.contains(flaggedRating)
            )
        }

        compose.onNodeWithText("Policy violation")
            .assertDoesNotExist()

        compose.onNodeWithText("Lovely campsite")
            .assertIsDisplayed()
    }

    /**
     * AC7:
     * Removed ratings disappear immediately from the list.
     */
    @Test
    fun removedRatingDisappearsImmediately() {

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.onNodeWithText("Yes, delete")
            .performClick()

        compose.onNodeWithText("Policy violation")
            .assertDoesNotExist()

        compose.onNodeWithTag("RemoveRating_entrant-1")
            .assertDoesNotExist()

        compose.onNodeWithText("Lovely campsite")
            .assertIsDisplayed()

        compose.onNodeWithTag("RemoveRating_entrant-2")
            .assertIsDisplayed()
    }

    /**
     * AC7:
     * Removed ratings remain absent when the admin
     * returns to the details page and reopens ratings.
     */
    @Test
    fun removedRatingRemainsAbsentAfterReopeningList() {

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.onNodeWithText("Yes, delete")
            .performClick()

        compose.onNodeWithTag("AdminRatingsBack")
            .performClick()

        openRatings()

        compose.onNodeWithText("Policy violation")
            .assertDoesNotExist()

        compose.onNodeWithText("Lovely campsite")
            .assertIsDisplayed()
    }

    /**
     * Empty state:
     * View Ratings is not shown when there are no ratings.
     */
    @Test
    fun campsiteWithoutRatingsShowsEmptyMessage() {

        gateway.rows = emptyList()

        showDetails()

        compose.onNodeWithText(
            "No ratings available for this campsite."
        ).performScrollTo()
            .assertIsDisplayed()

        compose.onNodeWithTag("ViewCampsiteRatings")
            .assertDoesNotExist()
    }

    /**
     * Removing the final rating updates the empty state.
     */
    @Test
    fun removingFinalRatingShowsEmptyMessage() {

        gateway.rows = listOf(flaggedRating)

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.onNodeWithText("Yes, delete")
            .performClick()

        compose.onNodeWithText("Policy violation")
            .assertDoesNotExist()

        compose.onNodeWithTag("AdminRatingsBack")
            .performClick()

        compose.onNodeWithText(
            "No ratings available for this campsite."
        ).performScrollTo()
            .assertIsDisplayed()

        compose.onNodeWithTag("ViewCampsiteRatings")
            .assertDoesNotExist()
    }

    /**
     * Successful removal displays temporary feedback.
     */
    @Test
    fun successfulRemovalShowsRatingDeletedMessage() {

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.onNodeWithText("Yes, delete")
            .performClick()

        compose.onNodeWithText("Rating Deleted")
            .assertIsDisplayed()

        compose.onNodeWithTag("AdminRatingsBack")
            .assertIsDisplayed()
    }

    /**
     * Success feedback disappears after approximately
     * ten seconds of Compose clock time.
     */
    @Test
    fun ratingDeletedMessageDisappearsAfterTenSeconds() {

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.mainClock.autoAdvance = false

        try {

            compose.onNodeWithText("Yes, delete")
                .performClick()

            compose.mainClock.advanceTimeBy(100)

            compose.onNodeWithText("Rating Deleted")
                .assertIsDisplayed()

            compose.mainClock.advanceTimeBy(9000)

            compose.onNodeWithText("Rating Deleted")
                .assertIsDisplayed()

            compose.mainClock.advanceTimeBy(1100)

            compose.onNodeWithText("Rating Deleted")
                .assertDoesNotExist()

        } finally {

            compose.mainClock.autoAdvance = true
        }
    }

    /**
     * A failed removal preserves the rating.
     */
    @Test
    fun failedRemovalPreservesRating() {

        gateway.shouldFail = true

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.onNodeWithText("Yes, delete")
            .performClick()

        compose.onNodeWithText("Removal failed.")
            .assertIsDisplayed()

        compose.onNodeWithText("Policy violation")
            .assertIsDisplayed()

        compose.onNodeWithText("Rating Deleted")
            .assertDoesNotExist()

        compose.onNodeWithTag("RemoveRating_entrant-1")
            .assertIsEnabled()
    }

    /**
     * Admin can retry a previously failed removal.
     */
    @Test
    fun adminCanRetryFailedRatingRemoval() {

        gateway.shouldFail = true

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.onNodeWithText("Yes, delete")
            .performClick()

        compose.onNodeWithText("Removal failed.")
            .assertIsDisplayed()

        compose.runOnIdle {
            gateway.shouldFail = false
        }

        selectFlaggedRating()

        compose.onNodeWithText("Yes, delete")
            .performClick()

        compose.onNodeWithText("Policy violation")
            .assertDoesNotExist()

        compose.onNodeWithText("Rating Deleted")
            .assertIsDisplayed()

        compose.runOnIdle {
            assertEquals(2, gateway.requests)
        }
    }

    /**
     * AC8:
     * Entrants cannot access admin moderation.
     */
    @Test
    fun entrantCannotAccessRatingModeration() {

        showDetails(
            account = entrant,
            adminActions = true
        )

        compose.onNodeWithTag("ViewCampsiteRatings")
            .assertDoesNotExist()

        compose.onNodeWithTag("RemoveRating_entrant-1")
            .assertDoesNotExist()

        compose.runOnIdle {

            assertEquals(0, gateway.observations)
            assertEquals(0, gateway.requests)
        }
    }

    /**
     * AC8:
     * Owners cannot access admin moderation.
     */
    @Test
    fun ownerCannotAccessRatingModeration() {

        showDetails(
            account = owner,
            adminActions = true
        )

        compose.onNodeWithTag("ViewCampsiteRatings")
            .assertDoesNotExist()

        compose.onNodeWithTag("RemoveRating_entrant-1")
            .assertDoesNotExist()

        compose.runOnIdle {

            assertEquals(0, gateway.observations)
            assertEquals(0, gateway.requests)
        }
    }

    /**
     * AC8:
     * Accounts with entrant and owner roles but no admin
     * role cannot access moderation.
     */
    @Test
    fun multipleNonAdminRolesCannotRemoveRatings() {

        val account = Account(
            "multiple-roles",
            userType = listOf("entrant", "owner")
        )

        showDetails(
            account = account,
            adminActions = true
        )

        compose.onNodeWithTag("ViewCampsiteRatings")
            .assertDoesNotExist()

        compose.runOnIdle {

            assertEquals(0, gateway.observations)
            assertEquals(0, gateway.requests)
        }
    }

    /**
     * AC8:
     * An account containing the admin role can moderate.
     */
    @Test
    fun accountContainingAdminRoleCanModerate() {

        val account = Account(
            "multiple-admin-roles",
            userType = listOf(
                "entrant",
                "owner",
                "admin"
            )
        )

        showDetails(
            account = account,
            adminActions = true
        )

        openRatings()

        compose.onNodeWithTag("RemoveRating_entrant-1")
            .assertIsDisplayed()
            .assertIsEnabled()
    }

    /**
     * Admin moderation is unavailable when admin actions
     * are disabled for the current navigation context.
     */
    @Test
    fun adminActionsMustBeEnabledForModeration() {

        showDetails(
            account = admin,
            adminActions = false
        )

        compose.onNodeWithTag("ViewCampsiteRatings")
            .assertDoesNotExist()

        compose.runOnIdle {

            assertEquals(0, gateway.observations)
        }
    }

    /**
     * An unauthenticated user cannot access moderation.
     */
    @Test
    fun unauthenticatedUserCannotModerateRatings() {

        showDetails(
            account = null,
            adminActions = true
        )

        compose.onNodeWithTag("ViewCampsiteRatings")
            .assertDoesNotExist()

        compose.runOnIdle {

            assertEquals(0, gateway.observations)
            assertEquals(0, gateway.requests)
        }
    }

    /**
     * The Back button remains visible while the
     * ratings LazyColumn is scrolled.
     */
    @Test
    fun backButtonRemainsVisibleWhileScrolling() {

        gateway.rows = (1..40).map { index ->

            CampsiteRating(
                reservationId = "stay-$index",
                score = 3,
                review = "Review $index",
                accountId = "user-$index"
            )
        }

        showDetails()

        openRatings()

        compose.onNodeWithTag("AdminRatingsList")
            .performScrollToIndex(39)

        compose.onNodeWithText("Review 40")
            .assertIsDisplayed()

        compose.onNodeWithTag("AdminRatingsBack")
            .assertIsDisplayed()
    }

    /**
     * Success feedback remains outside the scrolling list.
     */
    @Test
    fun successFeedbackRemainsVisibleWhileScrolling() {

        gateway.rows = listOf(flaggedRating) +
                (1..40).map { index ->

                    CampsiteRating(
                        reservationId = "stay-$index",
                        score = 3,
                        review = "Review $index",
                        accountId = "user-$index"
                    )
                }

        showDetails()

        openRatings()

        selectFlaggedRating()

        compose.onNodeWithText("Yes, delete")
            .performClick()

        compose.onNodeWithTag("AdminRatingsList")
            .performScrollToIndex(39)

        compose.onNodeWithText("Review 40")
            .assertIsDisplayed()

        compose.onNodeWithText("Rating Deleted")
            .assertIsDisplayed()

        compose.onNodeWithTag("AdminRatingsBack")
            .assertIsDisplayed()
    }

    // =====================================================
    // HELPER FUNCTIONS
    // =====================================================

    private fun showDetails(
        account: Account? = admin,
        adminActions: Boolean = true
    ) {

        compose.setContent {

            CampsiteDetailView().DetailView(
                campsite,
                { _, _ -> ImageBitmap(1, 1) },
                {},
                account = account,
                showAdminActions = adminActions,
                adminRatingGateway = gateway
            )
        }
    }

    private fun openRatings() {

        compose.onNodeWithTag("ViewCampsiteRatings")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
    }

    private fun selectFlaggedRating() {

        compose.onNodeWithTag("RemoveRating_entrant-1")
            .assertIsDisplayed()
            .performClick()

        compose.onNodeWithText("Remove Rating?")
            .assertIsDisplayed()
    }


    private class FakeRatingGateway(var rows: List<CampsiteRating>) : AdminRatingGateway {

        var requests = 0
        var observations = 0
        var removedRating: CampsiteRating? = null
        var removedByAdminAccountId: String? = null
        var shouldFail = false

        private var listener:
                ((List<CampsiteRating>) -> Unit)? = null

        override fun observeAdminRatings(
            campsiteId: String,
            onRatings: (List<CampsiteRating>) -> Unit,
            onError: (String) -> Unit
        ): () -> Unit {

            observations++

            listener = onRatings

            onRatings(rows)

            return {
                listener = null
            }
        }

        override fun removeAdminRating(
            campsiteId: String,
            adminAccountId: String,
            selected: CampsiteRating,
            onResult: (Boolean) -> Unit,
            onError: (String) -> Unit
        ) {
            requests++
            removedByAdminAccountId = adminAccountId

            if (shouldFail) {

                onError("Removal failed.")

                return
            }

            if (selected !in rows) {

                onResult(false)

                return
            }

            removedRating = selected

            rows = rows.filterNot {
                it == selected
            }

            listener?.invoke(rows)

            onResult(true)
        }
    }
}
