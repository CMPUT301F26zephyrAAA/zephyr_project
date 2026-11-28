package com.example.zephyrcampsites

import java.util.Date

/**
 * Holds a confirmed reservation used to verify that an entrant has stayed at a campsite.
 * New lottery reservations retain their originating lottery ID.
 *
 * @property id Reservation document ID.
 * @property accountId Account that reserved the campsite.
 * @property campsiteId Reserved campsite ID.
 * @property bookedPeriodStart Start of the stay, or null if not recorded.
 * @property bookedPeriodEnd End of the stay, or null if not recorded.
 * @property cancelled Whether the reservation was cancelled or rejected.
 * @property lotteryId ID of the lottery that produced the reservation, if recorded.
 */
data class ReservedUser(
    override val id: String = "",
    val accountId: String = "",
    val campsiteId: String = "",
    val bookedPeriodStart: Date? = null,
    val bookedPeriodEnd: Date? = null,
    val cancelled: Boolean = false,
    val lotteryId: String? = null
) : DataHolder(id)
