
package com.example.zephyrcampsites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

/**
 * Displays detailed reservation information
 * for a campsite owner.
 */
@Composable
fun OwnerReservationDetailsView(
    reservation: ReservedUser,
    campsite: Campsite?,
    entrant: Account?,
    onBack: () -> Unit
) {
    val dateFormat = remember {
        SimpleDateFormat(
            "MMMM d, yyyy",
            Locale.getDefault()
        )
    }

    val startDate = reservation.bookedPeriodStart
    val endDate = reservation.bookedPeriodEnd

    val currentTime = Date()

    val status = when {
        reservation.cancelled -> "Cancelled"

        startDate == null || endDate == null ->
            "Unavailable"

        !endDate.after(currentTime) ->
            "Completed"

        startDate.after(currentTime) ->
            "Confirmed · Upcoming"

        else -> "Confirmed · Current stay"
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

        // Campsite information
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Campsite",
                    style = MaterialTheme.typography.labelMedium
                )

                Text(
                    text = campsite?.name
                        ?.takeIf { it.isNotBlank() }
                        ?: "Unknown campsite",
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

        // Entrant information
        Text(
            text = "Entrant Information",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        ReservationDetailItem(
            label = "Entrant name",
            value = entrant?.name
                ?.takeIf { it.isNotBlank() }
                ?: "Unknown entrant"
        )

        HorizontalDivider()

        // Reservation information
        Text(
            text = "Reservation Information",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        ReservationDetailItem(
            label = "Check-in",
            value = startDate?.let {
                dateFormat.format(it)
            } ?: "Unavailable"
        )

        ReservationDetailItem(
            label = "Check-out",
            value = endDate?.let {
                dateFormat.format(it)
            } ?: "Unavailable"
        )

        ReservationDetailItem(
            label = "Duration",
            value = duration?.let {
                if (it == 1) "1 night" else "$it nights"
            } ?: "Unavailable"
        )

        ReservationDetailItem(
            label = "Reservation status",
            value = status
        )

        HorizontalDivider()

        ReservationDetailItem(
            label = "Reservation ID",
            value = reservation.id
        )

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Reservations")
        }
    }
}

/**
 * Displays a labelled reservation detail.
 */
@Composable
private fun ReservationDetailItem(
    label: String,
    value: String
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
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

/**
 * Calculates the number of calendar nights
 * between check-in and check-out.
 */
private fun calculateReservationNights(
    startDate: Date?,
    endDate: Date?
): Int? {
    if (startDate == null || endDate == null) {
        return null
    }

    if (!startDate.before(endDate)) {
        return null
    }

    val start = Calendar.getInstance().apply {
        time = startDate
    }

    val end = Calendar.getInstance().apply {
        time = endDate
    }

    var nights = 0

    while (
        start.get(Calendar.YEAR) != end.get(Calendar.YEAR) ||
        start.get(Calendar.DAY_OF_YEAR) !=
        end.get(Calendar.DAY_OF_YEAR)
    ) {
        start.add(Calendar.DAY_OF_YEAR, 1)
        nights++
    }

    return nights
}