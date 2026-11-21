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

/** Verifies campsite browse, role checks, confirmation, and removal feedback without Firebase. */
@RunWith(AndroidJUnit4::class)
class AdminCampsiteViewTest {
    @get:Rule val compose = createComposeRule()
    private val admin = Account("admin", userType = "admin")
    private val campsite =
        Campsite("1").apply {
            name = "Pine Lake"
            location = "Jasper"
            ownerID = "owner"
            maxOccupancy = 4
        }

    @Test
    fun browseShowsRequiredFieldsAndSelectsTheCorrectCampsite() {
        var selected: Campsite? = null
        compose.setContent {
            AdminCampsiteListView(
                admin,
                listOf(campsite),
                false,
                null,
                { _, result -> result(null) },
                { selected = it },
                {},
            )
        }
        compose.onNodeWithText("Pine Lake").assertIsDisplayed()
        compose.onNodeWithText("Location: Jasper").assertIsDisplayed()
        compose.onNodeWithText("Owner: owner").assertIsDisplayed()
        compose.onNodeWithText("Maximum occupancy: 4").assertIsDisplayed()
        compose.onNodeWithText("Rating: Not rated").assertIsDisplayed()
        compose.onNodeWithTag("AdminCampsite_1").performClick()
        compose.runOnIdle { assertEquals(campsite, selected) }
    }

    @Test
    fun browseShowsEmptyStateAndBackWorks() {
        var back = false
        compose.setContent {
            AdminCampsiteListView(
                admin,
                emptyList(),
                false,
                null,
                { _, result -> result(null) },
                {},
                { back = true },
            )
        }
        compose.onNodeWithText("No campsites registered.").assertIsDisplayed()
        compose.onNodeWithText("BACK").performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test
    fun nonAdminCannotBrowse() {
        compose.setContent {
            AdminCampsiteListView(
                Account("entrant"),
                listOf(campsite),
                false,
                null,
                { _, result -> result(null) },
                {},
                {},
            )
        }
        compose.onNodeWithText("Administrator access required.").assertIsDisplayed()
        compose.onNodeWithTag("AdminCampsite_1").assertDoesNotExist()
    }

    @Test
    fun removalRequiresConfirmationAndCancelDoesNotDelete() {
        var requests = 0
        compose.setContent {
            CampsiteDetailView()
                .DetailView(
                    campsite,
                    { _, _ -> ImageBitmap(1, 1) },
                    {},
                    account = admin,
                    onRemove = { requests++ },
                )
        }
        compose.onNodeWithTag("RemoveCampsite").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithText("CANCEL").performClick()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag("RemoveCampsite").performScrollTo().performClick()
        compose.onNodeWithTag("ConfirmRemoveCampsite").performClick()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun adminDoesNotSeeOwnerOrEntrantControls() {
        compose.setContent {
            CampsiteDetailView()
                .DetailView(
                    campsite,
                    { _, _ -> ImageBitmap(1, 1) },
                    {},
                    account = admin,
                    showOwnerView = true,
                )
        }
        compose.onNodeWithText("Manage Lottery").assertDoesNotExist()
        compose.onNodeWithText("Generate/Hide QR").assertDoesNotExist()
        compose.onNodeWithText("Join Lottery").assertDoesNotExist()
    }

    @Test
    fun entrantDoesNotSeeRemoveButton() {
        compose.setContent {
            CampsiteDetailView()
                .DetailView(
                    campsite,
                    { _, _ -> ImageBitmap(1, 1) },
                    {},
                    account = Account("entrant"),
                )
        }
        compose.onNodeWithTag("RemoveCampsite").assertDoesNotExist()
    }

    @Test
    fun failedRemovalIsShownAndCanBeRetried() {
        val error = mutableStateOf<String?>(null)
        compose.setContent {
            CampsiteDetailView()
                .DetailView(
                    campsite,
                    { _, _ -> ImageBitmap(1, 1) },
                    {},
                    account = admin,
                    removalError = error.value,
                    onRemove = { error.value = "Removal failed." },
                )
        }
        compose.onNodeWithTag("RemoveCampsite").performScrollTo().performClick()
        compose.onNodeWithTag("ConfirmRemoveCampsite").performClick()
        compose.onNodeWithText("Removal failed.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("RemoveCampsite").assertIsEnabled()
    }
}
