
package com.example.zephyrcampsites

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ReservationGreen = Color(0xFF193C32)
private val ReservationRed = Color(0xFFF44343)
private val BackButtonGrey = Color(0xFFD9D9D9)

/**
 * Displays the full details of an entrant's campsite reservation.
 * This view receives all data from its caller.
 * @param reservation Reservation being displayed.
 * @param campsite Associated campsite, or null if unavailable.
 * @param isPast Whether to display the past-reservation layout.
 * @param imageContent Existing campsite image composable.
 * @param onViewMap Called when VIEW MAP is selected.
 * @param onMessageOwner Called when MESSAGE OWNER is selected.
 * @param onCancelReservation Called when CANCEL RESERVATION is selected.
 * @param onRateCampsite Called when RATE CAMPSITE is selected.
 * @param onBack Called when BACK is selected.
 * @param numberOfGuests Number of guests, if available.
 * @param totalPaid Formatted total payment, if available.
 * @param userRating Entrant's saved rating, if available.
 */
// AI Citation [AI-21]
// OpenAI, ChatGPT assisted with active and past
// reservation details layouts and action callbacks.
// AI usage and citation documented in the GitHub Wiki.
@Composable
fun ReservationDetailsView(
    reservation: ReservedUser,
    campsite: Campsite?,
    isPast: Boolean,
    imageContent: @Composable (Campsite) -> Unit,
    onViewMap: () -> Unit,
    onMessageOwner: () -> Unit,
    onCancelReservation: () -> Unit,
    onRateCampsite: () -> Unit,
    onBack: () -> Unit,
    numberOfGuests: Int? = null,
    totalPaid: String? = null,
    userRating: String? = null
) {
    val dateFormat = remember {
        SimpleDateFormat("MM/dd/yy", Locale.getDefault())
    }

    val startDate = reservation.bookedPeriodStart
        ?.let { dateFormat.format(it) } ?: "N/A"

    val endDate = reservation.bookedPeriodEnd
        ?.let { dateFormat.format(it) } ?: "N/A"

    val now = Date()

    val status = when {
        reservation.cancelled -> "Cancelled"
        reservation.bookedPeriodStart == null ||
                reservation.bookedPeriodEnd == null -> "N/A"
        !reservation.bookedPeriodEnd.after(now) -> "Completed"
        reservation.bookedPeriodStart.after(now) -> "Upcoming"
        else -> "Ongoing"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("reservationDetailsScreen")
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            // Campsite image at the top of the page.
            if (campsite != null) {
                imageContent(campsite)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(155.dp)
                        .background(Color(0xFFE5E9E5)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Campsite image unavailable")
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 23.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    text = campsite?.name
                        ?.takeIf { it.isNotBlank() }
                        ?: "Campsite unavailable",
                    fontSize = 29.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag(
                        "reservationDetailsCampsiteName"
                    )
                )

                Text(
                    text = campsite?.location
                        ?.takeIf { it.isNotBlank() }
                        ?: "N/A",
                    fontSize = 19.sp,
                    modifier = Modifier.testTag(
                        "reservationDetailsLocation"
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Dates: $startDate to $endDate",
                    fontSize = 15.sp,
                    modifier = Modifier.testTag(
                        "reservationDetailsDates"
                    )
                )

                Text(
                    text = "Number of Guests: " +
                            (numberOfGuests?.toString() ?: "N/A"),
                    fontSize = 15.sp
                )

                Text(
                    text = "Total Paid: " +
                            (totalPaid?.takeIf { it.isNotBlank() } ?: "N/A"),
                    fontSize = 15.sp
                )

                Text(
                    text = "Status: $status",
                    fontSize = 15.sp,
                    modifier = Modifier.testTag(
                        "reservationDetailsStatus"
                    )
                )

                if (isPast) {
                    Text(
                        text = "Your Rating: " +
                                (userRating
                                    ?.takeIf { it.isNotBlank() }
                                    ?: "Not rated"),
                        fontSize = 15.sp,
                        modifier = Modifier.testTag(
                            "reservationDetailsRating"
                        )
                    )
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 23.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!isPast) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Button(
                        onClick = onViewMap,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("viewReservationMapButton"),
                        shape = RoundedCornerShape(5.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ReservationGreen,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("VIEW MAP", fontSize = 13.sp)
                    }

                    if (!reservation.cancelled) {
                        MessageOwnerButton(onMessageOwner, Modifier.weight(1f))
                    }
                }

                Button(
                    onClick = onCancelReservation,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("cancelReservationButton"),
                    shape = RoundedCornerShape(5.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReservationRed,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "CANCEL RESERVATION",
                        fontSize = 14.sp
                    )
                }
            } else if (!reservation.cancelled) {
                MessageOwnerButton(onMessageOwner)
                Button(
                    onClick = onRateCampsite,
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("rateReservationCampsiteButton"),
                    shape = RoundedCornerShape(5.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ReservationGreen,
                        contentColor = Color.White
                    )
                ) {
                    Text(
                        text = "RATE CAMPSITE",
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(55.dp))

            Button(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.End)
                    .width(110.dp)
                    .height(42.dp)
                    .testTag("reservationDetailsBackButton"),
                shape = RoundedCornerShape(5.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BackButtonGrey,
                    contentColor = Color.Black
                )
            ) {
                Text("BACK", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun MessageOwnerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(50.dp).testTag("messageReservationOwnerButton"),
        shape = RoundedCornerShape(5.dp),
        colors = ButtonDefaults.buttonColors(containerColor = ReservationGreen, contentColor = Color.White),
        contentPadding = PaddingValues(horizontal = 4.dp)
    ) {
        Text("MESSAGE OWNER", fontSize = 12.sp)
    }
}
