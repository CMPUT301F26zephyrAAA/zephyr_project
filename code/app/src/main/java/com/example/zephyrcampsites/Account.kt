package com.example.zephyrcampsites

/**
 * Account data class for a device identified user ie. entrant, owner or admin
 * Stored in the "accounts" Firestore collection. DocumentID is the deviceID so the app identifies the user without a username or password.
 * @param id Device ID used as firestore document ID
 * @property userType Account role, defaulting to entrant.
 * @property name Display name.
 * @property email Contact email address.
 * @property phoneNumber Contact phone number.
 * @property notificationsEnabled Whether notification senders may notify this account.
 */

data class Account(
    override val id: String = "",
    var userType: String = "entrant",
    override var name: String = "",
    var email: String = "",
    var phoneNumber: String = "",
    var notificationsEnabled: Boolean = true
) : DataHolder(id)
