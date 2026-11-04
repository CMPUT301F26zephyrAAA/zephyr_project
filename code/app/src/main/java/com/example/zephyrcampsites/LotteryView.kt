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
    /**
     * Displays the lottery settings for the selected campsite.
     *
     * @param campsiteId ID of the campsite being added to the lottery.
     */

    @Composable
    fun LotterySettingsView(campsiteId: String) {

        var registrationStart by remember { mutableStateOf("") }
        var registrationEnd by remember { mutableStateOf("") }

        // Find the existing lottery for the selected campsite.
        val existingLottery = lotteryController.getLotteryByCampsiteId(campsiteId)

        // Check whether the registration period can still be changed.
        val canEditRegistration = existingLottery != null &&
                System.currentTimeMillis() < existingLottery.registrationStart

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
                label = { Text("Registration Start (YYYY-MM-DD HH:mm)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = registrationEnd,
                onValueChange = { registrationEnd = it },
                label = { Text("Registration End (YYYY-MM-DD HH:mm)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))


            var message by remember { mutableStateOf("") }

            Button(
                onClick = {
                    // Prevent creating another lottery for the same campsite.
                    if (existingLottery != null) {
                        message = "This campsite already has a lottery. Update its registration period instead."
                        return@Button
                    }
                    try {
                        // Parse registration dates and times using a fixed format.
                        val formatter = SimpleDateFormat(
                            "yyyy-MM-dd HH:mm", Locale.US
                        ).apply {
                            isLenient = false
                            timeZone = TimeZone.getTimeZone("America/Edmonton")
                        }

                        val start = formatter.parse(registrationStart)?.time
                            ?: throw IllegalArgumentException("Invalid start date")

                        val end = formatter.parse(registrationEnd)?.time
                            ?: throw IllegalArgumentException("Invalid end date")

                        require(start < end) {
                            "Registration end must be after start."
                        }

                        lotteryController.createLottery(
                            // Use the selected campsite ID instead of a fixed test ID.
                            campsiteId = campsiteId,
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
                        message = "Enter dates and times as YYYY-MM-DD HH:mm."
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("List Campsite in Lottery")
            }

            if (message.isNotEmpty()) {
                Text(message)
            }

            // Allow the owner to update dates before registration starts.
            if (existingLottery != null)  {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        try {
                            // Convert the entered dates and times to timestamps.
                            val formatter = SimpleDateFormat(
                                "yyyy-MM-dd HH:mm", Locale.US
                            ).apply {
                                isLenient = false
                                timeZone = TimeZone.getTimeZone("America/Edmonton")
                            }

                            val newStart = formatter.parse(registrationStart)?.time
                                ?: throw IllegalArgumentException("Invalid start date")

                            val newEnd = formatter.parse(registrationEnd)?.time
                                ?: throw IllegalArgumentException("Invalid end date")

                            // Update the existing lottery through the controller.
                            lotteryController.updateRegistrationPeriod(
                                existingLottery.id,
                                newStart,
                                newEnd
                            )

                            message = "Registration period update submitted."
                        } catch (e: IllegalArgumentException) {
                            message = e.message ?: "Invalid registration period."
                        } catch (e: java.text.ParseException) {
                            message = "Enter dates and times as YYYY-MM-DD HH:mm."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Registration Period")
                }
            }



        }
    }
}
