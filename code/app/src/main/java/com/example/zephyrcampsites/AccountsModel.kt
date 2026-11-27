// Reads accounts and coordinates authenticated profile creation and updates.
package com.example.zephyrcampsites
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentSnapshot
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Source
import com.google.android.gms.tasks.Tasks
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.storage
import android.net.Uri
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

/**
 * Model class for accounts. Reads/Writes the "accounts" Firestore collection.
 *
 * @param database Optional database used by local emulator tests.
 * @param storage Optional Storage instance used by local emulator tests.
 * **/

open class AccountsModel(private val database: FirebaseFirestore? = null, private val storage: FirebaseStorage? = null) {
    private val firestore by lazy { database ?: Firebase.firestore }
    private val functions by lazy { FirebaseFunctions.getInstance() }
    private val accounts by lazy { firestore.collection("accounts") } // connecting to firestore

    private companion object {
        private var startupSignIn: Task<FirebaseUser>? = null

        /** Shares an in-flight sign-in when the activity is recreated during startup. */
        @Synchronized
        fun authenticate(): Task<FirebaseUser> {
            val auth = FirebaseAuth.getInstance()
            auth.currentUser?.let { return Tasks.forResult(it) }
            startupSignIn?.takeUnless { it.isComplete }?.let { return it }
            return auth.signInAnonymously()
                .onSuccessTask { Tasks.forResult(checkNotNull(it.user)) }
                .also { startupSignIn = it }
        }
    }

    /**
     ** Retrieves an account using its Firebase Auth profile ID.
     * @param deviceID Firebase Auth profile ID.
     * @param onResult Returns the account, or null if not found
     * @param onError Called if Firestore cannot read the document
     */

    fun getAccount(deviceID: String, onResult: (Account?) -> Unit, onError: (Exception) -> Unit) {
        accounts.document(deviceID).get()
            .addOnSuccessListener { onResult(readAccount(it)) }
            .addOnFailureListener(onError)
    }

    /** Decodes a saved account, including profiles created before preference settings. */
    private fun readAccount(document: DocumentSnapshot): Account? {
        if (!document.exists()) {
            return null
        }
        return Account(document.id).apply {
            userType = accountRoles(document.get("userType"))
            name = document.getString("name") ?: ""
            email = document.getString("email") ?: ""
            phoneNumber = document.getString("phoneNumber") ?: ""
            notificationsEnabled = document.getBoolean("notificationsEnabled") ?: true
            imagePath = document.getString("imagePath") ?: ""
            compressedImage = document.getString("compressedImage") ?: ""
        }
    }

    // AI Citation [AI-39] (Bhav Saluja)
    // OpenAI, Codex, Firebase Auth startup follow-up, 2026-11-27. See the team AI Documentation Google Doc.
    /**
     * Creates or loads the authenticated user's own profile.
     * @param onResult Called with the profile at auth_<uid>.
     * @param onError Called if sign-in or profile initialization fails.
     */
    fun getOrCreateAccount(onResult: (Account) -> Unit, onError: (Exception) -> Unit) {
        authenticate()
            .onSuccessTask { user ->
                user.getIdToken(true).onSuccessTask { token ->
                    loadOrCreateProfile("auth_" + user.uid, token.claims["admin"] == true)
                }
            }
            .addOnSuccessListener(onResult)
            .addOnFailureListener(onError)
    }

    /** Loads a verified profile or creates a new Auth-owned profile atomically. */
    private fun loadOrCreateProfile(accountId: String, isAdmin: Boolean): Task<Account> {
        val profile = accounts.document(accountId)
        return firestore.runTransaction { transaction ->
            val document = transaction.get(profile)
            val saved = readAccount(document)
            val account = saved ?: Account(accountId)
            if (saved == null) {
                // [AI-02] OpenAI, ChatGPT, account field mapping.
                transaction.set(profile, mapOf(
                    "id" to account.id,
                    "userType" to account.userType,
                    "notificationsEnabled" to account.notificationsEnabled
                ))
            }
            account.userType = accountRoles(if (isAdmin) "admin" else null)
            account
        }
    }

