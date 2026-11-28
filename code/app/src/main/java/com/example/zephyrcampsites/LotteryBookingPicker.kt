// Lets a winning entrant check availability and confirm a campsite reservation.
package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
// Hiral - [AI-08] OpenAI ChatGPT Implement US-02.04.02
// OpenAI, Codex, US 02.04.02 winning reservation picker, 2026-11-27.
@Composable
internal fun LotteryBookingPicker(
    lottery: Lottery,
    campsiteNames: Map<String, String>,
    initialCampsiteId: String,
    controller: ReservationOfferController,
    onConfirmed: () -> Unit
) {
    var open by remember(lottery.id) { mutableStateOf(false) }
    var campsiteId by remember(lottery.id, initialCampsiteId) { mutableStateOf(initialCampsiteId) }
    var checkIn by remember(lottery.id) { mutableStateOf("") }
    var checkout by remember(lottery.id) { mutableStateOf("") }
    var checking by remember(lottery.id) { mutableStateOf(false) }
    var available by remember(lottery.id) { mutableStateOf(false) }
    var error by remember(lottery.id) { mutableStateOf("") }
    val sites = lottery.participatingCampsiteIds().filter { it in campsiteNames }
    val formatter = remember {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("America/Edmonton")
        }
    }

    fun submit(checkOnly: Boolean) {
        val start = runCatching { formatter.parse(checkIn) }.getOrNull()
        val end = runCatching { formatter.parse(checkout) }.getOrNull()
        if (start == null || end == null ||
            formatter.format(start) != checkIn || formatter.format(end) != checkout ||
            start.time < lottery.bookingStart || end.time > lottery.bookingEnd || !start.before(end)
        ) {
            error = "Enter check-in and checkout times within the lottery period."
            available = false
            return
        }
        checking = true
        error = ""
        controller.checkOrConfirmBooking(
            lotteryId = lottery.id,
            campsiteId = campsiteId,
            start = start.time,
            end = end.time,
            checkOnly = checkOnly,
            onSuccess = {
                checking = false
                if (checkOnly) {
                    available = true
                } else {
                    open = false
                    onConfirmed()
                }
            },
            onError = {
                checking = false
                available = false
                error = it
            }
        )
    }

    Button(onClick = { open = true }, enabled = sites.isNotEmpty()) {
        Text("Book Campsite")
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { if (!checking) { open = false } },
            title = { Text("Book a campsite") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "${formatter.format(Date(lottery.bookingStart))} to " +
                            "${formatter.format(Date(lottery.bookingEnd))} (Mountain time)"
                    )
                    sites.forEach { id ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = campsiteId == id,
                                onClick = {
                                    campsiteId = id
                                    available = false
                                    error = ""
                                },
                                enabled = !checking
                            )
                            Text(campsiteNames.getValue(id))
                        }
                    }
                    OutlinedTextField(
                        value = checkIn,
                        onValueChange = {
                            checkIn = it
                            available = false
                            error = ""
                        },
                        label = { Text("Check-in (YYYY-MM-DD HH:mm)") },
                        enabled = !checking,
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = checkout,
                        onValueChange = {
                            checkout = it
                            available = false
                            error = ""
                        },
                        label = { Text("Checkout (YYYY-MM-DD HH:mm)") },
                        enabled = !checking,
                        singleLine = true
                    )
                    if (error.isNotEmpty()) {
                        Text(error, color = MaterialTheme.colorScheme.error)
                    } else if (available) {
                        Text("Available for these dates.")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { submit(checkOnly = !available) },
                    enabled = !checking && campsiteId in sites
                ) {
                    Text(if (available) "Confirm Reservation" else "Check Availability")
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }, enabled = !checking) { Text("Cancel") }
            }
        )
    }
}
