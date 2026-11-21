package com.example.zephyrcampsites
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.FieldValue
import com.google.android.gms.tasks.Tasks
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage

/**
 * Model class for accounts. Reads/Writes the "accounts" Firestore collection.
 *
 * @param database Optional database used by local emulator tests.
 * @param storage Optional Storage instance used by local emulator tests.
 * **/

open class AccountsModel(private val database: FirebaseFirestore? = null, private val storage: FirebaseStorage? = null) {
    private val firestore by lazy { database ?: Firebase.firestore }
    private val accounts by lazy { firestore.collection("accounts") } // connecting to firestore

    /**
     ** Retrieves an account using its device ID
     * @param deviceID ID of the device
     * @param onResult Returns the account, or null if not found
     * @param onError Called if Firestore cannot read the document
     */

    fun getAccount(deviceID: String, onResult: (Account?) -> Unit, onError: (Exception) -> Unit) {
        accounts.document(deviceID).get().addOnSuccessListener { document ->
            if (document.exists()) {
                val account = Account(document.id)
                account.userType = document.getString("userType") ?: "entrant"
                account.name = document.getString("name") ?: ""
                account.email = document.getString("email") ?: ""
                account.phoneNumber = document.getString("phoneNumber") ?: ""
                account.notificationsEnabled = document.getBoolean("notificationsEnabled") ?: true
                account.imagePath = document.getString("imagePath") ?: ""
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
        //[AI-02] OpenAI, ChatGPT, explanation of hashMapOf for constructing Firestore account document fields
        val accountData = hashMapOf(
            "id" to account.id,
            "userType" to account.userType,
            "notificationsEnabled" to account.notificationsEnabled
        )
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

    /**
     * Updates personal information of an existing account.
     * @param account Account containing the updated details
     * @param onSuccess Called when Firestore saves the changes
     * @param onError called if the update fails
     * **/
    open fun updateAccount(
        account: Account,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val updatedData = hashMapOf<String, Any>(
            "name" to account.name,
            "email" to account.email,
            "phoneNumber" to account.phoneNumber
        )
        accounts.document(account.id) //chooses which account to update
            .update(updatedData) //updates the specified field
            .addOnSuccessListener{
                onSuccess()
            }
            .addOnFailureListener{exception ->
                onError(exception)
            }
    }

    /**
     * Saves the notification preference without changing other account fields.
     *
     * @param deviceId Device identifier of the account.
     * @param enabled Whether the account may receive notifications.
     * @param onSuccess Called after the preference is saved.
     * @param onError Called if the preference cannot be saved.
     */
    open fun updateNotificationPreference(
        deviceId: String,
        enabled: Boolean,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        accounts.document(deviceId).update("notificationsEnabled", enabled)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onError(it) }
    }

    /**
     * Observes registered profiles without requiring manual refresh.
     *
     * @param onSuccess Receives the latest profiles.
     * @param onError Receives a loading error.
     * @return Listener that the caller must remove when leaving the screen.
     */
    open fun observeAccounts(
        onSuccess: (List<Account>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        return accounts.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
            } else {
                onSuccess(
                    snapshot?.documents.orEmpty().map { document ->
                        Account(
                            document.id,
                            userType = document.getString("userType") ?: "entrant",
                            name = document.getString("name") ?: "",
                            email = document.getString("email") ?: "",
                            phoneNumber = document.getString("phoneNumber") ?: "",
                            notificationsEnabled =
                                document.getBoolean("notificationsEnabled") ?: true,
                            imagePath = document.getString("imagePath") ?: "",
                        )
                    }
                )
            }
        }
    }

    /**
     * Removes a profile, its waiting list entries, and its active reservations' availability holds.
     *
     * @param targetAccount Profile to remove.
     * @param adminId Administrator recorded on cancelled reservations.
     * @param onSuccess Called after the cleanup and deletion commit.
     * @param onError Called when deletion fails.
     */
    open fun deleteAccount(
        targetAccount: Account,
        adminId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        val accountRef = accounts.document(targetAccount.id)
        accountRef
            .get(Source.SERVER)
            .addOnSuccessListener { existing ->
                if (!existing.exists()) {
                    onError(IllegalStateException("Profile no longer exists."))
                    return@addOnSuccessListener
                }
                val fail: (Exception) -> Unit = { error ->
                    accountRef.update("deletionPending", FieldValue.delete())
                    onError(error)
                }
                accountRef
                    .update("deletionPending", true)
                    .addOnSuccessListener {
                        val queries =
                            listOf(
                                firestore
                                    .collection("waitingList")
                                    .whereEqualTo("accountId", targetAccount.id)
                                    .get(Source.SERVER),
                                firestore
                                    .collection("reservation")
                                    .whereEqualTo("accountId", targetAccount.id)
                                    .get(Source.SERVER),
                            )
                        Tasks.whenAllSuccess<QuerySnapshot>(queries)
                            .addOnSuccessListener { results ->
                                val entries = results[0].documents
                                val reservations = results[1].documents
                                val lotteryRefs =
                                    entries
                                        .mapNotNull { it.getString("lotteryId") }
                                        .distinct()
                                        .map { firestore.collection("lottery").document(it) }
                                if (entries.size + reservations.size + lotteryRefs.size + 1 > 500) {
                                    fail(
                                        IllegalStateException(
                                            "Too many related records to remove at once."
                                        )
                                    )
                                    return@addOnSuccessListener
                                }
                                firestore
                                    .runTransaction { transaction ->
                                        val savedAccount = transaction.get(accountRef)
                                        val savedEntries = entries.map {
                                            transaction.get(it.reference)
                                        }
                                        val savedLotteries = lotteryRefs.map { transaction.get(it) }
                                        val savedReservations = reservations.map {
                                            transaction.get(it.reference)
                                        }
                                        check(savedAccount.exists()) { "Profile no longer exists." }
                                        val removedCounts =
                                            savedEntries
                                                .filter { it.exists() }
                                                .groupingBy { it.getString("lotteryId") }
                                                .eachCount()
                                        savedLotteries
                                            .filter { it.exists() }
                                            .forEach { lottery ->
                                                val count =
                                                    lottery.getLong("entrantCount")
                                                        ?: error(
                                                            "Lottery entrant count is missing."
                                                        )
                                                transaction.update(
                                                    lottery.reference,
                                                    "entrantCount",
                                                    (count - (removedCounts[lottery.id] ?: 0))
                                                        .coerceAtLeast(0L),
                                                )
                                            }
                                        savedEntries
                                            .filter { it.exists() }
                                            .forEach { transaction.delete(it.reference) }
                                        savedReservations
                                            .filter {
                                                it.exists() && it.getBoolean("cancelled") != true
                                            }
                                            .forEach { reservation ->
                                                val end =
                                                    reservation
                                                        .getTimestamp("bookedPeriodEnd")
                                                        ?.toDate()
                                                        ?.time
                                                        ?: error("Reservation checkout is missing.")
                                                if (end > System.currentTimeMillis()) {
                                                    transaction.update(
                                                        reservation.reference,
                                                        mapOf(
                                                            "cancelled" to true,
                                                            "status" to "cancelled",
                                                            "cancelledBy" to adminId,
                                                            "cancelledAt" to
                                                                FieldValue.serverTimestamp(),
                                                            "cancellationReason" to
                                                                "Profile removed by administrator.",
                                                        ),
                                                    )
                                                }
                                            }
                                        transaction.delete(accountRef)
                                        true
                                    }
                                    .addOnSuccessListener { onSuccess() }
                                    .addOnFailureListener(fail)
                            }
                            .addOnFailureListener(fail)
                    }
                    .addOnFailureListener(onError)
            }
            .addOnFailureListener(onError)
    }

    /**
     * Removes the selected profile image without deleting the profile or clearing a newer image.
     *
     * @param account Profile containing the selected image.
     * @param path Selected image path.
     * @param onSuccess Called after Storage deletion and reference cleanup succeed.
     * @param onError Called if the selection is stale or removal fails.
     */
    open fun removeProfileImage(
        account: Account,
        path: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        if (path.isBlank()) {
            onError(IllegalArgumentException("No profile image exists."))
            return
        }
        val reference = accounts.document(account.id)
        reference
            .get(Source.SERVER)
            .addOnSuccessListener { document ->
                if (!document.exists() || document.getString("imagePath") != path) {
                    onError(
                        IllegalStateException(
                            "The selected profile image has changed. Refresh the list."
                        )
                    )
                    return@addOnSuccessListener
                }
                FirebaseConnector("accounts", firestore)
                    .removeImage(
                        path,
                        storage ?: Firebase.storage,
                        onSuccess = {
                            firestore
                                .runTransaction { transaction ->
                                    val current = transaction.get(reference)
                                    check(current.exists()) { "Profile no longer exists." }
                                    if (current.getString("imagePath") == path) {
                                        transaction.update(reference, "imagePath", "")
                                    }
                                    true
                                }
                                .addOnSuccessListener { onSuccess() }
                                .addOnFailureListener(onError)
                        },
                        onError = onError,
                    )
            }
            .addOnFailureListener(onError)
    }
}
