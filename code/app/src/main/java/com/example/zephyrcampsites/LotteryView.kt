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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType


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

        // Store the number of winners entered by the owner.
        var numberOfWinners by remember { mutableStateOf("1") }
        // Empty means the waitlist has no capacity limit.
        var waitlistCapacity by remember { mutableStateOf("") }

        // US 02.04.04: Store the booking period entered by the campsite owner.
        var bookingStart by remember { mutableStateOf("") }
        var bookingEnd by remember { mutableStateOf("") }

        // Find the existing lottery for the selected campsite.
        val existingLottery = lotteryController.getLotteryByCampsiteId(campsiteId)

        // Check whether the registration period can still be changed.
        val canEditRegistration = existingLottery != null &&
                System.currentTimeMillis() < existingLottery.registrationStart

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
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

            Spacer(modifier = Modifier.height(12.dp))

            // Let the owner enter the booking start date.
            OutlinedTextField(
                value = bookingStart,
                onValueChange = { bookingStart = it },
                label = { Text("Booking Start (YYYY-MM-DD HH:mm)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            //  Let the owner enter the booking end date.
            OutlinedTextField(
                value = bookingEnd,
                onValueChange = { bookingEnd = it },
                label = { Text("Booking End (YYYY-MM-DD HH:mm)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Let the owner enter the number of winners.
            OutlinedTextField(
                value = numberOfWinners,
                onValueChange = { numberOfWinners = it },
                label = { Text("Number of Winners") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = waitlistCapacity,
                onValueChange = { waitlistCapacity = it },
                label = { Text("Waitlist Capacity (Optional)") },
                placeholder = { Text("Leave blank for unlimited") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                )
            )
            Spacer(modifier = Modifier.height(24.dp))


            var message by remember { mutableStateOf("") }

            Button(
                onClick = {
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

                        // Convert the booking dates to timestamps.
                        val bookingStartTime = formatter.parse(bookingStart)?.time
                            ?: throw IllegalArgumentException("Invalid booking start date")

                        val bookingEndTime = formatter.parse(bookingEnd)?.time
                            ?: throw IllegalArgumentException("Invalid booking end date")

                        // Check that the booking period is valid.
                        require(bookingStartTime > end) {
                            "Booking must start after registration ends."
                        }

                        require(bookingStartTime < bookingEndTime) {
                            "Booking end must be after booking start."
                        }

                        // Check that the owner entered a positive whole number.
                        val winnerCount = numberOfWinners.toIntOrNull()
                            ?: throw IllegalArgumentException("Enter a valid whole number of winners.")

                        require(winnerCount > 0) {
                            "Number of winners must be positive."
                        }

                        val capacity = if (waitlistCapacity.isBlank()) {
                            null
                        } else {
                            waitlistCapacity.toIntOrNull()
                        }

                        if (waitlistCapacity.isNotBlank() && (capacity == null || capacity <= 0)) {
                            message = "Waitlist capacity must be a positive whole number."
                        } else {
                            lotteryController.createLottery(
                                // Use the selected campsite ID instead of a fixed test ID.
                                campsiteId = campsiteId,
                                registrationStart = start,
                                registrationEnd = end,
                                bookingStart = bookingStartTime,
                                bookingEnd = bookingEndTime,
                                numberOfWinners = winnerCount,
                                waitlistCapacity = capacity
                            )
                            message = "Lottery submission sent to Firebase."
                        }



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

                Spacer(modifier = Modifier.height(12.dp))

                // Allow the owner to update the number of winners.
                Button(
                    onClick = {
                        try {
                            val newCount = numberOfWinners.toIntOrNull()
                                ?: throw IllegalArgumentException("Enter a valid whole number of winners.")

                            require(newCount > 0) {
                                "Number of winners must be positive."
                            }

                            lotteryController.updateNumberOfWinners(
                                existingLottery.id,
                                newCount
                            )

                            message = "Number of winners update submitted."

                        } catch (e: IllegalArgumentException) {
                            message = e.message ?: "Invalid number of winners."
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canEditRegistration
                ) {
                    Text("Update Number of Winners")
                }
                Spacer(modifier = Modifier.height(12.dp))


                // Allows the owner to update the waitlist capacity.
                Button(
                    onClick = {
                        if (existingLottery != null) {

                            // Convert the input to a number, or null if left blank.
                            val capacity = if (waitlistCapacity.isBlank()) {
                                null
                            } else {
                                waitlistCapacity.toIntOrNull()
                            }

                            // Check that the capacity is a positive whole number.
                            if (waitlistCapacity.isNotBlank() &&
                                (capacity == null || capacity <= 0)
                            ) {
                                message = "Waitlist capacity must be a positive whole number."
                            } else {
                                try {
                                    // The model checks if the capacity change is allowed.
                                    lotteryController.updateWaitlistCapacity(
                                        existingLottery.id,
                                        capacity
                                    )
                                    message = "Waitlist capacity update submitted."
                                } catch (e: IllegalArgumentException) {
                                    message = e.message ?: "Invalid waitlist capacity."
                                }
                            }
                        } else {
                            message = "Create a lottery first."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Waitlist Capacity")
                }

                Spacer(modifier = Modifier.height(12.dp))

            // Allow the owner to update the booking period.
                Button(
                    onClick = {
                        try {
                            val formatter = SimpleDateFormat(
                                "yyyy-MM-dd HH:mm", Locale.US
                            ).apply {
                                isLenient = false
                                timeZone = TimeZone.getTimeZone("America/Edmonton")
                            }

                            val newStart = formatter.parse(bookingStart)?.time
                                ?: throw IllegalArgumentException("Invalid booking start date")

                            val newEnd = formatter.parse(bookingEnd)?.time
                                ?: throw IllegalArgumentException("Invalid booking end date")

                            lotteryController.updateBookingPeriod(
                                existingLottery.id,
                                newStart,
                                newEnd
                            )

                            message = "Booking period update submitted."

                        } catch (e: IllegalArgumentException) {
                            message = e.message ?: "Invalid booking period."
                        } catch (e: java.text.ParseException) {
                            message = "Enter dates and times as YYYY-MM-DD HH:mm."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Booking Period")
                }

            }

        }
    }
}
