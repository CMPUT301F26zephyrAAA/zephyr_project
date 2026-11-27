
// Identifies the installation and coordinates profile operations.
package com.example.zephyrcampsites

import android.content.Context
import android.provider.Settings
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth

/**
 * Controls account identification and initialization.
 *
 * Uses Firebase Auth at startup and the Android device ID to find legacy profiles.
 *
 * @param accountsModel Model used to read and update accounts.
 */
class AccountsController(private val accountsModel: AccountsModel = AccountsModel()) {
    private val profileValidator = ProfileValidator()

    /**
     * Retrieves the device identifier.
     *
     * @param context Android application context.
     * @return The Android device ID.
     */
    //AI Citation [AI-01]
    //Open AI, ChatGPT was used for assistance understanding Android device identification
    //AI Usage and citation in WIKI
    fun getDeviceId(context: Context): String? {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
    }

    /** Matches a profile to this installation before requesting a profile change. */
    private fun isOwnProfile(context: Context, accountId: String): Boolean {
        if (accountId.isBlank() || '/' in accountId) {
            return false
        }
        if (accountId.startsWith("auth_")) {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
            return accountId == "auth_$uid"
        }
        return accountId == getDeviceId(context)
    }

    /**
     * Identifies the device and initializes its account.
     *
     * @param context Android application context.
     * @param onSuccess Returns the identified account.
     * @param onError Called if identification fails.
     */
    // [AI-04] OpenAI, ChatGPT, assistance with device-based account initialization and Firebase callback handling.
    fun initializeAccount(
        context: Context,
        onSuccess: (Account) -> Unit,
        onError: (Exception) -> Unit
    ) {

        val deviceId = getDeviceId(context)

        if (deviceId.isNullOrBlank()) {
            onError(Exception("Device ID not found"))
            return
        }

        accountsModel.getOrCreateAccount(deviceId, onResult = onSuccess, onError = onError)
    }

    /**
     * Reads an existing account without creating one.
     *
     * @param deviceId Device ID of the account to retrieve.
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
        if (!isOwnProfile(context, account.id)) {
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
        context: Context,
        account: Account,
        onSuccess: (Account) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isOwnProfile(context, account.id)) {
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
     * Requests deletion of the profile associated with this device.
     *
     * @param account Current administrator account.
     * @param targetAccount Profile to remove.
     * @param onSuccess Called after deletion succeeds.
     * @param onError Called when access or deletion fails.
     */
    fun deleteOwnProfile(
        context: Context,
        account: Account,
        onSuccess: () -> Unit,
        onError: (Exception) -> Unit,
    ) {
        if (!isOwnProfile(context, account.id)) {
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

