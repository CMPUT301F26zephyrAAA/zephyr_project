package com.example.zephyrcampsites

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays an entrant's active and past campsite reservations.
 * Provides navigation between Lotteries, Reservations, and Past Reservations.
 * Reservation cards show campsite details and allow the entrant to open the selected reservation.
 * @param activeReservations Active and upcoming reservations.
 * @param pastReservations Completed reservations sorted newest first.
 * @param campsiteForId Returns an already loaded campsite for a campsite ID.
 * @param onViewDetails Called when the entrant selects VIEW DETAILS.
 * @param onLotteriesClick Called when the entrant selects the Lotteries tab.
 * @param unreadIndicator Displays unread message status for a reservation and its campsite owner.
 * @param imageContent Composable used to display an existing campsite image.
 */
// AI Citation [AI-18], [AI-19]
// OpenAI, ChatGPT assisted with Compose reservation
// UI structure, tabs, test tags, and accessibility.
// AI usage and citations documented in the GitHub Wiki.
@Composable fun ReservationsView(
    activeReservations: List<ReservedUser>,
    pastReservations: List<ReservedUser>,
    selectedTab: Int = 1,
    onSelectedTabChange: (Int) -> Unit = {},
    campsiteForId: (String) -> Campsite?,
    onViewDetails: (ReservedUser) -> Unit,
    onLotteriesClick: () -> Unit,
    unreadIndicator: @Composable (ReservedUser, String) -> Unit = { _, _ -> },
    imageContent: @Composable (Campsite) -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(145.dp)
                .background(Color(0xFFE5E9E5)),
            contentAlignment = Alignment.Center
        ) {
            Text("Campsite image")
        }
    }
) {
    val reservations = if (selectedTab == 1) {
        activeReservations
    } else
    {
        pastReservations
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(top = 12.dp)
            .testTag("tripsScreen")
    ) {
        Text(
            text = "TRIPS",
            fontSize = 14.sp,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .testTag("tripsHeading")
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .height(52.dp)
        ) {
            listOf(
                "Lotteries",
                "Reservations",
                "Past Reservations"
            ).forEachIndexed { index, title ->

                val selected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            if (selected) Color.White
                            else Color(0xFFE1E1E1)
                        )
                        .selectable(
                            selected = selected,
                            role = Role.Tab,
                            onClick = {
                                if (index == 0) {
                                    onLotteriesClick()
                                } else {
                                    onSelectedTabChange(index)
                                }
                            }
                        )
                        .testTag(
                            when (index) {
                                0 -> "lotteriesTab"
                                1 -> "reservationsTab"
                                else -> "pastReservationsTab"
                            }
                        )
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 12.sp,
                        fontWeight = if (selected) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        maxLines = 2
                    )
                }
            }
        }

        HorizontalDivider(color = Color.DarkGray)

        if (reservations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("reservationsEmptyState"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (selectedTab == 1) {
                        "No active reservations."
                    } else {
                        "No past reservations."
                    },
                    color = Color.Gray
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = 34.dp,
                    vertical = 42.dp
                ),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(
                    items = reservations,
                    key = { it.id }
                ) { reservation ->
                    val campsite = campsiteForId(reservation.campsiteId)
                    ReservationCard(
                        reservation = reservation,
                        campsite = campsite,
                        imageContent = imageContent,
                        unreadIndicator = { unreadIndicator(reservation, campsite?.ownerID.orEmpty()) },
                        onViewDetails = {
                            onViewDetails(reservation)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Displays a campsite reservation card using the team's Trips UI layout.
 * @param reservation Reservation associated with the card.
 * @param campsite Campsite details, or null if unavailable.
 * @param imageContent Composable used to display the campsite image.
 * @param onViewDetails Called when VIEW DETAILS is selected.
 * @param unreadIndicator Displays unread status above the reservation information.
 */
@Composable
private fun ReservationCard(
    reservation: ReservedUser,
    campsite: Campsite?,
    imageContent: @Composable (Campsite) -> Unit,
    unreadIndicator: @Composable () -> Unit,
    onViewDetails: () -> Unit
) {
    val dateFormat = remember {
        SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    }

    val startDate = reservation.bookedPeriodStart
        ?.let { dateFormat.format(it) } ?: "N/A"

    val endDate = reservation.bookedPeriodEnd
        ?.let { dateFormat.format(it) } ?: "N/A"

    val now = Date()

    val status = when {
        reservation.cancelled -> "Cancelled"
        reservation.bookedPeriodEnd == null -> "Unavailable"
        !reservation.bookedPeriodEnd.after(now) -> "Completed"
        reservation.bookedPeriodStart != null &&
                !reservation.bookedPeriodStart.after(now) -> "Ongoing"
        else -> "Upcoming"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reservationCard_${reservation.id}"),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            Color(0xFFE5E5E5)
        ),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column {
            if (campsite != null) {
                imageContent(campsite)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(145.dp)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Campsite unavailable")
                }
            }

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = campsite?.name
                        ?.takeIf { it.isNotBlank() }
                        ?: "Campsite unavailable",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = campsite?.location.orEmpty(),
                    fontSize = 12.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Dates: $startDate - $endDate",
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Text(
                    text = "Status: $status",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.testTag(
                        "reservationStatus_${reservation.id}"
                    )
                )
                unreadIndicator()
            }

            HorizontalDivider(
                color = Color(0xFFE5E5E5)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Max Occupancy: " +
                            (campsite?.maxOccupancy ?: "—"),
                    fontSize = 12.sp,
                    color = Color.Gray
                )

                Text(
                    text = "Rating: " +
                            (campsite?.averageRating?.toString()
                                ?: "N/A"),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            HorizontalDivider(
                color = Color(0xFFE5E5E5)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onViewDetails,
                    modifier = Modifier
                        .fillMaxWidth(0.75f)
                        .height(54.dp)
                        .testTag(
                            "viewReservationDetails_${reservation.id}"
                        ),
                    shape = RoundedCornerShape(2.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF193C32)
                    )
                ) {
                    Text(
                        text = "VIEW DETAILS",
                        color = Color.White
                    )
                }
            }
        }
    }
}
