package com.example.zephyrcampsites.Notifications

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.zephyrcampsites.DataHolder
import java.sql.Timestamp

/**
 * Data class for a notification.
 *
 * No methods, only data holder. ID is given to create, and includes val to prevent data from being modified.
 *
 * @param notificationId the notification id to assign to this instance.
 */
data class Notification(val notificationId: String = "0") : DataHolder(notificationId) {
    val sentTimestamp by mutableStateOf(Timestamp(System.currentTimeMillis()))  // For current timestamp - https://developer.android.com/reference/java/sql/Timestamp
    val senderID: String = "0"
    val notificationContent: String = ""
    val notificationType: NotificationType = NotificationType.NOTIFICATION
}

/**
 * Enum class for defining user types.
 *
 * No methods, only data holder.
 * Creates a type called UserType and holds the 3 different user types: Entrant, Owner, and Admin.
 *
 * @param value the campsite id to assign to this instance.
 */
enum class NotificationType(val value: String) {
    NOTIFICATION("notification"),
    MESSAGE("message")
}