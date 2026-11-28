// Renders the owner notification composer and the entrant's in-app inbox.
// AI Citation [AI-39]
// OpenAI, Codex, US 02.10.01 waitlist notifications, 2026-11-27. Generated the Compose notification screens.
// See the Bhav Saluja entry prepared for the GitHub Wiki in README.md.
package com.example.zephyrcampsites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Lets the campsite owner write a message and choose current waitlisted entrants. */
@Composable
internal fun NotifyLotteryEntrantsScreen(
    accountId: String,
    lottery: Lottery,
    controller: LotteryNotificationController,
    onBack: () -> Unit,
    onViewSent: () -> Unit
) {
    var recipients by remember { mutableStateOf<List<LotteryNotificationRecipient>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmation by rememberSaveable { mutableStateOf<String?>(null) }
    var message by rememberSaveable { mutableStateOf("") }
    var allEntrants by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var requestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    val focus = LocalFocusManager.current

    fun draftChanged() {
        requestId = UUID.randomUUID().toString()
        confirmation = null
    }

    fun loadRecipients() {
        loading = true
        error = null
        controller.loadRecipients(accountId, lottery.id)
            .addOnSuccessListener { current ->
                recipients = current
                val currentIds = current.map { it.accountId }.toSet()
                val available = selected.filter { it in currentIds }
                if (available.size != selected.size) {
                    selected = ArrayList(available)
                    draftChanged()
                }
                loading = false
            }
            .addOnFailureListener {
                recipients = emptyList()
                loading = false
                error = lotteryNotificationError(it, "Unable to load the waitlist. Try again.")
            }
    }

    LaunchedEffect(accountId, lottery.id) { loadRecipients() }
    BackHandler(enabled = sending) {}
    val canSend = !loading && !sending && recipients.isNotEmpty() &&
        lotteryNotificationValidationError(message, allEntrants || selected.isNotEmpty()) == null

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack, enabled = !sending) { Text("Back") }
        Text("Notify Entrants", style = MaterialTheme.typography.headlineSmall)
        Text(lottery.lotteryName, style = MaterialTheme.typography.titleMedium)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                OutlinedTextField(
                    value = message,
                    onValueChange = {
                        message = it
                        draftChanged()
                    },
                    label = { Text("Message") },
                    supportingText = { Text("${message.trim().length} / $MAX_LOTTERY_NOTIFICATION_LENGTH") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6,
                    enabled = !sending,
                    isError = message.trim().length > MAX_LOTTERY_NOTIFICATION_LENGTH
                )
            }
            when {
                loading -> item { Text("Loading waitlist…") }
                recipients.isEmpty() && error == null -> item { Text("The waitlist is empty.") }
                recipients.isNotEmpty() -> {
                    item {
                        Column(Modifier.selectableGroup()) {
                            for ((everyone, label) in listOf(
                                true to "Everyone on the waitlist",
                                false to "Select individual entrants"
                            )) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().selectable(
                                        selected = allEntrants == everyone,
                                        enabled = !sending,
                                        role = Role.RadioButton,
                                        onClick = {
                                            if (allEntrants != everyone) {
                                                allEntrants = everyone
                                                draftChanged()
                                            }
                                        }
                                    ).padding(vertical = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = allEntrants == everyone, onClick = null)
                                    Text(label)
                                }
                            }
                        }
                    }
                    if (!allEntrants) {
                        items(recipients, key = { it.accountId }) { recipient ->
                            Row(
                                modifier = Modifier.fillMaxWidth().toggleable(
                                    value = recipient.accountId in selected,
                                    enabled = !sending,
                                    role = Role.Checkbox,
                                    onValueChange = { checked ->
                                        selected = if (checked) {
                                            ArrayList(selected + recipient.accountId)
                                        } else {
                                            ArrayList(selected - recipient.accountId)
                                        }
                                        draftChanged()
                                    }
                                ).padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = recipient.accountId in selected, onCheckedChange = null)
                                Text(recipient.name)
                            }
                        }
                    }
                }
            }
        }
        error?.let {
            NotificationStatus(it, isError = true)
        }
        if (error != null || (!loading && recipients.isEmpty())) {
            TextButton(onClick = ::loadRecipients, enabled = !loading && !sending) {
                Text("Reload Waitlist")
            }
        }
        // AI Citation [AI-40]
        // OpenAI, Codex, US 02.10.02, 2026-11-27. Added the post-send history link.
        // See Bhav's entry in the team's AI Documentation Google Doc.
        confirmation?.let {
            NotificationStatus(it)
            TextButton(onClick = onViewSent) { Text("View Sent Notifications") }
        }
        Button(
            onClick = {
                focus.clearFocus()
                sending = true
                error = null
                confirmation = null
                controller.send(accountId, lottery.id, message, allEntrants, selected.toSet(), requestId)
                    .addOnSuccessListener { result ->
                        message = ""
                        draftChanged()
                        sending = false
                        confirmation = "Notification saved. Delivered to ${result.delivered} of " +
                            "${result.intended} waitlisted entrants."
                    }
                    .addOnFailureListener {
                        sending = false
                        error = lotteryNotificationError(it, "Unable to send the notification. Try again.")
                    }
            },
            enabled = canSend,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (sending) { "Sending…" } else { "Send Notification" })
        }
    }
}

/** Displays messages delivered to this entrant when they open or refresh the inbox. */
@Composable
internal fun LotteryNotificationsScreen(
    account: Account,
    controller: LotteryNotificationController,
    onBack: () -> Unit
) {
    var messages by remember(account.id) { mutableStateOf<List<ReceivedLotteryNotification>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val timestampFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    fun loadReceived() {
        loading = true
        error = null
        controller.loadReceived(account.id)
            .addOnSuccessListener {
                messages = it
                loading = false
            }
            .addOnFailureListener {
                messages = emptyList()
                loading = false
                error = lotteryNotificationError(it, "Unable to load notifications. Try again.")
            }
    }

    LaunchedEffect(account.id, account.notificationsEnabled) {
        if (account.notificationsEnabled) {
            loadReceived()
        }
    }
    LotteryListScreen(title = "Notifications", onBack = onBack) {
        if (account.notificationsEnabled) {
            item {
                TextButton(onClick = ::loadReceived, enabled = !loading) { Text("Refresh") }
            }
            item {
                when {
                    loading -> Text("Loading notifications…")
                    error != null -> NotificationStatus(checkNotNull(error), isError = true)
                    messages.isEmpty() -> Text("No notifications yet.")
                }
            }
            items(messages, key = { it.id }) { message ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(message.lotteryName, style = MaterialTheme.typography.titleMedium)
                        Text(message.message)
                        Text(
                            timestampFormat.format(Date(message.sentAtMillis)),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            item { Text("Notifications are turned off in Settings.") }
        }
    }
}

/** Announces feedback and lets a user copy account setup details. */
@Composable
internal fun NotificationStatus(message: String, isError: Boolean = false) {
    SelectionContainer {
        Text(
            message,
            color = if (isError) { MaterialTheme.colorScheme.error } else { MaterialTheme.colorScheme.onSurface },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
        )
    }
}
