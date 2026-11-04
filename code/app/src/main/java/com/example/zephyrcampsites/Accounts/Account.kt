package com.example.zephyrcampsites.Accounts

import com.example.zephyrcampsites.DataHolder

/**
 * Account data class for a device identified user i.e. entrant, owner or admin
 * Stored in the "accounts" Firestore collection. DocumentID is the deviceID so the app identifies the user without a username or password.
 *
 * @param deviceId Device ID used as Firestore document ID.
 */
data class Account (val deviceId: String = "0") : DataHolder(deviceId) {
    override var name: String = ""
    var email: String = ""
    var phoneNumber: String = ""
    var notificationPreference: Boolean = false  // Default value is false because admin doesn't have any profile page so they don't have any notification preferences.
    var userTypes: MutableList<UserType> = mutableListOf()  // The same account/deviceID can hold multiple user types. For example, the same person may be an admin as well as an entrant.
    var profileImagePath: String = ""
}

/**
 * Enum class for defining user types.
 * Creates a type called UserType and holds the 3 different user types: Entrant, Owner, and Admin.
 *
 * @param value the campsite id to assign to this instance.
 */
enum class UserType(val value: String) {
    ENTRANT("entrant"),
    OWNER("owner"),
    ADMIN("admin")
}