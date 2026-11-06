package com.example.zephyrcampsites

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import java.io.File
import java.util.Date
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.test.assertIsNotEnabled

/**
 * Verifies campsite detail fields, image states, and existing owner controls.
 * Uses local fixtures and does not read or write Firebase.
 */
@RunWith(AndroidJUnit4::class)
class CampsiteDetailViewTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val temporaryImages = mutableListOf<File>()
    private val campsite = Campsite("1").apply {
        name = "Pine Lake Campground"
        location = "Jasper, Alberta"
        maxOccupancy = 4
        pricePerNight = 35.0
        rating = mutableListOf(4, 5)
        ownerID = "owner-demo"
        description = "A lakeside campsite with picnic tables."
        rules = "Quiet hours after 10 pm. Cancellations require 48 hours notice."
        bookingPeriodStart = Date(1893456000000L)
        bookingPeriodEnd = Date(1894060800000L)
    }

    @After
    fun removeTemporaryImages() {
        for (image in temporaryImages) {
            image.delete()
        }
    }

    @Test
    fun detailsShowRequiredInformationAndBackAction() {
        var backPressed = false
        showDetails(onBack = { backPressed = true })

        composeTestRule.onNodeWithText(campsite.name).assertIsDisplayed()
        composeTestRule.onNodeWithText("Location: Jasper, Alberta").assertExists()
        composeTestRule.onNodeWithText("Maximum occupancy: 4").assertExists()
        composeTestRule.onNodeWithText("Price per night: $35.00").assertExists()
        composeTestRule.onNodeWithText("Rating: 4.50 / 5").assertExists()
        composeTestRule.onNodeWithText("Owner: Jordan Lee").assertExists()
        composeTestRule.onNodeWithText(campsite.description).assertExists()
        composeTestRule.onNodeWithTag("CampsiteImagePlaceholder").assertIsDisplayed()

        composeTestRule.onNodeWithText("Rules and cancellation policy")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(campsite.rules).assertExists()
        composeTestRule.onNodeWithText("Availability (booking period):", substring = true)
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Manage Lottery").assertDoesNotExist()
        composeTestRule.onNodeWithTag("DetailsBack").performClick()
        assertTrue(backPressed)
    }

    @Test
    fun promotionalImageLoadsWhenProvided() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File.createTempFile("campsite-promo-", ".png", context.cacheDir)
        temporaryImages.add(image)
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(35, 123, 103))
        image.outputStream().use { stream ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        }
        bitmap.recycle()
        showDetails(imageUrl = image.absolutePath)

        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithTag("CampsitePromoImage")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("CampsitePromoImage").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Promotional image of ${campsite.name}")
            .assertIsDisplayed()
        composeTestRule.onNodeWithTag("CampsiteImagePlaceholder").assertDoesNotExist()
    }

    @Test
    fun inaccessibleImageShowsPlaceholder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        showDetails(imageUrl = File(context.cacheDir, "missing-campsite-promo.png").absolutePath)

        composeTestRule.waitUntil(timeoutMillis = 10000) {
            composeTestRule.onAllNodesWithText("No promotional image")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("CampsiteImagePlaceholder").assertIsDisplayed()
        composeTestRule.onNodeWithTag("CampsitePromoImage").assertDoesNotExist()
    }

    @Test
    fun missingCampsiteShowsExplanationAndBackAction() {
        var backPressed = false
        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(
                    campsite = null,
                    codeGenerator = { _, _ ->
                        throw AssertionError("A missing campsite must not generate a QR code.")
                    },
                    onBack = { backPressed = true }
                )
            }
        }

        composeTestRule.onNodeWithText("Campsite not found").assertIsDisplayed()
        composeTestRule.onNodeWithTag("DetailsBack").performClick()
        assertTrue(backPressed)
    }

    @Test
    fun missingFieldsAndLegacyPoliciesAreDisplayedHonestly() {
        campsite.pricePerNight = null
        campsite.rating.clear()
        campsite.rules = ""
        campsite.rulesPolicies = listOf("No open fires.", "Cancellation requires 24 hours notice.")
        campsite.bookingPeriodStart = Date(1)
        showDetails(ownerName = null)

        composeTestRule.onNodeWithText("Price per night: Not provided").assertExists()
        composeTestRule.onNodeWithText("Rating: Not rated").assertExists()
        composeTestRule.onNodeWithText("Owner: owner-demo").assertExists()
        composeTestRule.onNodeWithText("No open fires.\nCancellation requires 24 hours notice.")
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText("Availability (booking period): Not provided").assertExists()
    }

    @Test
    fun zeroPriceIsDisplayedAsFreeRatherThanMissing() {
        campsite.pricePerNight = 0.0
        showDetails()
        composeTestRule.onNodeWithText("Price per night: $0.00").assertExists()
    }

    @Test
    fun adminDoesNotSeeOwnerControls() {
        showDetails(account = Account("admin-demo").apply { userType = "admin" })
        composeTestRule.onNodeWithText("Generate/Hide QR").assertDoesNotExist()
        composeTestRule.onNodeWithText("Download/Save QR").assertDoesNotExist()
        composeTestRule.onNodeWithText("Manage Lottery").assertDoesNotExist()
    }

    @Test
    fun differentOwnerDoesNotSeeOwnerControls() {
        showDetails(account = Account("other-owner").apply { userType = "owner" })
        composeTestRule.onNodeWithText("Generate/Hide QR").assertDoesNotExist()
        composeTestRule.onNodeWithText("Download/Save QR").assertDoesNotExist()
        composeTestRule.onNodeWithText("Manage Lottery").assertDoesNotExist()
    }

    @Test
    fun ownerCanStillGenerateQrAndOpenLotteryManagement() {
        var managedId: String? = null
        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(
                    campsite = campsite,
                    codeGenerator = { _, _ -> ImageBitmap(2, 2) },
                    onBack = {},
                    showOwnerView = true,
                    account = Account(campsite.ownerID).apply { userType = "owner" },
                    onManageLottery = { id -> managedId = id }
                )
            }
        }

        composeTestRule.onNodeWithText("Generate/Hide QR").performScrollTo().performClick()
        composeTestRule.onNodeWithContentDescription("Generated QR code of ${campsite.name}")
            .assertExists()
        composeTestRule.onNodeWithText("Download/Save QR").assertExists()
        composeTestRule.onNodeWithText("Manage Lottery").performScrollTo().performClick()
        assertEquals(campsite.id, managedId)
    }

    /**
     * Displays the detail view using local fixture data.
     *
     * @param imageUrl Local fixture image path, or null for the placeholder.
     * @param ownerName Owner name supplied to the view.
     * @param account Account viewing the campsite.
     * @param onBack Called when Back is pressed.
     */
    private fun showDetails(
        imageUrl: String? = null,
        ownerName: String? = "Jordan Lee",
        account: Account = Account("entrant-demo"),
        onBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(
                    campsite = campsite,
                    codeGenerator = { _, _ ->
                        throw AssertionError("Accounts that do not own the campsite must not generate QR codes.")
                    },
                    onBack = onBack,
                    account = account,
                    imageUrl = imageUrl,
                    ownerName = ownerName
                )
            }
        }
    }
    // [AI-07] OpenAI, ChatGPT, assistance with developing Compose UI tests and a fake waiting-list implementation for simulating registration and removal operations.

    private class FakeWaitingListGateway : WaitingListGateway {
        var joinCalled = false
        var joined = false
        var failJoin = false

        override fun isEntrantJoined(
            lotteryId: String,
            accountId: String,
            onResult: (Boolean) -> Unit,
            onError: (String) -> Unit
        ) {
            onResult(joined)
        }

        override fun joinWaitingList(
            lotteryId: String,
            accountId: String,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) {
            joinCalled = true
            if (failJoin) {
                onError("Failed to join waiting list")
            } else {
                joined = true
                onSuccess()
            }
        }

        override fun leaveWaitingList(
            lotteryId: String,
            accountId: String,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) {
            joined = false
            onSuccess()
        }
    }

    @Test
    fun entrantCanJoinActiveLottery() {
        val fake = FakeWaitingListGateway()
        val controller = WaitingListController(fake)

        val now = System.currentTimeMillis()

        val lottery = Lottery("lottery-test").apply {
            campsiteId = campsite.id
            registrationStart = now - 60000L
            registrationEnd = now + 600000L
        }

        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(
                    campsite = campsite,
                    codeGenerator = { _, _ -> ImageBitmap(2, 2) },
                    onBack = {},
                    account = Account("entrant-test").apply {
                        userType = "entrant"
                    },
                    lottery = lottery,
                    waitingListController = controller
                )
            }
        }

        composeTestRule.onNodeWithText("Join Lottery")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithText("Joined")
            .assertExists()

        assertTrue(fake.joinCalled)
    }
    @Test
    fun entrantCanLeaveWaitingListAfterConfirmation() {
        val fake = FakeWaitingListGateway()
        fake.joined = true

        val controller = WaitingListController(fake)
        val now = System.currentTimeMillis()

        val lottery = Lottery("lottery-test").apply {
            campsiteId = campsite.id
            registrationStart = now - 60000L
            registrationEnd = now + 600000L
        }

        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(
                    campsite = campsite,
                    codeGenerator = { _, _ -> ImageBitmap(2, 2) },
                    onBack = {},
                    account = Account("entrant-test").apply {
                        userType = "entrant"
                    },
                    lottery = lottery,
                    waitingListController = controller
                )
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Leave Waiting List")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Leave Waiting List")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithText(
            "Are you sure you want to leave the waiting list?"
        ).assertIsDisplayed()

        composeTestRule.onNodeWithText("Yes, Leave")
            .performClick()

        composeTestRule.onNodeWithText("Join Lottery")
            .assertExists()

        assertFalse(fake.joined)
    }
    @Test
    fun cancellingLeaveKeepsEntrantRegistered() {
        val fake = FakeWaitingListGateway()
        fake.joined = true

        val controller = WaitingListController(fake)
        val now = System.currentTimeMillis()

        val lottery = Lottery("lottery-test").apply {
            campsiteId = campsite.id
            registrationStart = now - 60000L
            registrationEnd = now + 600000L
        }

        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(
                    campsite = campsite,
                    codeGenerator = { _, _ -> ImageBitmap(2, 2) },
                    onBack = {},
                    account = Account("entrant-test").apply {
                        userType = "entrant"
                    },
                    lottery = lottery,
                    waitingListController = controller
                )
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Leave Waiting List")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Leave Waiting List")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithText(
            "Are you sure you want to leave the waiting list?"
        ).assertIsDisplayed()

        composeTestRule.onNodeWithText("Cancel")
            .performClick()

        composeTestRule.onNodeWithText("Leave Waiting List")
            .assertExists()

        composeTestRule.onNodeWithText("Joined")
            .assertExists()

        assertTrue(fake.joined)
    }

    @Test
    fun joinDisabledBeforeRegistrationStarts() {
        val fake = FakeWaitingListGateway()
        val controller = WaitingListController(fake)
        val now = System.currentTimeMillis()

        val lottery = Lottery("future-lottery").apply {
            campsiteId = campsite.id
            registrationStart = now + 600000L
            registrationEnd = now + 1200000L
        }

        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(
                    campsite = campsite,
                    codeGenerator = { _, _ -> ImageBitmap(2, 2) },
                    onBack = {},
                    account = Account("entrant-test").apply {
                        userType = "entrant"
                    },
                    lottery = lottery,
                    waitingListController = controller
                )
            }
        }

        composeTestRule.onNodeWithText("Join Lottery")
            .performScrollTo()
            .assertIsNotEnabled()

        assertFalse(fake.joinCalled)
    }

    @Test
    fun joinDisabledAfterRegistrationEnds() {
        val fake = FakeWaitingListGateway()
        val controller = WaitingListController(fake)
        val now = System.currentTimeMillis()

        val lottery = Lottery("closed-lottery").apply {
            campsiteId = campsite.id
            registrationStart = now - 1200000L
            registrationEnd = now - 600000L
        }

        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(
                    campsite = campsite,
                    codeGenerator = { _, _ -> ImageBitmap(2, 2) },
                    onBack = {},
                    account = Account("entrant-test").apply {
                        userType = "entrant"
                    },
                    lottery = lottery,
                    waitingListController = controller
                )
            }
        }

        composeTestRule.onNodeWithText("Join Lottery")
            .performScrollTo()
            .assertIsNotEnabled()

        assertFalse(fake.joinCalled)
    }
    @Test
    fun failedJoinDisplaysError() {
        val fake = FakeWaitingListGateway()
        fake.failJoin = true

        val controller = WaitingListController(fake)
        val now = System.currentTimeMillis()

        val lottery = Lottery("lottery-test").apply {
            campsiteId = campsite.id
            registrationStart = now - 60000L
            registrationEnd = now + 600000L
        }

        composeTestRule.setContent {
            ZephyrCampsitesTheme {
                CampsiteDetailView().DetailView(
                    campsite = campsite,
                    codeGenerator = { _, _ -> ImageBitmap(2, 2) },
                    onBack = {},
                    account = Account("entrant-test").apply {
                        userType = "entrant"
                    },
                    lottery = lottery,
                    waitingListController = controller
                )
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Join Lottery")
                .fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Join Lottery")
            .performScrollTo()
            .performClick()

        composeTestRule.onNodeWithText("Failed to join waiting list")
            .performScrollTo()
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Joined")
            .assertDoesNotExist()

        assertTrue(fake.joinCalled)
        assertFalse(fake.joined)
    }
}
