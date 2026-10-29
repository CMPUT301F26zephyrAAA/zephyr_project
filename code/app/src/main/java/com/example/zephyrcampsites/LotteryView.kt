package com.example.zephyrcampsites

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone



/**
 * Displays the lottery settings for a campsite.
 */
class LotteryView (
    private val lotteryController: LotteryController
) {

    @Composable
    fun LotterySettingsView() {

        var registrationStart by remember { mutableStateOf("") }
        var registrationEnd by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text(
                text = "Lottery Settings",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(text = "Configure your campsite lottery")

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = registrationStart,
                onValueChange = { registrationStart = it },
                label = { Text("Registration Start (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = registrationEnd,
                onValueChange = { registrationEnd = it },
                label = { Text("Registration End (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))


            var message by remember { mutableStateOf("") }

            Button(
                onClick = {
                    try {
                        val formatter = SimpleDateFormat(
                            "yyyy-MM-dd", Locale.US
                        ).apply {
                            isLenient = false
                            timeZone = TimeZone.getTimeZone("UTC")
                        }

                        val start = formatter.parse(registrationStart)?.time
                            ?: throw IllegalArgumentException("Invalid start date")

                        val end = formatter.parse(registrationEnd)?.time
                            ?: throw IllegalArgumentException("Invalid end date")

                        require(start < end) {
                            "Registration end must be after start."
                        }

                        lotteryController.createLottery(
                            campsiteId = "1",
                            registrationStart = start,
                            registrationEnd = end,
                            bookingStart = end + 86_400_000L,
                            bookingEnd = end + 172_800_000L,
                            numberOfWinners = 1,
                            waitlistCapacity = null
                        )

                        message = "Lottery submission sent to Firebase."

                    } catch (e: IllegalArgumentException) {
                        message = e.message ?: "Invalid input."
                    } catch (e: java.text.ParseException) {
                        message = "Enter dates as YYYY-MM-DD."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("List Campsite in Lottery")
            }

            if (message.isNotEmpty()) {
                Text(message)
            }



        }
    }
}
