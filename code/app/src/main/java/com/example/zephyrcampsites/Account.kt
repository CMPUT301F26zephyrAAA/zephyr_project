package com.example.zephyrcampsites

/**
 * Profile for an entrant, owner, or administrator in the Firestore accounts collection.
 * @param id Profile ID in the form auth_<Firebase UID>.
 * @property userType Account roles, defaulting to entrant and owner.
 * @property name Display name.
 * @property email Contact email address.
 * @property phoneNumber Contact phone number.
 * @property notificationsEnabled Whether notification senders may notify this account.
 * @property imagePath Optional profile image URL or Storage path.
 * @property ownerPrivilegesRevoked Whether an administrator removed owner privileges.
 */

data class Account(
    override val id: String = "",
    var userType: List<String> = listOf("entrant", "owner"), // Can also hold the "admin" value
    override var name: String = "",
    var email: String = "",
    var phoneNumber: String = "",
    var notificationsEnabled: Boolean = true,
    var imagePath: String = "",
    var compressedImage: String = "",
    val ownerPrivilegesRevoked: Boolean = false
) : DataHolder(id)

/** Uses the saved profile and any revocations received during this session. */
internal fun Account.canUseOwnerMode(revokedOwners: Set<String>?): Boolean =
    "owner" in userType && !ownerPrivilegesRevoked && revokedOwners?.contains(id) != true

/** Returns a signed-in profile with admin access derived from its verified Auth claim. */
internal fun Account.withVerifiedAdminRole(isAdmin: Boolean): Account =
    copy(userType = accountRoles(if (isAdmin) "admin" else null))

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

/** Reads explicit profile roles and preserves the default for legacy profiles. */
internal fun storedProfileRoles(storedValue: Any?): List<String> = when (storedValue) {
    is List<*> -> storedValue.filterIsInstance<String>()
    "entrant", "owner" -> listOf(storedValue as String)
    else -> accountRoles(storedValue)
}
