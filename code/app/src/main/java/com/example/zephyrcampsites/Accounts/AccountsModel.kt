package com.example.zephyrcampsites.Accounts
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Model class for accounts. Reads/Writes/Modifies data in the "accounts" Firestore collection.
 */

class AccountsModel {
    private val accounts = Firebase.firestore.collection("accounts") //connecting to firestore

    /**
     * Retrieves an account using its device ID
     *
     * @param deviceID ID of the device
     * @param onResult Returns the account, or null if not found
     * @param onError Called if Firestore cannot read the document
     */

    fun getAccount(deviceID: String, onResult: (Account?) -> Unit, onError: (Exception) -> Unit) {
        accounts.document(deviceID).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val account = Account(
                    deviceId = document.id,
                    name = document.getString("name") ?: "",
                    email = document.getString("email") ?: "",
                    phoneNumber = document.getString("phoneNumber") ?: "",
                    notificationPreference = document.getBoolean("notificationPreference") ?: true,
                    profileImagePath = document.getString("profileImagePath") ?: ""
                )
                val typesList = document.get("userTypes") as? List<String>
                if (typesList != null) {
                    account.userTypes = typesList.map { UserType.fromString(it) }.toMutableList()
                }
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
     * Retrieves a list of all accounts from Firebase.
     *
     * @param onResult Returns the list of accounts, or null if not found.
     * @param onError Called if Firestore cannot successfully import the list of all the accounts.
     */
    fun getAllAccounts(onResult: (List<Account>) -> Unit, onError: (Exception) -> Unit) {
        accounts.get().addOnSuccessListener { snapshot ->
            val accountsList = snapshot.documents.mapNotNull { doc ->
                val account = Account(
                    deviceId = doc.id,
                    name = doc.getString("name") ?: "",
                    email = doc.getString("email") ?: "",
                    phoneNumber = doc.getString("phoneNumber") ?: "",
                    notificationPreference = doc.getBoolean("notificationPreference") ?: true,
                    profileImagePath = doc.getString("profileImagePath") ?: ""
                )
                val typesList = doc.get("userTypes") as? List<String>
                if (typesList != null) {
                    account.userTypes = typesList.map { UserType.fromString(it) }.toMutableList()
                }
                account
            }
            onResult(accountsList)
        }.addOnFailureListener { exception -> onError(exception) }
    }

    /**
     * Creates or overwrites an account in Firestore.
     *
     * @param account Account to be stored.
     * @param onSuccess Called when the account is successfully created or updated.
     * @param onError Called if creating or updating the Account in the database fails.
     */
    fun createOrModifyAccount(account: Account, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        val accountData = hashMapOf(
            "deviceId" to account.deviceId,
            "name" to account.name,
            "email" to account.email,
            "phoneNumber" to account.phoneNumber,
            "notificationPreference" to account.notificationPreference,
            "userTypes" to account.userTypes.map { it.value },
            "profileImagePath" to account.profileImagePath
        ) //AI use: understanding hashMapOf concept (https://chatgpt.com/share/6ae94496-0440-83eb-9305-3b1de9913155)

        accounts.document(account.deviceId).set(accountData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { exception -> onError(exception) }
    }

    /**
     * Retrieves an existing account or creates one if the device has not been registered.
     *
     * @param deviceID ID of the device
     * @param onResult Returns the account, or null if not found
     * @param onError Called if Firestore cannot read the document
     */
    fun getOrCreateAccount(deviceID: String, onResult: (Account) -> Unit, onError: (Exception) -> Unit) {
        getAccount(
            deviceID, onResult = { account ->
                if (account != null) {
                    //returning user
                    onResult(account)
                } else {
                    //First time user
                    val newAccount = Account(deviceID)
                    createOrModifyAccount(newAccount, onSuccess = { onResult(newAccount) }, onError = onError)
                }
            },
            onError = onError
        )
    }

    /**
     * Deletes an account from Firestore. Only deletes the specific document in accounts collection and does not delete any other documents in other collections that store the same deviceID.
     *
     * @param deviceID ID of the device
     * @param onSuccess Called when the account is deleted.
     * @param onError Called if deleting the Account in the database fails.
     */
    fun deleteAccount(deviceID: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        // Should we implement this as delete or disable? And if as a delete then how do we implement cascade delete?
        // How do we handle the active waitlists, lotteries, and reservations that are associated with the deviceID being deleted or disabled?
        accounts.document(deviceID).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { exception -> onError(exception) }
    }
}