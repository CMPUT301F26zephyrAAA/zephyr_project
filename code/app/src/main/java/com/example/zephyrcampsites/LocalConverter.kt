package com.example.zephyrcampsites

import java.util.TimeZone

/**
 * Class to handle any necessary conversions to local/client device.
 */
class LocalConverter {
    /**
     * Converts given time into UTC.
     *
     * @param target Time to convert in user timezone, in type long.
     * @param timeZone Timezone to use for conversion. Defaults to local/default timezone.
     * @return Converted UTC time, in type long.
     */
    fun timezoneConvert (
        target: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        val difference = timeZone.getOffset(System.currentTimeMillis()) // Default in picker is UTC, so gets difference between local time and UTC first
        return (target - difference)
    }
}