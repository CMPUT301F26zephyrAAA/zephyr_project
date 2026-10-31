package com.example.zephyrcampsites

/**
 * Account data class for a device identified user ie. entrant, owner or admin
 * Stored in the "accounts" Firestore collection. DocumentID is the deviceID so the app identifies the user without a username or password.
 * @param id Device ID used as firestore document ID
 */

data class Account(override val id: String = "") : DataHolder(id){
    /**Role of this account is entrant, owner or admin. Defaults to entrant**/
    var userType: String = "entrant"
}