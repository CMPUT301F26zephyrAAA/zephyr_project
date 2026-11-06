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
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import java.util.Date


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
    fun LotterySettingsView(campsiteId: String, onLotteryCreated: (String) -> Unit,
                            onBackClick: () -> Unit) {

        var lotteryName by remember { mutableStateOf("") }

        var registrationStart by remember { mutableStateOf("") }
        var registrationEnd by remember { mutableStateOf("") }

        // Store the number of winners entered by the owner.
        var numberOfWinners by remember { mutableStateOf("1") }
        // Empty means the waitlist has no capacity limit.
        var waitlistCapacity by remember { mutableStateOf("") }

        //  Store the booking period entered by the campsite owner.
        var bookingStart by remember { mutableStateOf("") }
        var bookingEnd by remember { mutableStateOf("") }


        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "CREATE LOTTERY",
                style = MaterialTheme.typography.titleSmall,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(16.dp))


            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Lottery Name:",
                color = Color(0xFF2E8B75)
            )

            OutlinedTextField(
                value = lotteryName,
                onValueChange = { lotteryName = it },
                label = { Text("Lottery Name") },
                placeholder = { Text("Enter lottery name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Registration Period (YYYY-MM-DD HH:mm):",
                color = Color(0xFF2E8B75)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            )// owner can enter the registration start and end here
            {
                OutlinedTextField(
                    value = registrationStart,
                    onValueChange = { registrationStart = it },
                    placeholder = { Text("Start date") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Text("to")

                OutlinedTextField(
                    value = registrationEnd,
                    onValueChange = { registrationEnd = it },
                    placeholder = { Text("End date") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Booking Period (YYYY-MM-DD HH:mm):",
                color = Color(0xFF2E8B75)
            )

            Spacer(modifier = Modifier.height(8.dp))
            // owner can enter the booking start and end here
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = bookingStart,
                    onValueChange = { bookingStart = it },
                    placeholder = { Text("Start date") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                Text("to")

                OutlinedTextField(
                    value = bookingEnd,
                    onValueChange = { bookingEnd = it },
                    placeholder = { Text("End date") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Let the owner enter the number of winners.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Number of Winners:",
                        color = Color(0xFF2E8B75)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = numberOfWinners,
                        onValueChange = { numberOfWinners = it },
                        placeholder = { Text("0") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Waitlist Limit:",
                        color = Color(0xFF2E8B75)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = waitlistCapacity,
                        onValueChange = { waitlistCapacity = it },
                        placeholder = { Text("Optional") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))


            var message by remember { mutableStateOf("") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
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
                                lotteryName = lotteryName,
                                campsiteId = campsiteId,
                                registrationStart = start,
                                registrationEnd = end,
                                bookingStart = bookingStartTime,
                                bookingEnd = bookingEndTime,
                                numberOfWinners = winnerCount,
                                waitlistCapacity = capacity,
                                // Navigate only after Firebase confirms the save.
                                onSuccess = { lotteryId ->
                                    onLotteryCreated(lotteryId)
                                },

                                // Display an error if Firebase cannot save the lottery.
                                onFailure = { exception ->
                                    message = "Failed to publish lottery: ${exception.message}"
                                }
                            )
                        }



                    } catch (e: IllegalArgumentException) {
                        message = e.message ?: "Invalid input."
                    } catch (e: java.text.ParseException) {
                        message = "Enter dates and times as YYYY-MM-DD HH:mm."
                    }
                },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF19483D),
                        contentColor = Color.White
                )
                ) {
                    Text("PUBLISH")
                }

                Button(
                    onClick = {
                        onBackClick()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.LightGray,
                        contentColor = Color.Black
                    )
                ) {
                    Text("BACK")
                }
            }

            if (message.isNotEmpty()) {
                Text(message)
            }


        }
    }

    @Composable
    fun LotteryDetailsView(
        lotteryId: String,
        onEditClick: () -> Unit,
        onBackClick: () -> Unit
    ) {
        val lottery = lotteryController.getLotteryById(lotteryId)

        if (lottery == null) {
            Text("Lottery not found.")
            return
        }

        val formatter = SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("America/Edmonton")
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "LOTTERY DETAILS",
                style = MaterialTheme.typography.titleSmall,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = lottery.lotteryName,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Campsite ID: ${lottery.campsiteId}")

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Registration Period: " +
                        formatter.format(Date(lottery.registrationStart)) +
                        " to " +
                        formatter.format(Date(lottery.registrationEnd))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Booking Period: " +
                        formatter.format(Date(lottery.bookingStart)) +
                        " to " +
                        formatter.format(Date(lottery.bookingEnd))
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Number of Winners: ${lottery.numberOfWinners}")

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                "Waitlist Limit: ${lottery.waitlistCapacity ?: "Unlimited"}"
            )

            Spacer(modifier = Modifier.height(24.dp))


            Text(
                text = "Status: Not available",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Progress:",
                color = Color(0xFF2E8B75),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text("Joined Waitlist: N/A")
            Spacer(modifier = Modifier.height(8.dp))

            Text("Selected: N/A")
            Spacer(modifier = Modifier.height(8.dp))

            Text("Reserved: N/A")
            Spacer(modifier = Modifier.height(8.dp))

            Text("Declined: N/A")

            Spacer(modifier = Modifier.height(32.dp))



            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("VIEW WAITLIST")
                }

                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("NOTIFY ENTRANTS")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onEditClick,
                modifier = Modifier.fillMaxWidth(0.5f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF19483D),
                    contentColor = Color.White
                )
            ) {
                Text("EDIT LOTTERY")
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onBackClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.LightGray,
                        contentColor = Color.Black
                    )
                ) {
                    Text("BACK")
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

        }
    }

    @Composable
    fun EditLotteryView(
        lotteryId: String,
        onSaveClick: () -> Unit,
        onBackClick: () -> Unit
    ) {
        val lottery = lotteryController.getLotteryById(lotteryId)

        if (lottery == null) {
            Text("Lottery not found.")
            return
        }

        val formatter = SimpleDateFormat(
            "yyyy-MM-dd HH:mm",
            Locale.US
        ).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("America/Edmonton")
        }

        var lotteryName by remember(lotteryId) {
            mutableStateOf(lottery.lotteryName)
        }

        var registrationStart by remember(lotteryId) {
            mutableStateOf(formatter.format(Date(lottery.registrationStart)))
        }

        var registrationEnd by remember(lotteryId) {
            mutableStateOf(formatter.format(Date(lottery.registrationEnd)))
        }

        var bookingStart by remember(lotteryId) {
            mutableStateOf(formatter.format(Date(lottery.bookingStart)))
        }

        var bookingEnd by remember(lotteryId) {
            mutableStateOf(formatter.format(Date(lottery.bookingEnd)))
        }

        var numberOfWinners by remember(lotteryId) {
            mutableStateOf(lottery.numberOfWinners.toString())
        }

        var waitlistCapacity by remember(lotteryId) {
            mutableStateOf(lottery.waitlistCapacity?.toString() ?: "")
        }

        // Message to show validation errors or update status.
        var message by remember(lotteryId) {
            mutableStateOf("")
        }

        val canEditRegistration =
            System.currentTimeMillis() < lottery.registrationStart

        val canEditBooking =
            System.currentTimeMillis() < lottery.registrationStart

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(
                text = "EDIT LOTTERY",
                style = MaterialTheme.typography.titleSmall,
                color = Color.DarkGray
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Lottery Name:",
                color = Color(0xFF2E8B75)
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = lotteryName,
                onValueChange = { lotteryName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Registration Period:",
                color = Color(0xFF2E8B75)
            )

            Text(
                text = "Format: YYYY-MM-DD HH:mm",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = registrationStart,
                    onValueChange = { registrationStart = it },
                    placeholder = { Text("Start date") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = canEditRegistration
                )

                Text("to")

                OutlinedTextField(
                    value = registrationEnd,
                    onValueChange = { registrationEnd = it },
                    placeholder = { Text("End date") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = canEditRegistration
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Booking Period:",
                color = Color(0xFF2E8B75)
            )

            Text(
                text = "Format: YYYY-MM-DD HH:mm",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = bookingStart,
                    onValueChange = { bookingStart = it },
                    placeholder = { Text("Start date") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = canEditBooking
                )

                Text("to")

                OutlinedTextField(
                    value = bookingEnd,
                    onValueChange = { bookingEnd = it },
                    placeholder = { Text("End date") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    enabled = canEditBooking
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Number of Winners:",
                        color = Color(0xFF2E8B75)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = numberOfWinners,
                        onValueChange = { numberOfWinners = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Waitlist Limit:",
                        color = Color(0xFF2E8B75)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = waitlistCapacity,
                        onValueChange = { waitlistCapacity = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("Unlimited") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            // Convert the entered dates into timestamps.
                            val start = formatter.parse(registrationStart)?.time
                                ?: throw IllegalArgumentException("Invalid registration start date.")

                            val end = formatter.parse(registrationEnd)?.time
                                ?: throw IllegalArgumentException("Invalid registration end date.")

                            val bookingStartTime = formatter.parse(bookingStart)?.time
                                ?: throw IllegalArgumentException("Invalid booking start date.")

                            val bookingEndTime = formatter.parse(bookingEnd)?.time
                                ?: throw IllegalArgumentException("Invalid booking end date.")

                            // Validate the numeric fields.
                            val winners = numberOfWinners.toIntOrNull()
                                ?: throw IllegalArgumentException("Invalid number of winners.")

                            val capacity = if (waitlistCapacity.isBlank()) {
                                null
                            } else {
                                waitlistCapacity.toIntOrNull()
                                    ?: throw IllegalArgumentException("Invalid waitlist limit.")
                            }

                            // Send the updated information to the controller.
                            lotteryController.updateLottery(
                                lotteryId = lotteryId,
                                lotteryName = lotteryName,
                                registrationStart = start,
                                registrationEnd = end,
                                bookingStart = bookingStartTime,
                                bookingEnd = bookingEndTime,
                                numberOfWinners = winners,
                                waitlistCapacity = capacity,
                                // Firebase confirmed that the lottery was saved.
                                onSuccess = {
                                    onSaveClick()
                                },

                                // Firebase could not save the lottery.
                                onFailure = { exception ->
                                    message = "Failed to update lottery: ${exception.message}"
                                }
                            )

                        } catch (e: IllegalArgumentException) {
                            message = e.message ?: "Invalid lottery information."
                        } catch (e: java.text.ParseException) {
                            message = "Invalid date. Please use YYYY-MM-DD HH:mm and enter a real date."}
                        catch (e: Exception) {
                            message = e.message ?: "Unable to submit lottery update."
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF19483D),
                        contentColor = Color.White
                    )
                ) {
                    Text("SAVE")
                }

                Button(
                    onClick = onBackClick,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.LightGray,
                        contentColor = Color.Black
                    )
                ) {
                    Text("BACK")
                }
            }
            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = message,
                    color = Color(0xFF19483D)
                )
            }
        }
    }
}


