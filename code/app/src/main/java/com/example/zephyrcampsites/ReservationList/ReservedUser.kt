package com.example.zephyrcampsites.ReservationList

import com.example.zephyrcampsites.DataHolder
import java.util.Date

/**
 * Data class for a waiting list user.
 *
 * No methods, only data holder. ID is given to create, and includes val since these fields must not be modified.
 *
 * @param accountId the account id to assign to this instance.
 */
data class ReservedUser(val accountId: String = "0") : DataHolder(accountId) {
    val campsiteId: String = "0"
    val bookedPeriodStart: Date = Date(1)
    val bookedPeriodEnd: Date = Date(1793723395)
    val status: Boolean = false
}