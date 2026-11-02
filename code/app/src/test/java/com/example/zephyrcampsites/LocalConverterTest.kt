package com.example.zephyrcampsites

import org.junit.Test
import java.util.TimeZone

/**
 * Test class for LocalConverter
 */
class LocalConverterTest {
    val localConverter = LocalConverter()

    /**
     * Tests whether the timezoneConvert function works and output matches expected result.
     * Forces timezone to ensure test consistency.
     */
    @Test
    fun timezoneConvertTestCorrect() {
        val expectedRes : Long = 1793491200000
        val testTimeZone = TimeZone.getTimeZone("GMT-6")
        assert((localConverter.timezoneConvert(target = 1793469600000, timeZone = testTimeZone)) == expectedRes)
    }

    /**
     * Tests whether the timezoneConvert function works and output is not the initial input.
     * Forces timezone to ensure test consistency.
     */
    @Test
    fun timezoneConvertTestIncorrect() {
        val expectedFalseRes : Long = 1793491200000
        val testTimeZone = TimeZone.getTimeZone("GMT-6")
        assert((localConverter.timezoneConvert(target = 1793491200000, timeZone = testTimeZone)) != expectedFalseRes)
    }
}