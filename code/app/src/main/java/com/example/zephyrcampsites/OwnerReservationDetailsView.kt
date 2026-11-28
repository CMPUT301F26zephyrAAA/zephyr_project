// Renders details for an active owner reservation selected from the owner list.
package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Displays detailed information for an active reservation owned by the current owner. */
@Composable
internal fun OwnerReservationDetailsView(
    reservation: OwnerReservation,
    currentTimeMillis: Long,
    errorMessage: String?,
    onMessageEntrant: (() -> Unit)?,
    onCancel: (() -> Unit)?,
    onBack: () -> Unit
) {
    val dateFormat = remember {
        SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
    }
    val startDate = remember(reservation.startMillis) { Date(reservation.startMillis) }
    val endDate = remember(reservation.endMillis) { Date(reservation.endMillis) }
    val status = if (reservation.startMillis > currentTimeMillis) {
        "Confirmed · Upcoming"
    } else {
        "Confirmed · Current stay"
    }
    val duration = remember(startDate, endDate) {
        calculateReservationNights(startDate, endDate)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("‹ Back")
        }

        Text(
            text = "Reservation Details",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Campsite", style = MaterialTheme.typography.labelMedium)
                Text(
                    text = reservation.campsiteName.ifBlank { "Campsite" },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Text(
            text = "Entrant Information",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        ReservationDetailItem(
            label = "Entrant name",
            value = reservation.entrantName.ifBlank { "Entrant name unavailable" }
        )

        HorizontalDivider()

        Text(
            text = "Reservation Information",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        ReservationDetailItem(label = "Check-in", value = dateFormat.format(startDate))
        ReservationDetailItem(label = "Check-out", value = dateFormat.format(endDate))
        ReservationDetailItem(
            label = "Duration",
            value = if (duration == 1) "1 night" else "$duration nights"
        )
        ReservationDetailItem(label = "Reservation status", value = status)

        HorizontalDivider()
        ReservationDetailItem(label = "Reservation ID", value = reservation.id)

        onMessageEntrant?.let { messageEntrant ->
            Button(onClick = messageEntrant, modifier = Modifier.fillMaxWidth()) {
                Text("Message Entrant")
            }
        }
        onCancel?.let { cancel ->
            Button(onClick = cancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel reservation")
            }
        }
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Reservations")
        }
    }
}

/** Displays a labelled reservation detail. */
@Composable
private fun ReservationDetailItem(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Calculates the number of calendar nights between check-in and check-out. */
private fun calculateReservationNights(
    startDate: Date,
    endDate: Date
): Int {
    val start = Calendar.getInstance().apply { time = startDate }
    val end = Calendar.getInstance().apply { time = endDate }
    var nights = 0

    while (
        start.get(Calendar.YEAR) != end.get(Calendar.YEAR) ||
        start.get(Calendar.DAY_OF_YEAR) != end.get(Calendar.DAY_OF_YEAR)
    ) {
        start.add(Calendar.DAY_OF_YEAR, 1)
        nights++
    }

    return nights
}
