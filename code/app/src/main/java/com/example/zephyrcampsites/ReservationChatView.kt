package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Looks up the owner of the campsite associated with the selected reservation.
 * @param reservation Reservation opened from My Trips.
 * @param campsite Campsite associated with the reservation, if available.
 * @param accountsController Reads the owner's existing account from Firebase.
 * @param onBack Returns to the same reservation details.
 */
@Composable
internal fun ReservationChatScreen(
    reservation: ReservedUser,
    campsite: Campsite?,
    accountsController: AccountsController,
    onBack: () -> Unit
) {
    val ownerId = campsite?.ownerID.orEmpty()
    var ownerName by remember(ownerId) { mutableStateOf<String?>(null) }
    var status by remember(ownerId) {
        mutableStateOf(if (ownerId.isBlank()) "Owner unavailable." else "Loading owner…")
    }

    DisposableEffect(ownerId, accountsController) {
        var active = true
        if (ownerId.isNotBlank()) {
            accountsController.findAccount(
                deviceId = ownerId,
                onResult = { owner ->
                    if (active) {
                        ownerName = owner?.name
                        status = if (owner == null) "Owner unavailable." else ""
                    }
                },
                onError = {
                    if (active) {
                        status = "Unable to load the owner."
                    }
                }
            )
        }
        onDispose { active = false }
    }

    ReservationChatView(
        reservation = reservation,
        campsiteName = campsite?.name.orEmpty(),
        ownerName = ownerName,
        status = status,
        onBack = onBack
    )
}

/**
 * Displays the owner and reservation context for the chat screen.
 * @param reservation Supplies the booking dates shown in the header.
 * @param campsiteName Display name of the reserved campsite.
 * @param ownerName Display name of the account that owns the campsite.
 * @param status Owner lookup status, if any.
 * @param onBack Returns to the reservation details.
 */
@Composable
internal fun ReservationChatView(
    reservation: ReservedUser,
    campsiteName: String,
    ownerName: String?,
    status: String,
    onBack: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy", Locale.getDefault()) }
    val startDate = reservation.bookedPeriodStart?.let { dateFormat.format(it) } ?: "N/A"
    val endDate = reservation.bookedPeriodEnd?.let { dateFormat.format(it) } ?: "N/A"

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("MESSAGES", style = MaterialTheme.typography.labelLarge)
        Text(
            text = ownerName?.takeIf { it.isNotBlank() } ?: "Campsite Owner",
            style = MaterialTheme.typography.headlineSmall
        )
        Text("${campsiteName.ifBlank { "Campsite unavailable" }} · $startDate to $endDate")
        HorizontalDivider()

        if (status.isNotBlank()) {
            Text(status)
        }
        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message") },
                enabled = false
            )
            Button(onClick = {}, enabled = false) { Text("SEND") }
        }
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.End)) { Text("BACK") }
    }
}
