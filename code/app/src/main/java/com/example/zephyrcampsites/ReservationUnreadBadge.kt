package com.example.zephyrcampsites

import androidx.compose.material3.Badge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Shows the participant's unread status on a reservation row outside the chat screen. */
@Composable
internal fun ReservationUnreadBadge(
    reservation: ReservedUser, ownerAccountId: String, chatModel: ReservationChatModel, ownerReply: Boolean = false
) {
    var unread by remember(reservation, ownerAccountId, ownerReply) { mutableStateOf(false) }
    DisposableEffect(reservation, ownerAccountId, chatModel, ownerReply) {
        val listener = chatModel.observeUnread(reservation, ownerAccountId, ownerReply) { unread = it }
        onDispose { listener.remove() }
    }
    if (unread) {
        Badge(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) { Text("New message") }
    }
}
