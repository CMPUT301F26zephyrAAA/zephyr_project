
package com.example.zephyrcampsites

import android.content.Context
import android.provider.Settings

/**
 * Controls account identification and initialization.
 *
 * Uses the Android device ID to retrieve or create
 * the user's account without requiring a login.
 */
class AccountsController {

    private val accountsModel = AccountsModel()
    private val profileValidator = ProfileValidator()

    /**
     * Retrieves the device identifier.
     *
     * @param context Android application context.
     * @return The Android device ID.
     */
    fun getDeviceId(context: Context): String? {
        //AI used for understanding how to implement this - https://chatgpt.com/share/6ae946a8-c8cc-83ed-96b3-65be0be7a44a
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
        ){
            val cleanName = name.trim()
            val cleanEmail = email.trim()
            val cleanPhone = phoneNumber.trim()
            val error = profileValidator.validate(cleanName, cleanEmail, cleanPhone)

            if (error != null){
                onError(error)
                return
            }

            val updatedAccount = Account(account.id)
            updatedAccount.userType = account.userType
            updatedAccount.name = cleanName
            updatedAccount.email = cleanEmail
            updatedAccount.phoneNumber = cleanPhone

            accountsModel.updateAccount(updatedAccount,
                onSuccess = {
                    onSuccess(updatedAccount)
                },

                onError = {exception ->
                    onError(exception.message ?: "Failed to updated profile")
                })
        }
    }

