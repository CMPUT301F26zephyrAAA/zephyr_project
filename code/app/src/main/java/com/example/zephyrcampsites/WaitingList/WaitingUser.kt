package com.example.zephyrcampsites.WaitingList

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.zephyrcampsites.DataHolder
import java.sql.Timestamp

/**
 * Data class for a waiting list user.
 *
 * No methods, only data holder. ID is given to create, and includes val since these fields must not be modified.
 *
 * @param accountId the account id to assign to this instance.
 */
data class WaitingUser(val accountId: String = "0") : DataHolder(accountId) {
    val lotteryId: String = "0"
    var joinTimestamp by mutableStateOf(Timestamp(System.currentTimeMillis()))  // For current timestamp - https://developer.android.com/reference/java/sql/Timestamp
}