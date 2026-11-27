// Displays an owner's bookings and requests confirmation before cancellation.
package com.example.zephyrcampsites

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

// AI Citation [AI-39] (Bhav Saluja). OpenAI, Codex, owner cancellation, 2026-11-27.
// See the team AI Documentation Google Doc.
@Composable
internal fun OwnerReservationsScreen(owner: Account, controller: ReservationsController, onBack: () -> Unit) {
    var bookings by remember(owner.id) { mutableStateOf<OwnerReservationList?>(null) }
    var loadedAt by remember(owner.id) { mutableStateOf(0L) }
    var error by remember(owner.id) { mutableStateOf<String?>(null) }
    var selected by remember(owner.id) { mutableStateOf<OwnerReservation?>(null) }
    var busy by remember(owner.id) { mutableStateOf(false) }
    val format = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    fun reload() {
        busy = true
        error = null
        controller.loadOwnerReservations(
            onSuccess = {
                bookings = it
                loadedAt = SystemClock.elapsedRealtime()
                busy = false
            },
            onError = {
                error = "Unable to load your reservations."
                busy = false
            }
        )
    }

    fun serverNow(): Long = (bookings?.serverMillis ?: 0L) + SystemClock.elapsedRealtime() - loadedAt

    LaunchedEffect(owner.id) { reload() }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text("Reservations", style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = ::reload, enabled = !busy) { Text("Refresh") }
        if (busy) {
            CircularProgressIndicator()
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val rows = bookings?.reservations.orEmpty().sortedBy { it.startMillis }
        if (bookings != null && rows.isEmpty()) {
            Text("No reservations for your campsites.")
        }
        val now = serverNow()
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows, key = { it.id }) { reservation ->
                val status = when {
                    reservation.cancelled -> "Cancelled"
                    reservation.endMillis <= now -> "Completed"
                    reservation.startMillis <= now -> "Checked in"
                    else -> "Upcoming"
                }
                Column {
                    Text(reservation.campsiteName.ifBlank { "Campsite" }, style = MaterialTheme.typography.titleMedium)
                    Text(reservation.entrantName.ifBlank { "Entrant name unavailable" })
                    Text("${format.format(Date(reservation.startMillis))} – ${format.format(Date(reservation.endMillis))}")
                    Text(status)
                    if (!reservation.cancelled && reservation.startMillis > now) {
                        TextButton(onClick = { selected = reservation }, enabled = !busy) {
                            Text("Cancel reservation")
                        }
                    }
                }
            }
        }
    }

    selected?.let { reservation ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("Cancel this reservation?") },
            text = { Text("The booking will stay in the reservation history as cancelled.") },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Keep reservation") } },
            confirmButton = {
                TextButton(onClick = {
                    selected = null
                    if (reservation.startMillis <= serverNow()) {
                        error = "This booking has reached check-in and can no longer be cancelled."
                    } else {
                        busy = true
                        error = null
                        controller.cancelOwnerReservation(
                            reservation, owner,
                            onSuccess = { reload() },
                            onError = {
                                busy = false
                                error = "Unable to cancel. Refresh to check the booking's current status."
                            }
                        )
                    }
                }) { Text("Confirm cancellation") }
            }
        )
    }
}
