// Builds the role selector and the available actions for each app mode.
package com.example.zephyrcampsites

import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun RoleSelectionMenu(
    isLoading: Boolean,
    hasAccount: Boolean,
    isAdmin: Boolean,
    accountError: String? = null,
    recoveryUid: String? = null,
    onSelectRole: (String) -> Unit,
    onRetryAccount: () -> Unit
) {
    var showRecovery by remember { mutableStateOf(false) }
    RolePage(
        actions = buildList {
            add(RoleAction("Entrant") { onSelectRole(ENTRANT) })
            add(RoleAction("Owner") { onSelectRole(OWNER) })
            if (isAdmin) {
                add(RoleAction("Admin") { onSelectRole(ADMIN) })
            }
            if (recoveryUid != null) {
                add(RoleAction("Link existing profile") { showRecovery = true })
            }
        },
        actionsEnabled = !isLoading && hasAccount,
        statusMessage = when {
            isLoading -> "Account loading…"
            !hasAccount -> accountError ?: "Unable to load account."
            else -> null
        },
        onRetry = if (!isLoading && !hasAccount) onRetryAccount else null
    )
    if (showRecovery && recoveryUid != null) {
        AlertDialog(
            onDismissRequest = { showRecovery = false },
            title = { Text("Link your existing profile") },
            text = {
                SelectionContainer {
                    Text("Ask the profile administrator to verify your existing profile. " +
                        "Share this Firebase UID through the administrator’s verified contact channel.\n\n$recoveryUid")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showRecovery = false
                    onRetryAccount()
                }) { Text("Reload profile") }
            },
            dismissButton = {
                TextButton(onClick = { showRecovery = false }) { Text("Close") }
            }
        )
    }
}

@Composable
internal fun RoleHomeMenu(
    role: String,
    isAdmin: Boolean,
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    onSwitchRole: (String) -> Unit
) {
    val actions = when (role) {
        ENTRANT -> listOf(
            "Browse Campsites" to roleRoute(ENTRANT, CampsiteScreens.SummaryView.route),
            "My Trips" to GeneralScreen.Trips.route,
            "Notifications" to GeneralScreen.LotteryNotifications.route,
            "Scan QR Code" to GeneralScreen.QRScan.route,
            "My Profile" to roleRoute(ENTRANT, GeneralScreen.Profile.route)
        )

        OWNER -> listOf(
            "My Campsites" to roleRoute(OWNER, CampsiteScreens.SummaryView.route),
            "Register Campsite" to GeneralScreen.RegisterCampsite.route,
            "View Lotteries" to "$OWNER/allLotteries",
            "View Reservations" to GeneralScreen.ViewReservations.route,
            "My Profile" to roleRoute(OWNER, GeneralScreen.Profile.route)
        )

        ADMIN -> listOf(
            "Browse Campsites" to GeneralScreen.AdminBrowseCampsite.route,
            "Browse Profiles" to GeneralScreen.AdminBrowseProfile.route,
            "Browse Images" to GeneralScreen.AdminBrowseImage.route,
            "Notification Logs" to GeneralScreen.NotificationLogs.route,
            "Message Logs" to GeneralScreen.MessageLogs.route,
            "Make Admin" to GeneralScreen.MakeAdmin.route,
            "My Profile" to roleRoute(ADMIN, GeneralScreen.Profile.route)
        )

        else -> error("Unknown role: $role")
    }

    RoleMenuWithSwitcher(
        role = role,
        actions = actions.map { (label, route) ->
            RoleAction(label) { onNavigate(route) }
        },
        isAdmin = isAdmin,
        onBack = onBack,
        openRoleGraph = onSwitchRole
    )
}
