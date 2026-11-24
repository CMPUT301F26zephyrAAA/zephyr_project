package com.example.zephyrcampsites

import androidx.compose.runtime.Composable

@Composable
internal fun RoleSelectionMenu(
    isLoading: Boolean,
    hasAccount: Boolean,
    isAdmin: Boolean,
    onSelectRole: (String) -> Unit,
    onRetryAccount: () -> Unit
) {
    RolePage(
        actions = buildList {
            add(RoleAction("Entrant") { onSelectRole(ENTRANT) })
            add(RoleAction("Owner") { onSelectRole(OWNER) })
            if (isAdmin) {
                add(RoleAction("Admin") { onSelectRole(ADMIN) })
            }
        },
        actionsEnabled = !isLoading && hasAccount,
        statusMessage = when {
            isLoading -> "Account loading…"
            !hasAccount -> "Unable to load account."
            else -> null
        },
        onRetry = if (!isLoading && !hasAccount) onRetryAccount else null
    )
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