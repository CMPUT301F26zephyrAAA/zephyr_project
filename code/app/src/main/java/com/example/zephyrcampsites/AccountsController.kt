
// Identifies the installation and coordinates profile operations.
package com.example.zephyrcampsites

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth

/**
 * Controls account identification and initialization.
 *
 * Uses the current Firebase Auth identity for profile operations.
 *
 * @param accountsModel Model used to read and update accounts.
 * @param currentUid Provides the UID of the signed-in Firebase account.
 */
class AccountsController(
    private val accountsModel: AccountsModel = AccountsModel(),
    private val currentUid: () -> String? = { FirebaseAuth.getInstance().currentUser?.uid }
) {
    private val profileValidator = ProfileValidator()

    /** Checks the active Firebase session before requesting a profile change. */
    private fun isOwnProfile(accountId: String): Boolean {
        val uid = currentUid() ?: return false
        return accountId == "auth_$uid"
    }

    /**
     * Loads the profile for the Firebase Auth session.
     * @param onSuccess Returns the signed-in account.
     * @param onError Called if authentication or profile initialization fails.
     */
    // [AI-04] OpenAI, ChatGPT assisted with the original account initialization.
    fun initializeAccount(onSuccess: (Account) -> Unit, onError: (Exception) -> Unit) {
        accountsModel.getOrCreateAccount(onSuccess, onError)
    }

    /**
     * Rechecks admin access for the current profile. A failed refresh gives no admin access.
     *
     * @param accountId Signed-in profile whose role is being checked.
     * @param onResult Receives whether the refreshed token grants admin access.
     */
    fun refreshAdminAccess(accountId: String, onResult: (Boolean) -> Unit) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || accountId != "auth_${user.uid}") {
            onResult(false)
            return
        }
        user.getIdToken(true).addOnCompleteListener { token ->
            val isAdmin = token.isSuccessful &&
                    token.result.claims["admin"] == true &&
                    isOwnProfile(accountId)
            onResult(isAdmin)
        }
    }

    /**
     * Loads the public owner name used in campsite details.
     * @param campsiteId Campsite being viewed.
     * @param onResult Receives the owner label, if available.
     * @param onError Receives a lookup error.
     */
    fun findCampsiteOwnerName(
        campsiteId: String, onResult: (String?) -> Unit, onError: (Exception) -> Unit
    ) {
        accountsModel.getCampsiteOwnerName(campsiteId, onResult, onError)
    }

    /**
     * Reads an existing account for authorized profile management.
     *
     * @param deviceId Original account ID to retrieve.
     * @param onResult Called with the account, or null if it does not exist.
     * @param onError Called if the ID is invalid or the account cannot be read.
     */
    fun findAccount(
        deviceId: String,
        onResult: (Account?) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (deviceId.isBlank() || deviceId.contains("/")) {
            onError(IllegalArgumentException("Invalid account ID."))
            return
        }

        accountsModel.getAccount(deviceID = deviceId, onResult = onResult, onError = onError)
    }

    /***
     * Validates and updates an entrant's profile
     * @param account Existing account to update
     * @param name Updated name
     * @param email Updated email
     * @param phoneNumber Updated phone number
     * @param onSuccess returns the updated account after saving
     * @param onError returns an error message if saving fails
     */

    fun updateProfile(
        account: Account,
        name: String,
        email: String,
        phoneNumber: String,
        onSuccess: (Account) -> Unit,
        onError: (String) -> Unit
    ) {
        val cleanName = name.trim()
        val cleanEmail = email.trim()
        val cleanPhone = phoneNumber.trim()
        val error = profileValidator.validate(cleanName, cleanEmail, cleanPhone)

        if (error != null) {
            onError(error)
            return
        }

        val updatedAccount = account.copy(name = cleanName, email = cleanEmail, phoneNumber = cleanPhone)
        accountsModel.updateAccount(updatedAccount,
            onSuccess = { onSuccess(updatedAccount) },
            onError = { exception -> onError(exception.message ?: "Failed to updated profile") }
        )
    }

    /**
     * Allows an entrant to add or replace their own profile image.
     * The selected image is compressed before being saved in Firestore.
     *
     * @param context Android application context.
     * @param account Current entrant account.
     * @param imageUri URI of the selected image.
     * @param onSuccess Returns the updated account.
     * @param onError Returns an error message if the operation fails.
     */
    fun updateOwnProfileImage(
        context: Context,
        account: Account,
        imageUri: Uri,
        onSuccess: (Account) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isOwnProfile(account.id)) {
            onError("You can only update your own profile image.")
            return
        }

        if ("entrant" !in account.userType) {
            onError("Only entrants can update their profile image.")
            return
        }

        val updatedAccount = account.copy()

        accountsModel.updateOwnProfileImage(
            context.applicationContext,
            updatedAccount,
            imageUri,
            onSuccess = {
                onSuccess(updatedAccount)
            },
            onError = { exception ->
                onError(
                    exception.message ?: "Failed to update profile image."
                )
            }
        )
    }

    /**
     * Allows an entrant to remove their own profile image.
     *
     * @param context Android application context.
     * @param account Current entrant account.
     * @param onSuccess Returns the updated account.
     * @param onError Returns an error message if removal fails.
     */
    fun removeOwnProfileImage(
        account: Account,
        onSuccess: (Account) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isOwnProfile(account.id)) {
            onError("You can only remove your own profile image.")
            return
        }

        if ("entrant" !in account.userType) {
            onError("Only entrants can remove their profile image.")
            return
        }

        val updatedAccount = account.copy()

        accountsModel.removeOwnProfileImage(
            updatedAccount,
            onSuccess = {
                onSuccess(updatedAccount)
            },
            onError = { exception ->
                onError(
                    exception.message ?: "Failed to remove profile image."
                )
            }
        )
    }

    /**
     * Saves a notification preference and returns the updated account after success.
     *
     * @param account Account whose preference is changing.
     * @param enabled Whether notifications should be enabled.
     * @param onSuccess Called with the account after the preference is saved.
     * @param onError Called if validation or saving fails.
     */
    fun updateNotificationPreference(
        account: Account,
        enabled: Boolean,
        onSuccess: (Account) -> Unit,
        onError: (String) -> Unit
    ) {
        if (account.id.isBlank() || account.id.contains("/")) {
            onError("Invalid account ID.")
            return
        }
        accountsModel.updateNotificationPreference(
            deviceId = account.id,
            enabled = enabled,
            onSuccess = { onSuccess(account.copy(notificationsEnabled = enabled)) },
            onError = { onError("Unable to save notification preference. Please try again.") }
        )
    }

    /**
     * Requests deletion of the profile owned by the current Firebase user.
     *
     * @param account Current user's account.
     * @param onSuccess Called after deletion succeeds.
     * @param onError Called when access or deletion fails.
     */
    fun deleteOwnProfile(
        account: Account,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        if (!isOwnProfile(account.id)) {
            onError(IllegalArgumentException("You can only delete your own profile."))
            return
        }

        accountsModel.deleteOwnAccount(account.id, onSuccess, onError)
    }

    /**
     * Removes a profile only for an identified administrator.
     *
     * @param account Current administrator account.
     * @param targetAccount Profile to remove.
     * @param onSuccess Called after deletion succeeds.
     * @param onError Called when access or deletion fails.
     */
    fun deleteProfile(
        account: Account?,
        targetAccount: Account,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        if (account == null || "admin" !in account.userType || account.id.isBlank()) {
            onError(IllegalArgumentException("Only administrators can remove profiles."))
            return
        }
        if (targetAccount.id.isBlank() || targetAccount.id.contains("/")) {
            onError(IllegalArgumentException("Invalid profile ID."))
            return
        }
        accountsModel.deleteAccount(targetAccount, account.id, onSuccess, onError)
    }

    /**
     * Requests removal of a selected profile image for an administrator.
     *
     * @param account Current administrator.
     * @param targetAccount Profile containing the image.
     * @param path Selected image path.
     * @param onSuccess Called after removal succeeds.
     * @param onError Called when access or removal fails.
     */
    fun removeProfileImage(
        account: Account?,
        targetAccount: Account,
        path: String,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        if (account == null || "admin" !in account.userType || account.id.isBlank()) {
            onError(IllegalArgumentException("Only administrators can remove images."))
            return
        }
        accountsModel.removeProfileImage(targetAccount, path, onSuccess, onError)
    }
}

