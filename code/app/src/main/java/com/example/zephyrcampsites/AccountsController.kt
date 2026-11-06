
package com.example.zephyrcampsites

import android.content.Context
import android.provider.Settings

/**
 * Controls account identification and initialization.
 *
 * Uses the Android device ID to retrieve or create
 * the user's account without requiring a login.
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

        accountsModel.getOrCreateAccount(deviceId, onResult = { account -> onSuccess(account) },
            onError = { exception -> onError(exception)
            }
        )
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
        if (account?.userType != "admin" || account.id.isBlank()) {
            onError(IllegalArgumentException("Only administrators can remove profiles."))
            return
        }
        if (targetAccount.id.isBlank() || targetAccount.id.contains("/")) {
            onError(IllegalArgumentException("Invalid profile ID."))
            return
        }
        accountsModel.deleteAccount(targetAccount, account.id, onSuccess, onError)
    }
}

