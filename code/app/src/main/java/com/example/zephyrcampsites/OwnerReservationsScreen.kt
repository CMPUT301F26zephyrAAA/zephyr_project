// Coordinates the owner's active reservations, details, chat, and cancellation.
package com.example.zephyrcampsites

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val OWNER_RESERVATION_REFRESH_INTERVAL_MS = 30_000L
private const val OWNER_RESERVATION_CLOCK_TICK_MS = 60_000L

// AI Citation [AI-39] (Bhav Saluja). OpenAI, Codex, generated owner reservation confirmation.
// AI Citation [AI-49] (Bhav Saluja)
// OpenAI, Codex, November 27, 2026. Connected owner reservation details to the shared chat.
// See Bhav's entry in the team's AI Documentation Google Doc.
@Composable
internal fun OwnerReservationsScreen(
    owner: Account,
    controller: ReservationsController,
    accountsController: AccountsController,
    onBack: () -> Unit
) {
    val chatModel = remember { ReservationChatModel() }
    val listState = rememberLazyListState()
    var bookings by remember(owner.id) { mutableStateOf<OwnerReservationList?>(null) }
    var loadedAt by remember(owner.id) { mutableLongStateOf(0L) }
    var serverNowMillis by remember(owner.id) { mutableLongStateOf(0L) }
    var isLoading by remember(owner.id) { mutableStateOf(true) }
    var isRefreshing by remember(owner.id) { mutableStateOf(false) }
    var errorMessage by remember(owner.id) { mutableStateOf<String?>(null) }
    var selectedReservationId by remember(owner.id) { mutableStateOf<String?>(null) }
    var showChat by remember(owner.id, selectedReservationId) { mutableStateOf(false) }
    var cancellationTarget by remember(owner.id) { mutableStateOf<OwnerReservation?>(null) }

    fun serverNow(): Long {
        val currentBookings = bookings ?: return 0L
        return currentBookings.serverMillis + SystemClock.elapsedRealtime() - loadedAt
    }

    fun loadReservations() {
        if (isRefreshing) {
            return
        }

        isRefreshing = true
        errorMessage = null
        controller.loadOwnerReservations(
            onSuccess = { result ->
                bookings = result
                loadedAt = SystemClock.elapsedRealtime()
                serverNowMillis = result.serverMillis
                isLoading = false
                isRefreshing = false
            },
            onError = {
                errorMessage = "Unable to load your active reservations."
                isLoading = false
                isRefreshing = false
            }
        )
    }

    LaunchedEffect(owner.id) {
        loadReservations()
        while (isActive) {
            delay(OWNER_RESERVATION_REFRESH_INTERVAL_MS)
            loadReservations()
        }
    }

    LaunchedEffect(bookings?.serverMillis, loadedAt) {
        while (bookings != null && isActive) {
            serverNowMillis = serverNow()
            delay(OWNER_RESERVATION_CLOCK_TICK_MS)
        }
    }

    val activeReservations = controller.filterActiveOwnerReservations(
        reservations = bookings?.reservations.orEmpty(),
        currentTimeMillis = serverNowMillis
    )

    val reservation = activeReservations.firstOrNull { it.id == selectedReservationId }
    LaunchedEffect(selectedReservationId, reservation) {
        if (selectedReservationId != null && reservation == null) {
            selectedReservationId = null
        }
    }

    BackHandler(enabled = reservation != null) {
        if (showChat) {
            showChat = false
        } else {
            selectedReservationId = null
        }
    }

    val chatReservation = reservation?.toChatReservation()
    if (showChat && reservation != null && chatReservation != null) {
        ReservationChatScreen(
            reservation = chatReservation,
            ownerId = owner.id,
            campsiteName = reservation.campsiteName,
            accountsController = accountsController,
            chatModel = chatModel,
            onBack = { showChat = false },
            ownerReply = true,
            entrantName = reservation.entrantName
        )
    } else if (reservation == null) {
        OwnerActiveReservationsView(
            reservations = activeReservations,
            currentTimeMillis = serverNowMillis,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage?.takeIf { bookings == null },
            onRefresh = ::loadReservations,
            onRetry = ::loadReservations,
            onViewDetails = { selectedReservationId = it.id },
            onBack = onBack,
            listState = listState
        )
    } else {
        OwnerReservationDetailsView(
            reservation = reservation,
            currentTimeMillis = serverNowMillis,
            errorMessage = errorMessage,
            onMessageEntrant = if (chatReservation != null) { { showChat = true } } else { null },
            onCancel = if (reservation.startMillis > serverNowMillis) {
                { cancellationTarget = reservation }
            } else {
                null
            },
            onBack = { selectedReservationId = null }
        )
    }

    cancellationTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { cancellationTarget = null },
            title = { Text("Cancel this reservation?") },
            text = {
                Text("The booking will stay in the reservation history as cancelled.")
            },
            dismissButton = {
                TextButton(onClick = { cancellationTarget = null }) {
                    Text("Keep reservation")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        cancellationTarget = null
                        if (target.startMillis <= serverNow()) {
                            errorMessage =
                                "This booking has reached check-in and can no longer be cancelled."
                        } else {
                            errorMessage = null
                            controller.cancelOwnerReservation(
                                reservation = target,
                                owner = owner,
                                onSuccess = {
                                    selectedReservationId = null
                                    loadReservations()
                                },
                                onError = {
                                    errorMessage =
                                        "Unable to cancel. Refresh to check the booking's current status."
                                }
                            )
                        }
                    }
                ) {
                    Text("Confirm cancellation")
                }
            }
        )
    }
}
