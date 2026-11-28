// Defines the routes shared by the app's navigation graphs.
package com.example.zephyrcampsites

import android.net.Uri

sealed class GeneralScreen(val route: String) {
    data object RoleMenu : GeneralScreen("GeneralScreen/RoleMenu")
    data object EntrantMenu : GeneralScreen("GeneralScreen/EntrantMenu")
    data object OwnerMenu : GeneralScreen("GeneralScreen/OwnerMenu")
    data object AdminMenu : GeneralScreen("GeneralScreen/AdminMenu")
    data object Trips : GeneralScreen("entrant/trips")
    data object LotteryNotifications : GeneralScreen("entrant/lottery-notifications")
    data object SentNotifications : GeneralScreen("owner/sent-notifications")
    data object SentNotificationDetails : GeneralScreen("owner/sent-notifications/{notificationId}")
    data object ViewReservations : GeneralScreen("owner/reservations")
    data object NotificationLogs : GeneralScreen("admin/notification-logs")
    data object MessageLogs : GeneralScreen("admin/message-logs")
    data object MakeAdmin : GeneralScreen("admin/make-admin")
    data object QRScan : GeneralScreen("GeneralScreen/QRScan")
    data object Profile : GeneralScreen("GeneralScreen/Profile")
    data object LotterySettings : GeneralScreen("GeneralScreen/LotterySettings")
    data object RegisterCampsite : GeneralScreen("GeneralScreen/RegisterCampsite")
    data object PersonalInformation : GeneralScreen("GeneralScreen/PersonalInformation")
    data object Policy : GeneralScreen("GeneralScreen/Policy")
    data object Settings : GeneralScreen("GeneralScreen/Settings")

    data object EditCampsite : GeneralScreen("EditCampsite/{id}") {
        fun edit(id: Int) = "EditCampsite/$id"
    }

    data object AdminBrowseCampsite : GeneralScreen("GeneralScreen/AdminBrowseCampsite")
    data object AdminBrowseProfile : GeneralScreen("GeneralScreen/AdminBrowseProfile")
    data object AdminBrowseImage : GeneralScreen("GeneralScreen/AdminBrowseImage")

    data object AdminProfileDetail : GeneralScreen("GeneralScreen/AdminProfileDetail/{id}") {
        fun createRoute(id: String) = "GeneralScreen/AdminProfileDetail/${Uri.encode(id)}"
    }
}

internal const val ENTRANT = "entrant"
internal const val OWNER = "owner"
internal const val ADMIN = "admin"

internal fun roleRoute(role: String, route: String): String = "$role/$route"
