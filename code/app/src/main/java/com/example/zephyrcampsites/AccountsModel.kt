package com.example.zephyrcampsites
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Model class for accounts. Reads/Writes the "accounts" Firestore collection.
 * **/

class AccountsModel {
    private val accounts = Firebase.firestore.collection("accounts") //connecting to firestore

    /**
     ** Retrives an account using its device ID
     * @param deviceID ID of the device
     * @param onResult Returns the account, or null if not found
     * @param onError Called if Firestore cannot read the document
     */

    fun getAccount(deviceID: String, onResult: (Account?) -> Unit, onError: (Exception) -> Unit) {
        accounts.document(deviceID).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val account = Account(document.id)
                account.userType = document.getString("userType") ?: "entrant"
                onResult(account)
            } else {
                onResult(null)
            }
        }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    /**
     * Creates an account in Firestore
     * @param account Account to be stored.
     * @param onSuccess Called when the account is saved
     * @param onError Called if saving fails.
     * **/
    fun createAccount(account: Account, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val accountData = hashMapOf(
            "id" to account.id,
            "userType" to account.userType
        ) //AI use: understanding hashMapOf concept (https://chatgpt.com/share/6ae94496-0440-83eb-9305-3b1de9913155)
        accounts.document(account.id).set(accountData).addOnSuccessListener { onSuccess() }
            .addOnFailureListener { exception -> onError(exception) }
    }

    /***Retrieve an existing account or create one if device has not been registered***/
    fun getOrCreateAccount(
        deviceID: String,
        onResult: (Account) -> Unit,
        onError: (Exception) -> Unit
    ) {
        getAccount(
            deviceID, onResult = { account ->
                if (account != null) {
                    //returning user
                    onResult(account)
                } else {
                    //First time user
                    val newAccount = Account(deviceID)
                    createAccount(
                        newAccount,
                        onSuccess = { onResult(newAccount) },
                        onError = onError
                    )
                }
            },
            onError = onError
        )
    }
}
