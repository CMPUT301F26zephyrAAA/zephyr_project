// Lets a winning entrant view live date availability and confirm a campsite reservation.
package com.example.zephyrcampsites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private const val AVAILABILITY_REFRESH_INTERVAL_MS = 10_000L
private val AvailableReservationDateColor = Color(0xFFE8F2E6)
private val BookedReservationDateColor = Color(0xFFF9DEDC)
private val BlockedReservationDateColor = Color(0xFFE2E2E2)
private val SelectedReservationDateColor = Color(0xFF2F6841)

/** Identifies the month currently shown in the winning entrant's calendar. */
internal data class ReservationBookingMonth(val year: Int, val monthIndex: Int) {
    init {
        require(monthIndex in Calendar.JANUARY..Calendar.DECEMBER)
    }
}

/**
 * Builds the monthly calendar and verifies the selected stay before confirmation.
 *
 * A calendar day represents one night. The checkout day is exclusive, matching
 * the reservation transaction and the existing owner booking calendar.
 */
internal object LotteryBookingPickerController {
    fun monthFor(timeMillis: Long, timeZone: TimeZone): ReservationBookingMonth {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = timeMillis }
        return ReservationBookingMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
    }

    fun shiftMonth(month: ReservationBookingMonth, amount: Int): ReservationBookingMonth {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(month.year, month.monthIndex, 1)
            add(Calendar.MONTH, amount)
        }
        return ReservationBookingMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
    }

    fun daysInMonth(month: ReservationBookingMonth, timeZone: TimeZone): Int {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(month.year, month.monthIndex, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun firstDayOffset(month: ReservationBookingMonth, timeZone: TimeZone): Int {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(month.year, month.monthIndex, 1)
        }.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }

    fun dayStartMillis(month: ReservationBookingMonth, dayOfMonth: Int, timeZone: TimeZone): Long {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(month.year, month.monthIndex, dayOfMonth, 0, 0, 0)
        }.timeInMillis
    }

    fun dayStartMillis(timeMillis: Long, timeZone: TimeZone): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Returns the days that can start a one-night stay in the displayed month. */
    fun availableDayStarts(
        month: ReservationBookingMonth,
        availability: LotteryBookingAvailability,
        timeZone: TimeZone,
        nowMillis: Long = System.currentTimeMillis()
    ): Set<Long> {
        val today = dayStartMillis(nowMillis, timeZone)
        return buildSet {
            for (day in 1..daysInMonth(month, timeZone)) {
                val start = dayStartMillis(month, day, timeZone)
                val end = nextDayStart(start, timeZone)
                if (start >= today &&
                    start >= availability.bookingStartMillis &&
                    end <= availability.bookingEndMillis &&
                    availability.bookedPeriods.none { period ->
                        start < period.endMillis && period.startMillis < end
                    }
                ) {
                    add(start)
                }
            }
        }
    }

    /** Returns whether every night from check-in up to checkout is currently available. */
    fun isAvailableStay(
        checkInMillis: Long,
        checkoutMillis: Long,
        availability: LotteryBookingAvailability,
        timeZone: TimeZone,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (checkInMillis >= checkoutMillis) {
            return false
        }
        var night = checkInMillis
        while (night < checkoutMillis) {
            val nextNight = nextDayStart(night, timeZone)
            if (night < dayStartMillis(nowMillis, timeZone) ||
                night < availability.bookingStartMillis ||
                nextNight > availability.bookingEndMillis ||
                availability.bookedPeriods.any { period ->
                    night < period.endMillis && period.startMillis < nextNight
                }
            ) {
                return false
            }
            night = nextNight
        }
        return true
    }

    private fun nextDayStart(dayStart: Long, timeZone: TimeZone): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_MONTH, 1)
        }.timeInMillis
    }
}

/** Opens the full-screen reservation-date flow from a winning entrant's campsite. */
@Composable
internal fun LotteryBookingPicker(
    lottery: Lottery,
    campsite: Campsite,
    controller: ReservationOfferController,
    onConfirmed: () -> Unit,
    onDecline: () -> Unit
) {
    var open by remember(lottery.id, campsite.id) { mutableStateOf(false) }

    Button(
        onClick = { open = true },
        modifier = Modifier.fillMaxWidth(),
        enabled = lottery.bookingStart < lottery.bookingEnd
    ) {
        Text("View Available Dates")
    }

    if (open) {
        ReservationDateSelectionDialog(
            lottery = lottery,
            campsite = campsite,
            controller = controller,
            onDismiss = { open = false },
            onConfirmed = {
                open = false
                onConfirmed()
            },
            onDecline = {
                open = false
                onDecline()
            }
        )
    }
}

