package com.example.zephyrcampsites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.zephyrcampsites.ui.theme.ProfileMenuGreen
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Loads the other participant and connects to the conversation for the selected booking.
 * @param reservation Reservation opened from My Trips.
 * @param campsite Campsite associated with the reservation, if available.
 * @param accountsController Reads the other participant's account from Firebase.
 * @param onBack Returns to the previous reservation screen.
 * @param ownerReply Opens the conversation for the owner to reply to the entrant.
 */
@Composable
internal fun ReservationChatScreen(
    reservation: ReservedUser,
    campsite: Campsite?,
    accountsController: AccountsController,
    onBack: () -> Unit,
    ownerReply: Boolean = false
) {
    val ownerId = campsite?.ownerID.orEmpty()
    val participantId = if (ownerReply) reservation.accountId else ownerId
    val participantLabel = if (ownerReply) "Entrant" else "Campsite Owner"
    val chatModel = remember { ReservationChatModel() }
    var participantName by remember(participantId) { mutableStateOf<String?>(null) }
    var session by remember(reservation.id, ownerId) { mutableStateOf<ReservationChatSession?>(null) }
    var messages by remember(reservation.id, ownerId) { mutableStateOf(emptyList<ReservationMessage>()) }
    var chatStatus by remember(reservation.id, ownerId) { mutableStateOf("") }
    var draft by rememberSaveable(reservation.id, ownerId) { mutableStateOf("") }
    var sending by remember(reservation.id, ownerId) { mutableStateOf(false) }
    var sendError by remember(reservation.id, ownerId) { mutableStateOf<String?>(null) }
    var newMessageId by remember(reservation.id, ownerId) { mutableStateOf<String?>(null) }

    DisposableEffect(participantId, accountsController) {
        var active = true
        if (participantId.isNotBlank()) {
            accountsController.findAccount(
                deviceId = participantId,
                onResult = { participant ->
                    if (active) {
                        participantName = participant?.name
                    }
                },
                onError = {}
            )
        }
        onDispose { active = false }
    }

    DisposableEffect(reservation, ownerId, ownerReply) {
        var active = true
        session = null
        chatStatus = "Unable to open this conversation."
        if (ownerId.isNotBlank()) {
            chatStatus = "Loading messages…"
            chatModel.open(reservation, ownerId, ownerReply)
                .addOnSuccessListener { if (active) { session = it } }
                .addOnFailureListener { error ->
                    if (active) {
                        chatStatus = (error as? ReservationChatLinkRequired)?.message
                            ?: "Unable to open this conversation."
                    }
                }
        }
        onDispose { active = false }
    }

    val openChat = session
    DisposableEffect(openChat) {
        var seenMessageIds: Set<String>? = null
        val registration = openChat?.let { conversation ->
            chatModel.listen(
                conversation,
                onMessages = { updated ->
                    seenMessageIds?.let { seen ->
                        val incoming = updated.lastOrNull {
                            !it.from(conversation.userAccountId, conversation.userUid) && it.id !in seen
                        }
                        if (incoming != null) {
                            newMessageId = incoming.id
                        }
                    }
                    seenMessageIds = updated.map { it.id }.toSet()
                    messages = updated
                    chatStatus = ""
                },
                onError = {
                    messages = emptyList()
                    chatStatus = "Unable to load messages."
                }
            )
        }
        onDispose { registration?.remove() }
    }

    val canReply = !ownerReply || messages.any { !it.from(openChat?.userAccountId, openChat?.userUid) }
    val canCompose = openChat != null && chatStatus.isEmpty() && canReply && !sending
    ReservationChatView(
        reservation = reservation,
        campsiteName = campsite?.name.orEmpty(),
        participantName = participantName,
        participantLabel = participantLabel,
        status = chatStatus.ifBlank {
            if (canReply) "" else "Waiting for the entrant's first message."
        },
        onBack = onBack,
        messages = messages,
        userUid = openChat?.userUid,
        userAccountId = openChat?.userAccountId,
        newMessageId = newMessageId,
        draft = draft,
        canCompose = canCompose,
        sendError = sendError,
        onDraftChange = {
            if (it.length <= MAX_RESERVATION_MESSAGE_LENGTH) {
                draft = it
                sendError = null
            }
        },
        onSend = {
            if (openChat != null && canCompose && draft.isNotBlank()) {
                sending = true
                sendError = null
                chatModel.send(openChat, draft)
                    .addOnSuccessListener {
                        draft = ""
                        sending = false
                    }
                    .addOnFailureListener {
                        sendError = "Could not send your message. Try again."
                        sending = false
                    }
            }
        }
    )
}

/**
 * Displays reservation context, messages, and the text composer.
 * @param reservation Supplies the booking dates shown in the header.
 * @param campsiteName Display name of the reserved campsite.
 * @param participantName Display name of the other participant.
 * @param participantLabel Name to display if the other participant has no profile name.
 * @param status Conversation loading status, if any.
 * @param onBack Returns to the reservation details.
 * @param messages Conversation history in order.
 * @param userUid Firebase UID used to identify outgoing messages.
 * @param newMessageId Highlights the most recent newly received message.
 * @param draft Text waiting to be sent.
 * @param canCompose Whether the conversation is ready for input.
 * @param sendError Error for the most recent send attempt, if any.
 * @param onDraftChange Updates the message draft.
 * @param onSend Sends the draft.
 */
@Composable
internal fun ReservationChatView(
    reservation: ReservedUser,
    campsiteName: String,
    participantName: String?,
    status: String,
    onBack: () -> Unit,
    messages: List<ReservationMessage> = emptyList(),
    userUid: String? = null,
    userAccountId: String? = null,
    newMessageId: String? = null,
    draft: String = "",
    canCompose: Boolean = false,
    sendError: String? = null,
    onDraftChange: (String) -> Unit = {},
    onSend: () -> Unit = {},
    participantLabel: String = "Campsite Owner"
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy", Locale.getDefault()) }
    val startDate = reservation.bookedPeriodStart?.let { dateFormat.format(it) } ?: "N/A"
    val endDate = reservation.bookedPeriodEnd?.let { dateFormat.format(it) } ?: "N/A"
    val listState = rememberLazyListState()

    LaunchedEffect(messages.lastOrNull()?.id) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("MESSAGES", style = MaterialTheme.typography.labelLarge)
        Text(
            text = participantName?.takeIf { it.isNotBlank() } ?: participantLabel,
            style = MaterialTheme.typography.headlineSmall
        )
        Text("${campsiteName.ifBlank { "Campsite unavailable" }} · $startDate to $endDate")
        HorizontalDivider()

        if (status.isNotBlank()) {
            Text(status)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val outgoing = message.from(userAccountId, userUid)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start
                ) {
                    if (message.id == newMessageId) {
                        Text("New message", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(
                        text = message.text,
                        modifier = Modifier.widthIn(max = 280.dp)
                            .background(
                                if (outgoing) ProfileMenuGreen else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (outgoing) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (sendError != null) {
            Text(sendError, color = MaterialTheme.colorScheme.error)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message") },
                enabled = canCompose,
                maxLines = 3
            )
            Button(onClick = onSend, enabled = canCompose && draft.isNotBlank()) { Text("SEND") }
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) { Text("BACK") }
    }
}
