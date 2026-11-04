package com.example.zephyrcampsites.Accounts

import android.content.Context
import android.provider.Settings
import com.example.zephyrcampsites.Accounts.Account
import com.example.zephyrcampsites.Accounts.UserType

/**
 * Controls account identification and initialization.
 * Uses the Android device ID to retrieve or create the user's account without requiring a login.
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
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }

    /**
     * Identifies the device and initializes its account.
     *
     * @param context Android application context.
     * @param onSuccess Returns the identified account.
     * @param onError Called if identification fails.
     */
    fun initializeAccount(context: Context, onSuccess: (Account) -> Unit, onError: (Exception) -> Unit) {
        val deviceId = getDeviceId(context)
        if (deviceId.isNullOrBlank()) {
            onError(Exception("Device ID not found"))
            return
        }

        accountsModel.getOrCreateAccount(deviceId, onResult = { account -> onSuccess(account) }, onError = { exception -> onError(exception) })
    }

    /**
     * Gets list of all accounts.
     *
     * @param onSuccess Returns the list of accounts.
     * @param onError Called if retrieving the list of accounts fails.
     */
    fun getAllAccounts(onSuccess: (List<Account>) -> Unit, onError: (Exception) -> Unit) {
        accountsModel.getAllAccounts(onSuccess, onError)
    }

    /**
     * Filter list of accounts by user type.
     *
     * @param userTypes List of user types to filter by.
     * @param onSuccess Returns the filtered list of accounts.
     * @param onError Called if filtering the list of accounts fails.
     */
    fun filterAccountsByType(userTypes: List<UserType>, onSuccess: (List<Account>) -> Unit, onError: (Exception) -> Unit) {
        accountsModel.getAllAccounts(
            onSuccess = { accounts ->
            val filteredAccounts = accounts.filter { account ->
                account.userTypes.containsAll(userTypes)
            }
            onSuccess(filteredAccounts) },
            onError = onError
        )
    }

    /**
     * Updates account profile details (name, email, phone number)
     *
     * @param account Account to update.
     * @param newName New name.
     * @param newEmail New email.
     * @param newPhoneNumber New phone number.
     * @param onSuccess Called when the account is successfully updated.
     * @param onError Called if updating the Account in the database fails.
     */
    fun updateAccountProfile(account: Account, newName: String, newEmail: String, newPhoneNumber: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        account.name = newName
        account.email = newEmail
        account.phoneNumber = newPhoneNumber
        accountsModel.createOrModifyAccount(account, onSuccess, onError)
    }

    /**
     * Updates notification preference for an account.
     *
     * @param account Account to update.
     * @param notifPref New notification preference.
     * @param onSuccess Called when the notification preference of account is successfully updated.
     * @param onError Called if updating the notification preference fails.
     */
    fun updateNotificationPreference(account: Account, notifPref: Boolean, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        account.notificationPreference = notifPref
        accountsModel.createOrModifyAccount(account, onSuccess, onError)
    }

    /**
     * Adds or updates a profile image path for an account.
     * @param account Account to update.
     * @param imagePath New profile image path.
     * @param onSuccess Called when the profile image path is successfully updated.
     * @param onError Called if updating the profile image path fails.
     */
    fun updateProfileImagePath(account: Account, imagePath: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        account.profileImagePath = imagePath
        accountsModel.createOrModifyAccount(account, onSuccess, onError)
    }

    /**
     * Deletes a profile image for an account.
     *
     * @param account Account to update.
     * @param onSuccess Called when the profile image is successfully deleted.
     * @param onError Called if deleting the profile image fails.
     */
    fun deleteProfileImage(account: Account, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        account.profileImagePath = ""
        accountsModel.createOrModifyAccount(account, onSuccess, onError)
    }

    /**
     * Deletes a profile entirely by deviceID.
     *
     * @param deviceID ID of the device.
     * @param onSuccess Called when the profile is successfully deleted.
     * @param onError Called if deleting the profile fails.
     */
    fun deleteProfile(deviceID: String, onSuccess: () -> Unit, onError: (Exception) -> Unit) {
        accountsModel.deleteAccount(deviceID, onSuccess, onError)
    }
}
