// Covers blank, oversized, and recipient-free messages in the owner notification composer.
// AI Citation [AI-39]
// OpenAI, Codex, US 02.10.01 validation tests, 2026-11-27. See the Bhav Saluja entry in README.md.
package com.example.zephyrcampsites

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LotteryNotificationValidationTest {
    @Test
    fun rejectsBlankMessageAndMissingRecipients() {
        assertEquals("Enter a notification message.", lotteryNotificationValidationError("\n \t", true))
        assertEquals("Choose at least one recipient.", lotteryNotificationValidationError("Road closed", false))
    }

    @Test
    fun validatesLengthAfterTrimming() {
        val longestMessage = "a".repeat(MAX_LOTTERY_NOTIFICATION_LENGTH)
        assertNull(lotteryNotificationValidationError("  $longestMessage \n", true))
        assertEquals(
            "Use 2,000 characters or fewer.",
            lotteryNotificationValidationError("${longestMessage}a", true)
        )
    }
}
