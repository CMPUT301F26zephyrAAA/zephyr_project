package com.example.zephyrcampsites

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.Calendar
import java.util.GregorianCalendar

/** Verifies the reservation chat header and Back action with local test data. */
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
                    ownerName = "Jordan Lee",
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
}
