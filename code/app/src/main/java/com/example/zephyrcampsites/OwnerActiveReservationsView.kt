
package com.example.zephyrcampsites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays active reservations belonging to an owner.
 */
@Composable
fun OwnerActiveReservationsView(
    reservations: List<ReservedUser>,
    campsiteForId: (String) -> Campsite?,
    accountForId: (String) -> Account?,
    isLoading: Boolean,
    errorMessage: String?,
    onViewDetails: (ReservedUser) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("‹ Back")
        }

        Text(
            text = "Active Reservations",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            reservations.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No active reservations.")
                }
            }

            else -> {
                Text(
                    text = "${reservations.size} active reservations",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(reservations) { reservation ->

                        OwnerReservationCard(
                            reservation = reservation,
                            campsiteName = campsiteForId(
                                reservation.campsiteId
                            )?.name?.takeIf { it.isNotBlank() }
                                ?: "Unknown campsite",

                            entrantName = accountForId(
                                reservation.accountId
                            )?.name?.takeIf { it.isNotBlank() }
                                ?: "Unknown entrant",

                            onClick = {
                                onViewDetails(reservation)
                            }
                        )
                    }
                }
            }
        }
    }
}


/**
 * Displays a selectable summary of an active reservation.
 */
@Composable
private fun OwnerReservationCard(
    reservation: ReservedUser,
    campsiteName: String,
    entrantName: String,
    onClick: () -> Unit
) {
    val dateFormat = remember {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    }

    val startDate = reservation.bookedPeriodStart
    val endDate = reservation.bookedPeriodEnd

    val status = when {
        reservation.cancelled -> "Cancelled"

        startDate == null || endDate == null ->
            "Unavailable"

        !endDate.after(Date()) ->
            "Completed"

        startDate.after(Date()) ->
            "Confirmed · Upcoming"

        else -> "Confirmed · Current stay"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor =
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = campsiteName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = status,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                HorizontalDivider()

                Text("Entrant: $entrantName")

                Text(
                    text = "Check-in: ${
                        startDate?.let { dateFormat.format(it) }
                            ?: "Unavailable"
                    }"
                )

                Text(
                    text = "Check-out: ${
                        endDate?.let { dateFormat.format(it) }
                            ?: "Unavailable"
                    }"
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "›",
                style = MaterialTheme.typography.headlineMedium
            )
        }
    }
}

