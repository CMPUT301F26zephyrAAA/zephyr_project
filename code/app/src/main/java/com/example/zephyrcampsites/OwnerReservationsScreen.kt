// Coordinates the owner's active reservations list, details screen, and safe cancellation flow.
package com.example.zephyrcampsites

import android.os.SystemClock
import androidx.activity.compose.BackHandler
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

// AI Citation [AI-39] (Bhav Saluja). OpenAI, Codex, owner cancellation, 2026-11-27.
// See the team AI Documentation Google Doc.
@Composable
internal fun OwnerReservationsScreen(
    owner: Account,
    controller: ReservationsController,
    onBack: () -> Unit
) {
    var bookings by remember(owner.id) { mutableStateOf<OwnerReservationList?>(null) }
    var loadedAt by remember(owner.id) { mutableLongStateOf(0L) }
    var serverNowMillis by remember(owner.id) { mutableLongStateOf(0L) }
    var isLoading by remember(owner.id) { mutableStateOf(true) }
    var isRefreshing by remember(owner.id) { mutableStateOf(false) }
    var errorMessage by remember(owner.id) { mutableStateOf<String?>(null) }
    var selectedReservation by remember(owner.id) { mutableStateOf<OwnerReservation?>(null) }
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

    LaunchedEffect(activeReservations, selectedReservation?.id) {
        if (selectedReservation != null &&
            activeReservations.none { it.id == selectedReservation?.id }
        ) {
            selectedReservation = null
        }
    }

    BackHandler(enabled = selectedReservation != null) {
        selectedReservation = null
    }

    val reservation = selectedReservation
    if (reservation == null) {
        OwnerActiveReservationsView(
            reservations = activeReservations,
            currentTimeMillis = serverNowMillis,
            isLoading = isLoading,
            isRefreshing = isRefreshing,
            errorMessage = errorMessage?.takeIf { bookings == null },
            onRefresh = ::loadReservations,
            onRetry = ::loadReservations,
            onViewDetails = { selectedReservation = it },
            onBack = onBack
        )
    } else {
        OwnerReservationDetailsView(
            reservation = reservation,
            currentTimeMillis = serverNowMillis,
            errorMessage = errorMessage,
            onCancel = if (reservation.startMillis > serverNowMillis) {
                { cancellationTarget = reservation }
            } else {
                null
            },
            onBack = { selectedReservation = null }
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
                                    selectedReservation = null
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
