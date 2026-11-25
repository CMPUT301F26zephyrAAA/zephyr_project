package com.example.zephyrcampsites

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

/** Verifies the reservation chat view with local test data. */
class ReservationChatViewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheOwnerAndReservationAndReturnsToDetails() {
        val reservation = ReservedUser(
            id = "reservation-1",
            bookedPeriodStart = GregorianCalendar(2030, Calendar.JUNE, 15).time,
            bookedPeriodEnd = GregorianCalendar(2030, Calendar.JUNE, 18).time
        )
        var returnedToDetails = false

        composeRule.setContent {
            MaterialTheme {
                ReservationChatView(
                    reservation = reservation,
                    campsiteName = "Pinewood Lakeside",
                    participantName = "Jordan Lee",
                    status = "",
                    onBack = { returnedToDetails = true }
                )
            }
        }

        composeRule.onNodeWithText("Jordan Lee").assertIsDisplayed()
        composeRule.onNodeWithText("Pinewood Lakeside · 06/15/30 to 06/18/30").assertIsDisplayed()
        composeRule.onNodeWithText("BACK").performClick()
        composeRule.runOnIdle { assertTrue(returnedToDetails) }
    }

    @Test
    fun showsIncomingMessageAndKeepsDraftAfterSendFailure() {
        var sentText = ""
        composeRule.setContent {
            var draft by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            MaterialTheme {
                ReservationChatView(
                    reservation = ReservedUser(id = "reservation-1"),
                    campsiteName = "Pinewood Lakeside",
                    participantName = "Jordan Lee",
                    status = "",
                    onBack = {},
                    messages = listOf(ReservationMessage("reply-1", "owner", "Check-in is at 2.")),
                    userUid = "entrant",
                    newMessageId = "reply-1",
                    draft = draft,
                    canCompose = true,
                    sendError = error,
                    onDraftChange = { draft = it },
                    onSend = {
                        sentText = draft
                        error = "Could not send your message. Try again."
                    }
                )
            }
        }

        composeRule.onNodeWithText("New message").assertIsDisplayed()
        composeRule.onNodeWithText("Check-in is at 2.").assertIsDisplayed()
        composeRule.onNodeWithText("SEND").assertIsNotEnabled()
        composeRule.onNode(hasSetTextAction()).performTextInput("Thanks!")
        composeRule.onNodeWithText("SEND").performClick()
        composeRule.onNodeWithText("Could not send your message. Try again.").assertIsDisplayed()
        composeRule.onNodeWithText("Thanks!").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("Thanks!", sentText) }
    }

    @Test
    fun ownerSelectsTheReservationTheyAreReplyingTo() {
        val start = GregorianCalendar(2030, Calendar.JUNE, 15).time
        val end = GregorianCalendar(2030, Calendar.JUNE, 18).time
        val first = ReservedUser("booking-1", "entrant-1", "1", start, end)
        val second = ReservedUser("booking-2", "entrant-2", "1", start, end)
        var selected: ReservedUser? = null
        composeRule.setContent {
            MaterialTheme {
                ReservationReplyList(
                    reservations = listOf(first, second),
                    loadFailed = false,
                    onSelectReservation = { selected = it },
                    onRetry = {},
                    onBack = {}
                )
            }
        }
        composeRule.onNodeWithText("Reservation booking-2", substring = true).performClick()
        composeRule.runOnIdle { assertEquals(second, selected) }
    }
}
