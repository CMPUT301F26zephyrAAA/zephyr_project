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
     * Checks if an entrant has any active reservations.
     * An active reservation hasn't been cancelled & its checkout date hasnt passed
     * @param accountId ID of the entrant
     * @param onResult Returns true if an active reservation exists
     * @param onError called if firestore fails
     */
    open fun hasActiveReservations(
        accountId: String,
        onResult: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (accountId.isBlank() || accountId.contains("/")){
            onError(IllegalArgumentException("Invalid account ID"))
            return
        }
        firestore.collection("reservation")
            .whereEqualTo("accountId", accountId) //https://docs.oracle.com/en/database/oracle/backend-for-firebase/26.1/sbdjd/-oracle%20-backend%20for%20-firebase%20-android%20-s-d-k%20-reference,%20-release%2026.1.0/com.oracle.mobile.fusabase.oracledb/-query/where-equal-to.html
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val currentTime = System.currentTimeMillis()
                val hasActive = snapshot.documents.any {document ->
                    val cancelled = document.getBoolean("cancelled") == true
                    val checkoutTime = document.getTimestamp("bookedPeriodEnd")?.toDate()?.time
                    !cancelled && (checkoutTime == null || checkoutTime > currentTime)
                }
                onResult(hasActive)
            }
            .addOnFailureListener { exception -> onError(exception) }
    }

    // AI Citation [AI-12]
    // OpenAI, ChatGPT was used to assist with understanding
    // Firestore transactions, waiting-list cleanup, and
    // reservation handling during entrant profile deletion.
    // AI usage and citation documented in the GitHub Wiki.
    /**
     * Permanently deletes the profile of an entrant.
     *
     * Removes the entrant's waiting-list entries, updates the corresponding
     * lottery entrant counts, and cancels active or upcoming reservations.
     * Past reservations are preserved for historical records.
     *
     * This operation is intended for entrant self-deletion only.
     * Administrator profile deletion remains handled by deleteAccount().
     *
     * @param accountId Device-linked account ID of the entrant.
     * @param onSuccess Called after the deletion transaction commits.
     * @param onError Called if validation or Firestore deletion fails.
     */
    open fun deleteOwnAccount(
        accountId: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        // Step 1: Validate the account ID.
        if (accountId.isBlank() ||
            accountId.contains("/") ||
            accountId == "." ||
            accountId == ".."
        ) {
            onError(IllegalArgumentException("Invalid account ID."))
            return
        }

        val accountRef = accounts.document(accountId)

        // Step 2: Verify the entrant and lock the account.
        firestore.runTransaction { transaction ->
            val account = transaction.get(accountRef)

            check(account.exists()) {
                "Profile no longer exists."
            }

            check(account.getString("userType") == "entrant") {
                "Only entrants can delete their own profiles."
            }

            if (account.getBoolean("deletionPending") != true) {
                transaction.update(accountRef, "deletionPending", true)
            }

            true
        }.addOnSuccessListener {

            // Restore the account if the cleanup cannot complete.
            // Keep deletionPending set if cleanup fails.
            // A later deletion attempt can resume the operation.
            val fail: (Exception) -> Unit = { error ->
                onError(error)
            }

            // Step 3: Retrieve all related records.
            val queries = listOf(
                firestore.collection("waitingList")
                    .whereEqualTo("accountId", accountId)
                    .get(Source.SERVER),

                firestore.collection("reservation")
                    .whereEqualTo("accountId", accountId)
                    .get(Source.SERVER)
            )

            Tasks.whenAllSuccess<QuerySnapshot>(queries)
                .addOnSuccessListener { results ->

                    val entries = results[0].documents
                    val reservations = results[1].documents

                    // Step 4: Identify affected lotteries.
                    val lotteryIds = entries.mapNotNull {
                        it.getString("lotteryId")
                    }.distinct()

                    if (entries.any { it.getString("lotteryId").isNullOrBlank() }) {
                        fail(
                            IllegalStateException(
                                "Waiting-list entry has an invalid lottery ID."
                            )
                        )
                        return@addOnSuccessListener
                    }

                    val lotteryRefs = lotteryIds.map { lotteryId ->
                        firestore.collection("lottery")
                            .document(lotteryId)
                    }

                    // Avoid oversized transactions.
                    val operationCount =
                        entries.size +
                                reservations.size +
                                lotteryRefs.size + 1

                    if (operationCount > 500) {
                        fail(
                            IllegalStateException(
                                "Too many related records to delete at once."
                            )
                        )
                        return@addOnSuccessListener
                    }

                    // Step 5: Atomically update related documents.
                    firestore.runTransaction { transaction ->

                        // All transaction reads must precede writes.
                        val savedAccount = transaction.get(accountRef)

                        val savedEntries = entries.map {
                            transaction.get(it.reference)
                        }

                        val savedLotteries = lotteryRefs.map {
                            transaction.get(it)
                        }

                        val savedReservations = reservations.map {
                            transaction.get(it.reference)
                        }

                        check(savedAccount.exists()) {
                            "Profile no longer exists."
                        }

                        check(
                            savedAccount.getString("userType") == "entrant"
                        ) {
                            "Only entrants can delete their own profiles."
                        }

                        check(
                            savedAccount.getBoolean("deletionPending") == true
                        ) {
                            "Profile deletion is not pending."
                        }

                        // Step 6: Count waiting-list entries by lottery.
                        val removedCounts = savedEntries
                            .filter { it.exists() }
                            .groupingBy { it.getString("lotteryId") }
                            .eachCount()

                        // Update each lottery's entrant count.
                        savedLotteries
                            .filter { it.exists() }
                            .forEach { lottery ->

                                val currentCount =
                                    lottery.getLong("entrantCount")
                                        ?: error(
                                            "Lottery entrant count is missing."
                                        )

                                val removed =
                                    removedCounts[lottery.id] ?: 0

                                transaction.update(
                                    lottery.reference,
                                    "entrantCount",
                                    (currentCount - removed)
                                        .coerceAtLeast(0L)
                                )
                            }

                        // Step 7: Delete waiting-list entries.
                        savedEntries
                            .filter { it.exists() }
                            .forEach { entry ->
                                transaction.delete(entry.reference)
                            }

                        // Step 8: Cancel active/upcoming reservations and detach
// all historical reservations from the deleted profile.
                        val currentTime = System.currentTimeMillis()

                        savedReservations
                            .filter { it.exists() }
                            .forEach { reservation ->

                                val updates = mutableMapOf<String, Any>(
                                    "accountId" to FieldValue.delete()
                                )

                                // Only active/upcoming, non-cancelled reservations need cancellation.
                                if (reservation.getBoolean("cancelled") != true) {
                                    val endTime = reservation
                                        .getTimestamp("bookedPeriodEnd")
                                        ?.toDate()
                                        ?.time
                                        ?: error(
                                            "Cannot delete profile: a reservation has no checkout date"
                                        )

                                    if (endTime > currentTime) {
                                        updates["cancelled"] = true
                                        updates["status"] = "cancelled"
                                        updates["cancelledBy"] = accountId
                                        updates["cancelledAt"] = FieldValue.serverTimestamp()
                                        updates["cancellationReason"] =
                                            "Profile deleted by entrant."
                                    }
                                }

                                transaction.update(reservation.reference, updates)
                            }
                        // Step 9: Delete the entrant's account.
                        val profileImagePath = savedAccount.getString("imagePath").orEmpty()

                        transaction.delete(accountRef)

                        // Return the image path after the Firestore transaction commits.
                        profileImagePath
                    }
                        .addOnSuccessListener { imagePath ->
                            if (imagePath.isBlank()) {
                                onSuccess()
                            } else {
                                FirebaseConnector("accounts", firestore).removeImage(
                                    path = imagePath,
                                    storage = storage ?: Firebase.storage,
                                    onSuccess = {
                                        onSuccess()
                                    },
                                    onError = { exception ->
                                        android.util.Log.w(
                                            "AccountsModel",
                                            "Profile deleted, but profile image cleanup failed.",
                                            exception
                                        )
                                        onSuccess()
                                    }
                                )
                            }
                        }
                        .addOnFailureListener { exception ->
                            fail(exception)
                        }
                }
                .addOnFailureListener { exception ->
                    fail(exception)
                }
        }
            .addOnFailureListener { exception ->
                onError(exception)
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
