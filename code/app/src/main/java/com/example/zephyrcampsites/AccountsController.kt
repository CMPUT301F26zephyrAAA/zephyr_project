
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
}
