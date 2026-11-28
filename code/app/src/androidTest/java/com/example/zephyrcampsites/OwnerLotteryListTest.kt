// Hiral - [AI-08] OpenAI ChatGPT Implement US-02.04.02
// Exercises the owner lottery list actions without Firebase.
package com.example.zephyrcampsites

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class OwnerLotteryListTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun createAndBackAreOnTheSameRowAndRunTheirOwnCallbacks() {
        var created = 0
        var backed = 0
        compose.setContent {
            LotteryListScreen("My Lotteries", { backed++ }, { created++ }) {}
        }

        val back = compose.onNodeWithText("Back")
        val create = compose.onNodeWithText("Create Lottery")
        assertEquals(back.getUnclippedBoundsInRoot().top, create.getUnclippedBoundsInRoot().top)
        assertTrue(create.getUnclippedBoundsInRoot().left > back.getUnclippedBoundsInRoot().right)
        create.performClick()
        back.performClick()

        compose.runOnIdle {
            assertEquals(1, created)
            assertEquals(1, backed)
        }
    }

    @Test
    fun listsWithoutACreationActionDoNotShowTheButton() {
        compose.setContent {
            LotteryListScreen("Notifications", {}) {}
        }

        compose.onNodeWithText("Create Lottery").assertDoesNotExist()
    }
}
