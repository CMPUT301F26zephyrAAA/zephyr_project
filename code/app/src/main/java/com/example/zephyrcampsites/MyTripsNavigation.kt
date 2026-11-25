package com.example.zephyrcampsites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Date
private const val MY_TRIPS_ACTIVE_TAB = 1
private const val MY_TRIPS_PAST_TAB = 2


private enum class ReservationPage {
    DETAILS,
    MESSAGE_OWNER,
    CANCELLATION
}

@Composable
fun MyTripsNavigation(
    accountId: String,
    accountsController: AccountsController,
    reservationsController: ReservationsController,
    campsiteForId: (String) -> Campsite?,
    imageContent: @Composable (Campsite) -> Unit,
    onOpenCampsite: (Int) -> Unit,
    onRateCampsite: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedTab by rememberSaveable(accountId) {
        mutableIntStateOf(MY_TRIPS_ACTIVE_TAB)
    }
    var showLotteriesInfo by rememberSaveable(accountId) {
        mutableStateOf(false)
    }

    var selectedReservationId by rememberSaveable(accountId) {
        mutableStateOf<String?>(null)
    }
    var page by rememberSaveable(accountId) {
        mutableStateOf(ReservationPage.DETAILS)
    }
    var showCancelDialog by remember(accountId) { mutableStateOf(false) }
    var reservations by remember(accountId) {
        mutableStateOf<List<ReservedUser>?>(null)
    }
    var error by remember(accountId) { mutableStateOf<String?>(null) }
    var retry by remember(accountId) { mutableIntStateOf(0) }

    DisposableEffect(accountId, reservationsController, retry) {
        var requestActive = true
        reservations = null
        error = null

        reservationsController.getReservations(
            accountId = accountId,
            onSuccess = { result ->
                if (requestActive) reservations = result
            },
            onError = { exception ->
                if (requestActive) {
                    error = exception.message ?: "Unable to load reservations."
                }
            }
        )

        onDispose { requestActive = false }
    }

    fun goBack() {
        when {
            page != ReservationPage.DETAILS -> page = ReservationPage.DETAILS
            selectedReservationId != null -> selectedReservationId = null
            else -> onBack()
        }
    }

    BackHandler(
        enabled = !showCancelDialog &&
                (selectedReservationId != null || page != ReservationPage.DETAILS)
    ) {
        goBack()
    }

    val selected = reservations?.firstOrNull { it.id == selectedReservationId }

    when (page) {
        ReservationPage.MESSAGE_OWNER -> {
            when {
                selected != null -> ReservationChatScreen(
                    reservation = selected,
                    campsite = campsiteForId(selected.campsiteId),
                    accountsController = accountsController,
                    onBack = ::goBack
                )
                reservations == null && error == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                else -> FeaturePage("Reservation unavailable", ::goBack)
            }
            return
        }

        ReservationPage.CANCELLATION -> {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextButton(onClick = { goBack() }) { Text("Back") }
                Text(
                    "Cancellation confirmation",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text("Preview only. Your reservation is still active.")
                Button(onClick = {}, enabled = false) { Text("Download") }

                Button(onClick = {
                    selectedReservationId = null
                    selectedTab = MY_TRIPS_PAST_TAB
                    page = ReservationPage.DETAILS
                }) {
                    Text("Done")
                }
            }
            return
        }

        ReservationPage.DETAILS -> Unit
    }

    val now = Date()

    if (selected != null) {
        ReservationDetailsView(
            reservation = selected,
            campsite = campsiteForId(selected.campsiteId),
            isPast = selectedTab == MY_TRIPS_PAST_TAB || selected.cancelled ||
                    selected.bookedPeriodEnd?.after(now) == false,
            imageContent = imageContent,
            onViewMap = {
                selected.campsiteId.toIntOrNull()?.let(onOpenCampsite)
            },
            onMessageOwner = { page = ReservationPage.MESSAGE_OWNER },
            onCancelReservation = { showCancelDialog = true },
            onRateCampsite = { onRateCampsite(selected.campsiteId) },
            onBack = { goBack() }
        )

        if (showCancelDialog) {
            AlertDialog(
                onDismissRequest = { showCancelDialog = false },
                title = { Text("Cancel reservation?") },
                text = { Text("Continue to the cancellation preview?") },
                confirmButton = {
                    TextButton(onClick = {
                        showCancelDialog = false
                        page = ReservationPage.CANCELLATION
                    }) {
                        Text("Yes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelDialog = false }) {
                        Text("No")
                    }
                }
            )
        }
        return
    }

    val loadedReservations = reservations
    val loadError = error

    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = { goBack() },
            modifier = Modifier.padding(8.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF2E7D32),
                contentColor = Color.White)
        ) {
            Text("Back")
        }

        when {
            loadError != null -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(loadError)
                    Button(onClick = { retry++ }) { Text("Retry") }
                }
            }

            loadedReservations == null -> {
                Box(
                    modifier = Modifier.weight(1f).fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            else -> {
                Box(modifier = Modifier.weight(1f)) {
                    ReservationsView(
                        activeReservations = reservationsController
                            .filterActiveReservations(loadedReservations, now),
                        pastReservations = reservationsController
                            .filterPastReservations(loadedReservations, now),
                        selectedTab = selectedTab,
                        onSelectedTabChange = { selectedTab = it },
                        campsiteForId = campsiteForId,
                        onViewDetails = { selectedReservationId = it.id },
                        onLotteriesClick = { showLotteriesInfo = true },
                        imageContent = imageContent
                    )
                }
            }
        }
    }

    if (showLotteriesInfo) {
        AlertDialog(
            onDismissRequest = { showLotteriesInfo = false },
            title = { Text("Lotteries") },
            text = {
                Text("Open a campsite from Browse Campsites to view its lottery.")
            },
            confirmButton = {
                TextButton(onClick = { showLotteriesInfo = false }) {
                    Text("OK")
                }
            }
        )
    }
}
