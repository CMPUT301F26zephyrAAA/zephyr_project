// Hiral - [AI-08] OpenAI ChatGPT Implement US-02.04.02
// Verifies campsite membership for shared lotteries and legacy Firestore records.
package com.example.zephyrcampsites

import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LotteryCampsiteLinksTest {
    @Test
    fun sharedLotteryUsesEverySavedLink() {
        val lottery = Lottery("shared").apply {
            campsiteId = "12"
            campsiteIds = listOf("12", "34")
        }

        assertEquals(listOf("12", "34"), lottery.participatingCampsiteIds())
    }

    @Test
    fun legacyLotteryUsesItsSingleCampsite() {
        val lottery = Lottery("legacy").apply { campsiteId = "12" }

        assertEquals(listOf("12"), lottery.participatingCampsiteIds())
        assertEquals(emptyList<String>(), Lottery().participatingCampsiteIds())
    }

    @Test
    fun legacyReservationConsumesOfferOnlyWhenItsStayOverlaps() {
        val lottery = Lottery("shared").apply {
            campsiteId = "12"
            bookingStart = 30L
            bookingEnd = 60L
        }
        val reservation = ReservedUser(
            campsiteId = "12",
            bookedPeriodStart = Date(10L),
            bookedPeriodEnd = Date(30L)
        )

        assertFalse(reservation.usesLotteryOffer(lottery))
        assertTrue(reservation.copy(bookedPeriodEnd = Date(31L)).usesLotteryOffer(lottery))
        assertTrue(reservation.copy(lotteryId = lottery.id).usesLotteryOffer(lottery))
    }
}
