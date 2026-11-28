// Renders an owner-only monthly calendar of confirmed campsite bookings.
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

private const val BOOKING_CALENDAR_REFRESH_INTERVAL_MS = 30_000L
private val AvailableDateColor = Color(0xFFE8F2E6)
private val BookedDateColor = Color(0xFFF9DEDC)
private val SelectedDateBorderColor = Color(0xFF395B3D)

/** Describes the year and zero-based month currently rendered in the calendar. */
internal data class BookingCalendarMonth(val year: Int, val monthIndex: Int) {
    init {
        require(monthIndex in Calendar.JANUARY..Calendar.DECEMBER)
    }
}

/**
 * Converts reservation intervals into local calendar-day availability.
 *
 * A reservation occupies its check-in day through, but not including, its
 * checkout day. This matches the app's existing night-count calculation.
 */
internal object OwnerBookingCalendarController {
    /** Returns the month containing [currentTimeMillis] in [timeZone]. */
    fun currentMonth(
        currentTimeMillis: Long = System.currentTimeMillis(),
        timeZone: TimeZone = TimeZone.getDefault()
    ): BookingCalendarMonth {
        val calendar = Calendar.getInstance(timeZone).apply { timeInMillis = currentTimeMillis }
        return BookingCalendarMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
    }

    /** Returns the month [amount] months before or after [month]. */
    fun shiftMonth(month: BookingCalendarMonth, amount: Int): BookingCalendarMonth {
        val calendar = Calendar.getInstance().apply {
            clear()
            set(month.year, month.monthIndex, 1)
            add(Calendar.MONTH, amount)
        }
        return BookingCalendarMonth(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH))
    }

    /** Returns the number of days in [month]. */
    fun daysInMonth(month: BookingCalendarMonth, timeZone: TimeZone): Int {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(month.year, month.monthIndex, 1)
        }.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** Returns the Sunday-based weekday offset for the first day of [month]. */
    fun firstDayOffset(month: BookingCalendarMonth, timeZone: TimeZone): Int {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(month.year, month.monthIndex, 1)
        }.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }

    /** Returns the local start time for [dayOfMonth] in [month]. */
    fun dayStartMillis(
        month: BookingCalendarMonth,
        dayOfMonth: Int,
        timeZone: TimeZone
    ): Long {
        return Calendar.getInstance(timeZone).apply {
            clear()
            set(month.year, month.monthIndex, dayOfMonth, 0, 0, 0)
        }.timeInMillis
    }

    /** Returns the local start time of the day containing [timeMillis]. */
    fun dayStartMillis(timeMillis: Long, timeZone: TimeZone): Long {
        return Calendar.getInstance(timeZone).apply {
            timeInMillis = timeMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /** Returns the starts of the booked calendar days in [month]. */
    fun bookedDayStarts(
        month: BookingCalendarMonth,
        reservations: List<OwnerReservation>,
        timeZone: TimeZone
    ): Set<Long> {
        return buildSet {
            for (day in 1..daysInMonth(month, timeZone)) {
                val dayStart = dayStartMillis(month, day, timeZone)
                val dayEnd = Calendar.getInstance(timeZone).apply {
                    timeInMillis = dayStart
                    add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
                if (reservations.any { reservation ->
                        !reservation.cancelled &&
                            reservation.startMillis < reservation.endMillis &&
                            reservation.startMillis < dayEnd &&
                            reservation.endMillis > dayStart
                    }
                ) {
                    add(dayStart)
                }
            }
        }
    }
}

/**
 * Loads and displays bookings for the campsite selected by its verified owner.
 * The regular refresh detects reservation confirmations and cancellations made
 * elsewhere while this screen stays open.
 */
@Composable
internal fun OwnerBookingCalendarScreen(
    campsite: Campsite,
    owner: Account,
    controller: ReservationsController,
    onBack: () -> Unit
) {
    val timeZone = remember { TimeZone.getDefault() }
    var visibleMonth by remember(campsite.id) {
        mutableStateOf(OwnerBookingCalendarController.currentMonth(timeZone = timeZone))
    }
    var selectedDayMillis by remember(campsite.id) {
        mutableLongStateOf(
            OwnerBookingCalendarController.dayStartMillis(System.currentTimeMillis(), timeZone)
        )
    }
    var reservations by remember(campsite.id) { mutableStateOf<List<OwnerReservation>?>(null) }
    var isRefreshing by remember(campsite.id) { mutableStateOf(false) }
    var errorMessage by remember(campsite.id) { mutableStateOf<String?>(null) }

    fun loadBookings() {
        if (isRefreshing) {
            return
        }
        isRefreshing = true
        errorMessage = null
        controller.getCampsiteBookingCalendar(
            owner = owner,
            campsiteId = campsite.id,
            onSuccess = { result ->
                reservations = result
                isRefreshing = false
            },
            onError = {
                errorMessage = "Unable to load booking information."
                isRefreshing = false
            }
        )
    }

    LaunchedEffect(campsite.id, owner.id) {
        loadBookings()
        while (isActive) {
            delay(BOOKING_CALENDAR_REFRESH_INTERVAL_MS)
            loadBookings()
        }
    }

    val bookedDayStarts = remember(reservations, visibleMonth, timeZone) {
        OwnerBookingCalendarController.bookedDayStarts(
            month = visibleMonth,
            reservations = reservations.orEmpty(),
            timeZone = timeZone
        )
    }
    val monthDays = OwnerBookingCalendarController.daysInMonth(visibleMonth, timeZone)

    OwnerBookingCalendarView(
        campsiteName = campsite.name.ifBlank { "Campsite" },
        visibleMonth = visibleMonth,
        selectedDayMillis = selectedDayMillis,
        bookedDayStarts = bookedDayStarts,
        hasLoadedBookings = reservations != null,
        isRefreshing = isRefreshing,
        errorMessage = errorMessage,
        timeZone = timeZone,
        onPreviousMonth = {
            visibleMonth = OwnerBookingCalendarController.shiftMonth(visibleMonth, -1)
            selectedDayMillis = OwnerBookingCalendarController.dayStartMillis(
                visibleMonth,
                minOf(
                    Calendar.getInstance(timeZone).apply { timeInMillis = selectedDayMillis }
                        .get(Calendar.DAY_OF_MONTH),
                    OwnerBookingCalendarController.daysInMonth(visibleMonth, timeZone)
                ),
                timeZone
            )
        },
        onNextMonth = {
            visibleMonth = OwnerBookingCalendarController.shiftMonth(visibleMonth, 1)
            selectedDayMillis = OwnerBookingCalendarController.dayStartMillis(
                visibleMonth,
                minOf(
                    Calendar.getInstance(timeZone).apply { timeInMillis = selectedDayMillis }
                        .get(Calendar.DAY_OF_MONTH),
                    OwnerBookingCalendarController.daysInMonth(visibleMonth, timeZone)
                ),
                timeZone
            )
        },
        onSelectDay = { selectedDayMillis = it },
        onRefresh = ::loadBookings,
        onBack = onBack,
        availableDayCount = monthDays - bookedDayStarts.size
    )
}

/** Renders the owner booking calendar from already loaded reservation state. */
@Composable
internal fun OwnerBookingCalendarView(
    campsiteName: String,
    visibleMonth: BookingCalendarMonth,
    selectedDayMillis: Long,
    bookedDayStarts: Set<Long>,
    hasLoadedBookings: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    timeZone: TimeZone,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onSelectDay: (Long) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    availableDayCount: Int
) {
    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
    val monthStart = OwnerBookingCalendarController.dayStartMillis(visibleMonth, 1, timeZone)
    val daysInMonth = OwnerBookingCalendarController.daysInMonth(visibleMonth, timeZone)
    val firstDayOffset = OwnerBookingCalendarController.firstDayOffset(visibleMonth, timeZone)
    val selectedDate = OwnerBookingCalendarController.dayStartMillis(selectedDayMillis, timeZone)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("OwnerBookingCalendar"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TextButton(onClick = onBack) {
            Text("‹ Back")
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Booking Calendar",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(campsiteName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Booking overview",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onRefresh, enabled = !isRefreshing) {
                Text(if (isRefreshing) "Updating…" else "Refresh")
            }
        }

        if (!hasLoadedBookings) {
            errorMessage?.let { BookingCalendarError(it, onRefresh) }
            if (errorMessage == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            return@Column
        }

        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("BookingCalendarLoadError")
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPreviousMonth, modifier = Modifier.testTag("PreviousBookingMonth")) {
                Text("‹", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                text = monthFormat.format(monthStart),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onNextMonth, modifier = Modifier.testTag("NextBookingMonth")) {
                Text("›", style = MaterialTheme.typography.headlineSmall)
            }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEach { weekday ->
                Text(
                    text = weekday,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        val calendarCellCount = firstDayOffset + daysInMonth
        val weekCount = (calendarCellCount + 6) / 7
        repeat(weekCount) { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { weekday ->
                    val dayOfMonth = week * 7 + weekday - firstDayOffset + 1
                    if (dayOfMonth !in 1..daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f).height(48.dp))
                    } else {
                        val dayStart = OwnerBookingCalendarController.dayStartMillis(
                            visibleMonth,
                            dayOfMonth,
                            timeZone
                        )
                        BookingCalendarDay(
                            dayOfMonth = dayOfMonth,
                            isBooked = dayStart in bookedDayStarts,
                            isSelected = dayStart == selectedDate,
                            onClick = { onSelectDay(dayStart) },
                            testTag = "BookingCalendarDay_$dayStart"
                        )
                    }
                }
            }
        }

        CalendarLegend()

        Text(
            text = "Monthly overview",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalendarStatCard(
                label = "Booked days",
                count = bookedDayStarts.size,
                color = BookedDateColor,
                modifier = Modifier.weight(1f).testTag("BookedDaysCount")
            )
            CalendarStatCard(
                label = "Available days",
                count = availableDayCount,
                color = AvailableDateColor,
                modifier = Modifier.weight(1f).testTag("AvailableDaysCount")
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Selected date", style = MaterialTheme.typography.labelMedium)
                Text(
                    dateFormat.format(selectedDate),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (selectedDate in bookedDayStarts) {
                        "Confirmed booking"
                    } else {
                        "Available"
                    },
                    color = if (selectedDate in bookedDayStarts) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

/** Renders a retry affordance when the initial booking query fails. */
@Composable
private fun BookingCalendarError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("BookingCalendarLoadError")
        )
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

/** Renders one selectable date cell with booked and available styling. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.BookingCalendarDay(
    dayOfMonth: Int,
    isBooked: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val backgroundColor = if (isBooked) BookedDateColor else AvailableDateColor
    val textColor = if (isBooked) MaterialTheme.colorScheme.error else Color(0xFF2F6841)
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .padding(2.dp)
            .clip(shape)
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.background(SelectedDateBorderColor.copy(alpha = 0.16f))
                } else {
                    Modifier
                }
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = dayOfMonth.toString(),
            color = textColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Displays the color key used for available and booked dates. */
@Composable
private fun CalendarLegend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(AvailableDateColor)
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text("Available", style = MaterialTheme.typography.labelMedium)
        Spacer(modifier = Modifier.size(16.dp))
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(BookedDateColor)
        )
        Spacer(modifier = Modifier.size(4.dp))
        Text("Booked", style = MaterialTheme.typography.labelMedium)
    }
}

/** Renders a small monthly count card. */
@Composable
private fun CalendarStatCard(label: String, count: Int, color: Color, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
