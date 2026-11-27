package com.example.zephyrcampsites

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth

class MainActivity : ComponentActivity() {
    private val accountsController = AccountsController()
    private val currentAccount = mutableStateOf<Account?>(null)
    private val accountLoading = mutableStateOf(true)
    private val accountError = mutableStateOf<String?>(null)
    private val profileDeletionInProgress = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val locationClient = LocationServices.getFusedLocationProviderClient(this)

        enableEdgeToEdge()
        initializeUser()

        setContent {
            ZephyrCampsitesTheme {
                CampsitesNavigation(
                    activityContext = this@MainActivity,
                    locationClient = locationClient,
                    accountsController = accountsController,
                    currentAccount = currentAccount,
                    accountLoading = accountLoading,
                    accountError = accountError.value,
                    recoveryUid = if (currentAccount.value?.id?.startsWith("auth_") == true) {
                        FirebaseAuth.getInstance().currentUser?.uid
                    } else {
                        null
                    },
                    profileDeletionInProgress = profileDeletionInProgress,
                    onRetryAccount = { initializeUser() },
                    onFinishActivity = { finish() }
                )
            }
        }
    }

    // [AI-03] OpenAI, ChatGPT, assistance with account initialization and callbacks.
    private fun initializeUser() {
        accountLoading.value = true
        accountError.value = null

        accountsController.initializeAccount(
            context = this,
            onSuccess = { account ->
                currentAccount.value = account
                accountLoading.value = false
            },
            onError = { exception ->
                currentAccount.value = null
                accountLoading.value = false

                if (exception is LegacyAccountLinkRequired) {
                    accountError.value = "Contact the profile administrator to verify your existing profile. " +
                        "Account ID: ${exception.accountId}\nFirebase UID: ${exception.uid}\n" +
                        "After the administrator links your profile, tap Retry."
                    return@initializeAccount
                }
                Toast.makeText(
                    this,
                    "Failed to load account: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}
