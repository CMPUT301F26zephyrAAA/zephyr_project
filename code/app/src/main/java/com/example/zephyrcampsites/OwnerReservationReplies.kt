package com.example.zephyrcampsites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import java.text.DateFormat

/** Opens a conversation from a confirmed reservation at one owned campsite. */
@Composable
internal fun OwnerReservationReplies(
    owner: Account,
    campsite: Campsite,
    reservationsController: ReservationsController,
    accountsController: AccountsController,
    onBack: () -> Unit
) {
    var selectedId by rememberSaveable(owner.id, campsite.id) { mutableStateOf<String?>(null) }
    var reservations by remember(owner.id, campsite.id) { mutableStateOf<List<ReservedUser>?>(null) }
    var loadFailed by remember(owner.id, campsite.id) { mutableStateOf(false) }
    var retry by remember(owner.id, campsite.id) { mutableIntStateOf(0) }

    DisposableEffect(owner.id, campsite.id, retry) {
        var active = true
        loadFailed = false
        reservationsController.getCampsiteReservations(
            owner = owner,
            campsiteId = campsite.id,
            onSuccess = { if (active) { reservations = it } },
            onError = { if (active) { loadFailed = true } }
        )
        onDispose { active = false }
    }

    val selected = reservations?.firstOrNull { it.id == selectedId }
    BackHandler(enabled = selected != null) { selectedId = null }
    if (selected != null) {
        ReservationChatScreen(
            reservation = selected,
            campsite = campsite,
            accountsController = accountsController,
            onBack = { selectedId = null },
            ownerReply = true
        )
    } else {
        ReservationReplyList(
            reservations = reservations,
            loadFailed = loadFailed,
            onSelectReservation = { selectedId = it.id },
            onRetry = { retry++ },
            onBack = onBack
        )
    }
}

/** Displays the reservations that an owner can open to read and reply. */
@Composable
internal fun ReservationReplyList(
    reservations: List<ReservedUser>?,
    loadFailed: Boolean,
    onSelectReservation: (ReservedUser) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val dates = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    LotteryListScreen("Reservation Messages", onBack) {
        when {
            loadFailed -> {
                item { Text("Unable to load reservations.") }
                item { Button(onClick = onRetry) { Text("Retry") } }
            }
            reservations == null -> item { CircularProgressIndicator() }
            reservations.isEmpty() -> item { Text("No confirmed reservations.") }
            else -> items(reservations, key = { it.id }) { reservation ->
                val start = reservation.bookedPeriodStart?.let(dates::format).orEmpty()
                val end = reservation.bookedPeriodEnd?.let(dates::format).orEmpty()
                LotteryListItem("Reservation ${reservation.id}\n$start to $end") {
                    onSelectReservation(reservation)
                }
            }
        }
    }
}
