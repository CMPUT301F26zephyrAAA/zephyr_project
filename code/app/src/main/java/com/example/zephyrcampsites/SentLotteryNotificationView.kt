// Renders the owner's sent notification history and per-recipient delivery status.
// AI Citation [AI-40]
// OpenAI, Codex, US 02.10.02, 2026-11-27. Generated the Compose history and detail screens.
// See Bhav's entry in the team's AI Documentation Google Doc.
package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.tasks.Task
import java.text.DateFormat
import java.util.Date

/** Shows messages previously sent by the signed-in owner. */
@Composable
internal fun SentLotteryNotificationsScreen(
    accountId: String,
    controller: LotteryNotificationController,
    onBack: () -> Unit,
    onSelect: (String) -> Unit
) {
    var request by remember(accountId) { mutableStateOf<Task<List<SentLotteryNotification>>?>(null) }

    fun loadHistory() {
        request = null
        controller.loadSent(accountId).addOnCompleteListener { request = it }
    }

    LaunchedEffect(accountId) { loadHistory() }

    LotteryListScreen("Sent Notifications", onBack) {
        val result = request
        when {
            result == null -> item { NotificationStatus("Loading sent notifications…") }
            !result.isSuccessful -> item {
                SentNotificationLoadError("Unable to load sent notifications.", ::loadHistory)
            }
            result.result.isEmpty() -> item { Text("You haven’t sent any notifications yet.") }
            else -> items(result.result, key = { it.id }) { notification ->
                Card(
                    onClick = { onSelect(notification.id) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    SentNotificationMessage(notification, maxLines = 3)
                }
            }
        }
    }
}

/** Shows the selected message and delivery recorded for each intended recipient. */
@Composable
internal fun SentLotteryNotificationDetailsScreen(
    accountId: String,
    notificationId: String,
    controller: LotteryNotificationController,
    onBack: () -> Unit
) {
    var request by remember(accountId, notificationId) {
        mutableStateOf<Task<SentLotteryNotificationDetails>?>(null)
    }

    fun loadDetails() {
        request = null
        controller.loadSentDetails(accountId, notificationId).addOnCompleteListener { request = it }
    }

    LaunchedEffect(accountId, notificationId) { loadDetails() }

    LotteryListScreen("Notification Details", onBack) {
        val result = request
        when {
            result == null -> item { NotificationStatus("Loading notification details…") }
            !result.isSuccessful -> item {
                SentNotificationLoadError("Unable to load notification details.", ::loadDetails)
            }
            else -> {
                val details = result.result
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        SentNotificationMessage(details.notification)
                    }
                    Text(
                        "Recipients",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
                items(details.recipients) { recipient ->
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(recipient.name)
                        Text(
                            if (recipient.delivered) { "Delivered to inbox" } else { "Not delivered" },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

/** Renders the message and its send metadata in a history card. */
@Composable
private fun SentNotificationMessage(notification: SentLotteryNotification, maxLines: Int = Int.MAX_VALUE) {
    val timestamp = remember(notification.sentAtMillis) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(notification.sentAtMillis))
    }
    val delivery = notification.delivery
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(notification.message, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        Text(notification.lotteryName, style = MaterialTheme.typography.titleSmall)
        Text(timestamp, style = MaterialTheme.typography.bodySmall)
        Text("Delivered to ${delivery.delivered} of ${delivery.intended}", style = MaterialTheme.typography.bodySmall)
    }
}

/** Offers a retry when a history request fails. */
@Composable
private fun SentNotificationLoadError(message: String, onRetry: () -> Unit) {
    Column {
        NotificationStatus(message, isError = true)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}
