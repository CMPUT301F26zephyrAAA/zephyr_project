package com.example.zephyrcampsites

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.LocationServices

class MainActivity : ComponentActivity() {
    private val accountsController = AccountsController()
    private val currentAccount = mutableStateOf<Account?>(null)
    private val accountLoading = mutableStateOf(true)
    private val profileDeletionInProgress = mutableStateOf(false)
    private val adminRefreshInProgress = mutableStateOf(false)
    private var adminRefreshVersion = 0

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
                    profileDeletionInProgress = profileDeletionInProgress,
                    adminRefreshInProgress = adminRefreshInProgress,
                    onRetryAccount = { initializeUser() },
                    onFinishActivity = { finish() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val account = currentAccount.value ?: return
        if (accountLoading.value || profileDeletionInProgress.value) {
            return
        }

        val version = ++adminRefreshVersion
        adminRefreshInProgress.value = true
        accountsController.refreshAdminAccess(account.id) { isAdmin ->
            if (version != adminRefreshVersion) {
                return@refreshAdminAccess
            }
            val latestAccount = currentAccount.value
            if (!profileDeletionInProgress.value && latestAccount?.id == account.id) {
                currentAccount.value = latestAccount.withVerifiedAdminRole(isAdmin)
            }
            adminRefreshInProgress.value = false
        }
    }

    // [AI-03] OpenAI, ChatGPT, assistance with account initialization and callbacks.
    private fun initializeUser() {
        accountLoading.value = true

        accountsController.initializeAccount(
            onSuccess = { account ->
                currentAccount.value = account
                accountLoading.value = false
            },
            onError = { exception ->
                currentAccount.value = null
                accountLoading.value = false

                Toast.makeText(
                    this,
                    "Failed to load account: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}
