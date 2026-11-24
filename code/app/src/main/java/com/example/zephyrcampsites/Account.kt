package com.example.zephyrcampsites

/**
 * Account data class for a device identified user ie. entrant, owner or admin
 * Stored in the "accounts" Firestore collection. DocumentID is the deviceID so the app identifies the user without a username or password.
 * @param id Device ID used as firestore document ID
 * @property userType Account roles, defaulting to entrant and owner.
 * @property name Display name.
 * @property email Contact email address.
 * @property phoneNumber Contact phone number.
 * @property notificationsEnabled Whether notification senders may notify this account.
 * @property imagePath Optional profile image URL or Storage path.
 */

data class Account(
    override val id: String = "",
    var userType: List<String> = listOf("entrant", "owner"), // Can also hold the "admin" value
    override var name: String = "",
    var email: String = "",
    var phoneNumber: String = "",
    var notificationsEnabled: Boolean = true,
    var imagePath: String = ""
) : DataHolder(id)

internal fun accountRoles(storedValue: Any?): List<String> {
    val hasAdminGrant = when (storedValue) {
        "admin" -> true
        is List<*> -> "admin" in storedValue
        else -> false
    }

    return if (hasAdminGrant) {
        listOf("entrant", "owner", "admin")
    } else {
        listOf("entrant", "owner")
    }
}