/** Renders the winner's calendar and refreshes date availability from Firebase. */
@Composable
private fun ReservationDateSelectionDialog(
    lottery: Lottery,
    campsite: Campsite,
    controller: ReservationOfferController,
    onDismiss: () -> Unit,
    onConfirmed: () -> Unit,
    onDecline: () -> Unit
) {
    val timeZone = remember { TimeZone.getDefault() }
    var availability by remember(lottery.id, campsite.id) {
        mutableStateOf<LotteryBookingAvailability?>(null)
    }
    var loading by remember(lottery.id, campsite.id) { mutableStateOf(true) }
    var error by remember(lottery.id, campsite.id) { mutableStateOf<String?>(null) }
    var confirming by remember(lottery.id, campsite.id) { mutableStateOf(false) }
    var checkInMillis by remember(lottery.id, campsite.id) { mutableLongStateOf(0L) }
    var checkoutMillis by remember(lottery.id, campsite.id) { mutableLongStateOf(0L) }
    var visibleMonth by remember(lottery.id, campsite.id) {
        mutableStateOf(LotteryBookingPickerController.monthFor(lottery.bookingStart, timeZone))
    }

    fun refreshAvailability() {
        controller.getBookingAvailability(
            lotteryId = lottery.id,
            campsiteId = campsite.id,
            onSuccess = { result ->
                availability = result
                loading = false
                error = null
            },
            onError = { message ->
                loading = false
                error = message
            }
        )
    }

    DisposableEffect(lottery.id) {
        val listener = controller.listenForBookingAvailabilityChanges(
            lotteryId = lottery.id,
            onChanged = ::refreshAvailability,
            onError = { message ->
                if (availability == null) {
                    loading = false
                    error = message
                }
            }
        )
        onDispose { listener?.remove() }
    }

    LaunchedEffect(lottery.id, campsite.id) {
        while (isActive) {
            refreshAvailability()
            delay(AVAILABILITY_REFRESH_INTERVAL_MS)
        }
    }

    val currentAvailability = availability
    val availableDays = currentAvailability?.let {
        LotteryBookingPickerController.availableDayStarts(visibleMonth, it, timeZone)
    }.orEmpty()
    val hasAnyAvailableDate = currentAvailability?.let {
        var month = LotteryBookingPickerController.monthFor(it.bookingStartMillis, timeZone)
        val finalMonth = LotteryBookingPickerController.monthFor(it.bookingEndMillis, timeZone)
        while (month != finalMonth) {
            if (LotteryBookingPickerController.availableDayStarts(month, it, timeZone).isNotEmpty()) {
                return@let true
            }
            month = LotteryBookingPickerController.shiftMonth(month, 1)
        }
        LotteryBookingPickerController.availableDayStarts(month, it, timeZone).isNotEmpty()
    } ?: false
    val dateFormat = remember { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()) }

    Dialog(
        onDismissRequest = { if (!confirming) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("ReservationDatePicker"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(onClick = onDismiss, enabled = !confirming) {
                Text("‹ Back")
            }
            Text(
                text = "Select Reservation Dates",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(campsite.name.ifBlank { "Campsite" }, style = MaterialTheme.typography.titleMedium)

            when {
                loading && currentAvailability == null -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(280.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                currentAvailability == null -> {
                    Text(error ?: "Unable to load available dates.", color = MaterialTheme.colorScheme.error)
                    Button(onClick = {
                        loading = true
                        refreshAvailability()
                    }) {
                        Text("Retry")
                    }
                }

                !hasAnyAvailableDate -> {
                    NoAvailableDates(onDecline = onDecline)
                }

                else -> {
                    val isSelectionComplete = checkInMillis > 0L && checkoutMillis > checkInMillis &&
                        LotteryBookingPickerController.isAvailableStay(
                            checkInMillis,
                            checkoutMillis,
                            currentAvailability,
                            timeZone
                        )
                    Text("Choose check-in and check-out dates. Booked and owner-blocked dates are unavailable.")
                    ReservationMonthHeader(
                        visibleMonth = visibleMonth,
                        timeZone = timeZone,
                        onPreviousMonth = {
                            visibleMonth = LotteryBookingPickerController.shiftMonth(visibleMonth, -1)
                        },
                        onNextMonth = {
                            visibleMonth = LotteryBookingPickerController.shiftMonth(visibleMonth, 1)
                        }
                    )
                    ReservationCalendar(
                        month = visibleMonth,
                        availableDays = availableDays,
                        availability = currentAvailability,
                        checkInMillis = checkInMillis,
                        checkoutMillis = checkoutMillis,
                        timeZone = timeZone,
                        onSelectDay = { selectedDay ->
                            when {
                                checkInMillis == 0L || checkoutMillis > 0L -> {
                                    checkInMillis = selectedDay
                                    checkoutMillis = 0L
                                    error = null
                                }

                                selectedDay <= checkInMillis -> {
                                    checkInMillis = selectedDay
                                    checkoutMillis = 0L
                                    error = null
                                }

                                LotteryBookingPickerController.isAvailableStay(
                                    checkInMillis,
                                    selectedDay,
                                    currentAvailability,
                                    timeZone
                                ) -> {
                                    checkoutMillis = selectedDay
                                    error = null
                                }

                                else -> {
                                    checkInMillis = selectedDay
                                    checkoutMillis = 0L
                                    error = "Choose a continuous range of available dates."
                                }
                            }
                        }
                    )
                    ReservationLegend()
                    if (checkInMillis > 0L) {
                        Text(
                            if (checkoutMillis > checkInMillis) {
                                "Check-in: ${dateFormat.format(checkInMillis)}\n" +
                                    "Check-out: ${dateFormat.format(checkoutMillis)}"
                            } else {
                                "Check-in: ${dateFormat.format(checkInMillis)}\nSelect a check-out date."
                            }
                        )
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Button(
                        onClick = {
                            confirming = true
                            error = null
                            controller.checkOrConfirmBooking(
                                lotteryId = lottery.id,
                                campsiteId = campsite.id,
                                start = checkInMillis,
                                end = checkoutMillis,
                                checkOnly = false,
                                onSuccess = onConfirmed,
                                onError = { message ->
                                    confirming = false
                                    error = message
                                    refreshAvailability()
                                }
                            )
                        },
                        enabled = isSelectionComplete && !confirming,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (confirming) "Confirming…" else "Confirm Reservation")
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        enabled = !confirming,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Decline")
                    }
                }
            }
            if (currentAvailability != null && loading) {
                Text("Updating availability…", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun ReservationMonthHeader(
    visibleMonth: ReservationBookingMonth,
    timeZone: TimeZone,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val firstDay = LotteryBookingPickerController.dayStartMillis(visibleMonth, 1, timeZone)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPreviousMonth) { Text("‹") }
        Text(formatter.format(firstDay), fontWeight = FontWeight.Bold)
        TextButton(onClick = onNextMonth) { Text("›") }
    }
}

@Composable
private fun ReservationCalendar(
    month: ReservationBookingMonth,
    availableDays: Set<Long>,
    availability: LotteryBookingAvailability,
    checkInMillis: Long,
    checkoutMillis: Long,
    timeZone: TimeZone,
    onSelectDay: (Long) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { weekday ->
            Text(
                weekday,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
    val daysInMonth = LotteryBookingPickerController.daysInMonth(month, timeZone)
    val firstDayOffset = LotteryBookingPickerController.firstDayOffset(month, timeZone)
    val weekCount = (firstDayOffset + daysInMonth + 6) / 7
    repeat(weekCount) { week ->
        Row(modifier = Modifier.fillMaxWidth()) {
            repeat(7) { weekday ->
                val dayOfMonth = week * 7 + weekday - firstDayOffset + 1
                if (dayOfMonth !in 1..daysInMonth) {
                    Spacer(modifier = Modifier.weight(1f).height(46.dp))
                } else {
                    val dayStart = LotteryBookingPickerController.dayStartMillis(month, dayOfMonth, timeZone)
                    val isAvailable = dayStart in availableDays
                    val isBooked = availability.bookedPeriods.any { period ->
                        dayStart < period.endMillis && period.startMillis <
                            LotteryBookingPickerController.dayStartMillis(
                                LotteryBookingPickerController.monthFor(dayStart, timeZone),
                                Calendar.getInstance(timeZone).apply { timeInMillis = dayStart }
                                    .get(Calendar.DAY_OF_MONTH) + 1,
                                timeZone
                            )
                    }
                    val isSelected = dayStart == checkInMillis || dayStart == checkoutMillis ||
                        (checkInMillis > 0L && checkoutMillis > checkInMillis &&
                            dayStart > checkInMillis && dayStart < checkoutMillis)
                    val color = when {
                        isSelected -> SelectedReservationDateColor
                        isAvailable -> AvailableReservationDateColor
                        isBooked -> BookedReservationDateColor
                        else -> BlockedReservationDateColor
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                            .clickable(enabled = isAvailable) { onSelectDay(dayStart) }
                            .testTag("ReservationDate_$dayStart"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayOfMonth.toString(),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReservationLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ReservationLegendItem("Available", AvailableReservationDateColor)
        Spacer(modifier = Modifier.width(12.dp))
        ReservationLegendItem("Booked", BookedReservationDateColor)
        Spacer(modifier = Modifier.width(12.dp))
        ReservationLegendItem("Blocked", BlockedReservationDateColor)
    }
}

@Composable
private fun ReservationLegendItem(label: String, color: Color) {
    Box(
        modifier = Modifier
            .height(10.dp)
            .width(10.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(color)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(label, style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun NoAvailableDates(onDecline: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp)
            .testTag("NoAvailableReservationDates"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("No Available Dates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "All reservation dates for this campsite have been booked or blocked by the owner.",
            textAlign = TextAlign.Center
        )
        Text(
            "You can no longer make a reservation for this lottery.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall
        )
        OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
            Text("Decline")
        }
    }
}