    /** Retrieves just the public owner name for a campsite. */
    fun getCampsiteOwnerName(
        campsiteId: String, onResult: (String?) -> Unit, onError: (Exception) -> Unit
    ) {
        functions.getHttpsCallable("getAccountDisplayName").call(mapOf("campsiteId" to campsiteId))
            .addOnSuccessListener { onResult((it.data as? Map<*, *>)?.get("name") as? String) }
            .addOnFailureListener(onError)
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
     * Removes a previous profile image from Firebase Storage.
     * Skips deletion when no previous Storage image exists.
     */
    private fun removeOldProfileImage(
        oldImagePath: String,
        onComplete: () -> Unit
    ) {
        if (oldImagePath.isBlank()) {
            onComplete()
            return
        }

        FirebaseConnector("accounts", firestore).removeImage(
            path = oldImagePath,
            storage = storage ?: Firebase.storage,
            onSuccess = {
                onComplete()
            },
            onError = { exception ->
                android.util.Log.w(
                    "AccountsModel",
                    "Old profile image cleanup failed.",
                    exception
                )
                onComplete()
            }
        )
    }

    /**
     * Compresses and saves an entrant's profile image in Firestore.
     * Supports adding and replacing an existing profile image.
     *
     * @param context Android application context.
     * @param account Account whose image is being updated.
     * @param imageUri URI of the selected image.
     * @param onSuccess Called when the image is saved.
     * @param onError Called if compression or saving fails.
     */

    open fun updateOwnProfileImage(
        context: Context,
        account: Account,
        imageUri: Uri,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        val oldImagePath = account.imagePath

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val compressedImage =
                    ImageCompressionUtil.compressImage(
                        context = context.applicationContext,
                        imageUri = imageUri
                    )

                val updatedData = mapOf(
                    "compressedImage" to compressedImage,
                    "imagePath" to ""
                )

                accounts.document(account.id)
                    .update(updatedData)
                    .addOnSuccessListener {
                        account.compressedImage = compressedImage
                        account.imagePath = ""

                        removeOldProfileImage(oldImagePath) {
                            onSuccess()
                        }
                    }
                    .addOnFailureListener { exception ->
                        onError(exception)
                    }

            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                onError(exception)
            }
        }
    }


    /**
     * Removes the entrant's profile image and restores
     * the default profile icon.
     *
     * @param account Account whose image is being removed.
     * @param onSuccess Called when the image is removed.
     * @param onError Called if the operation fails.
     */

    open fun removeOwnProfileImage(
        account: Account,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (account.compressedImage.isBlank() &&
            account.imagePath.isBlank()
        ) {
            onError(
                IllegalArgumentException("No profile image exists.")
            )
            return
        }

        val oldImagePath = account.imagePath

        val updatedData = mapOf(
            "compressedImage" to "",
            "imagePath" to ""
        )

        accounts.document(account.id)
            .update(updatedData)
            .addOnSuccessListener {
                account.compressedImage = ""
                account.imagePath = ""

                removeOldProfileImage(oldImagePath) {
                    onSuccess()
                }
            }
            .addOnFailureListener { exception ->
                onError(exception)
            }
    }

    /**
     * Saves the notification preference without changing other account fields.
     *
     * @param deviceId Firebase Auth profile ID.
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
                            userType = accountRoles(document.get("userType")),
                            name = document.getString("name") ?: "",
                            email = document.getString("email") ?: "",
                            phoneNumber = document.getString("phoneNumber") ?: "",
                            notificationsEnabled =
                                document.getBoolean("notificationsEnabled") ?: true,
                            imagePath = document.getString("imagePath") ?: "",
                            compressedImage = document.getString("compressedImage") ?: "",
                        )
                    }
                )
            }
        }
    }

    open fun hasActiveReservations(
        accountId: String,
        onResult: (Boolean) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (accountId.isBlank() || '/' in accountId) {
            onError(IllegalArgumentException("Invalid account ID."))
            return
        }

        firestore.collection("reservation")
            .whereEqualTo("accountId", accountId)
            .get(Source.SERVER)
            .addOnSuccessListener { snapshot ->
                val now = System.currentTimeMillis()
                val hasActive = snapshot.documents.any { document ->
                    val cancelled = document.getBoolean("cancelled") == true
                    val checkoutTime = document.getTimestamp("bookedPeriodEnd")?.toDate()?.time
                    !cancelled && (checkoutTime == null || checkoutTime > now)
                }
                onResult(hasActive)
            }
            .addOnFailureListener { exception -> onError(exception) }
    }

    // AI Citation [AI-12]. OpenAI, ChatGPT, Firestore cleanup and reservation handling.
    // AI Citation [AI-39] (Bhav Saluja). Verified profile deletion. See the team AI documentation.
    /**
     * Deletes the verified entrant profile and releases its dependent records.
     * @param accountId Original account ID, matching the Firebase UID.
     * @param onSuccess Called after the transaction commits.
     * @param onError Called if the profile and cleanup cannot be removed together.
     */
    open fun deleteOwnAccount(
        accountId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit
    ) {
        deleteAccountWithCleanup(accountId, onSuccess, onError)
    }

    /**
     * Removes another profile and dependent records using verified administrator access.
     * @param targetAccount Profile to remove.
     * @param adminId Current administrator's account ID.
     * @param onSuccess Called after the transaction commits.
     * @param onError Called if authorization or cleanup fails.
     */
    open fun deleteAccount(
        targetAccount: Account, adminId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit
    ) {
        deleteAccountWithCleanup(targetAccount.id, onSuccess, onError)
    }

    private fun deleteAccountWithCleanup(
        accountId: String, onSuccess: () -> Unit, onError: (Exception) -> Unit
    ) {
        functions.getHttpsCallable("deleteProfile").call(mapOf("accountId" to accountId))
            .addOnSuccessListener { onSuccess() }
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
        if (account.compressedImage.isNotBlank() &&
            account.compressedImage == path
        ) {
            firestore.runTransaction { transaction ->
                val document = transaction.get(reference)

                check(document.exists()) {
                    "Profile no longer exists."
                }

                check(document.getString("compressedImage") == path) {
                    "The selected profile image has changed. Refresh the list."
                }

                transaction.update(
                    reference,
                    "compressedImage",
                    ""
                )

                true
            }
                .addOnSuccessListener {
                    onSuccess()
                }
                .addOnFailureListener(onError)

            return
        }
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
