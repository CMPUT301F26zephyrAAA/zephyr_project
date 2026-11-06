package com.example.zephyrcampsites

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks image selection, confirmation, failure feedback, and access using local fixtures. */
@RunWith(AndroidJUnit4::class)
class AdminImageViewTest {
    @get:Rule val compose = createComposeRule()
    private val admin = Account("admin", userType = "admin")
    private val image = AdminImage("profile", "Taylor Morgan", "profiles/taylor.png", "profile")

    @Test
    fun emptyBrowserDisplaysMessageAndBackWorks() {
        var back = false
        compose.setContent {
            AdminImageBrowser(
                admin,
                emptyList(),
                false,
                null,
                { _, _, _ -> },
                { back = true },
                { _, result -> result(null) },
            )
        }
        compose.onNodeWithText("No images found.").assertIsDisplayed()
        compose.onNodeWithText("BACK").performClick()
        compose.runOnIdle { assertTrue(back) }
    }

    @Test
    fun nonAdminCannotBrowseOrDelete() {
        compose.setContent {
            AdminImageBrowser(
                Account("entrant"),
                listOf(image),
                false,
                null,
                { _, _, _ -> fail("Removal was allowed") },
                {},
                { _, result -> result(null) },
            )
        }
        compose.onNodeWithText("Administrator access required.").assertIsDisplayed()
        compose.onNodeWithText("Taylor Morgan").assertDoesNotExist()
        compose.onNodeWithTag("RemoveImage").assertDoesNotExist()
    }

    @Test
    fun removalRequiresConfirmationAndReturnsToUpdatedList() {
        val images = mutableStateOf(listOf(image))
        var requests = 0
        compose.setContent {
            AdminImageBrowser(
                admin,
                images.value,
                false,
                null,
                { selected, success, _ ->
                    requests++
                    assertEquals(image, selected)
                    images.value = emptyList()
                    success()
                },
                {},
                { _, result -> result(null) },
            )
        }
        selectImage()
        compose.onNodeWithText("Owner: Taylor Morgan").assertIsDisplayed()
        compose.onNodeWithTag("RemoveImage").performClick()
        compose.onNodeWithText("CANCEL").performClick()
        compose.runOnIdle { assertEquals(0, requests) }
        compose.onNodeWithTag("RemoveImage").performClick()
        compose.onNodeWithTag("ConfirmRemoveImage").performClick()
        compose.onNodeWithText("No images found.").assertIsDisplayed()
        compose.runOnIdle { assertEquals(1, requests) }
    }

    @Test
    fun failedRemovalShowsTheErrorAndKeepsTheSelectedImage() {
        compose.setContent {
            AdminImageBrowser(
                admin,
                listOf(image),
                false,
                null,
                { _, _, failure -> failure(IllegalStateException("Storage denied deletion")) },
                {},
                { _, result -> result(null) },
            )
        }
        selectImage()
        compose.onNodeWithTag("RemoveImage").performClick()
        compose.onNodeWithTag("ConfirmRemoveImage").performClick()
        compose.onNodeWithText("Storage denied deletion").assertIsDisplayed()
        compose.onNodeWithText("IMAGE DETAILS").assertIsDisplayed()
        compose.onNodeWithTag("RemoveImage").assertIsEnabled()
    }

    @Test
    fun pendingRemovalDisablesRepeatedDeletionAndBack() {
        var complete: (() -> Unit)? = null
        compose.setContent {
            AdminImageBrowser(
                admin,
                listOf(image),
                false,
                null,
                { _, success, _ -> complete = success },
                {},
                { _, result -> result(null) },
            )
        }
        selectImage()
        compose.onNodeWithTag("RemoveImage").performClick()
        compose.onNodeWithTag("ConfirmRemoveImage").performClick()
        compose.onNodeWithTag("RemoveImage").assertIsNotEnabled()
        compose.onNodeWithText("BACK TO BROWSE").assertIsNotEnabled()
        compose.runOnIdle { complete!!() }
        compose.onNodeWithText("BROWSE IMAGES (ADMIN)").assertIsDisplayed()
    }

    private fun selectImage() {
        compose.onNodeWithTag("AdminImage_profile_profiles/taylor.png").performClick()
    }
}
