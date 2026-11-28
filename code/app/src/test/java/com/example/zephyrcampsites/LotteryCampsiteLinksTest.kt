// Hiral - [AI-08] OpenAI ChatGPT Implement US-02.04.02
// Verifies campsite membership for shared lotteries and legacy Firestore records.
package com.example.zephyrcampsites

import org.junit.Assert.assertEquals
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
}
