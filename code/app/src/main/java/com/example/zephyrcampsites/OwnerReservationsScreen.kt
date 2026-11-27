package com.example.zephyrcampsites

import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import java.util.Date
import androidx.activity.compose.BackHandler

/**
 * Connects the owner's reservation screens to Firebase
 * and manages reservation state and listener lifecycle.
 */
@Composable
fun OwnerReservationsScreen(
    owner: Account,
    campsiteModel: CampsiteModel,
    onBack: () -> Unit
) {
    val controller = remember {
        ReservationsController()
    }

    val accountsModel = remember {
        AccountsModel()
    }

    var reservations by remember(owner.id) {
        mutableStateOf<List<ReservedUser>>(emptyList())
    }

    var isLoading by remember(owner.id) {
        mutableStateOf(true)
    }

    var errorMessage by remember(owner.id) {
        mutableStateOf<String?>(null)
    }

    var retryCount by remember(owner.id) {
        mutableIntStateOf(0)
    }

    var selectedReservationId by remember(owner.id) {
        mutableStateOf<String?>(null)
    }

    var currentTime by remember {
        mutableStateOf(Date())
    }

    val entrantAccounts = remember(owner.id) {
        mutableStateMapOf<String, Account>()
    }

    val ownerCampsites = campsiteModel.campsites.filter {
        it.ownerID == owner.id
    }

    val ownerCampsiteIds = ownerCampsites.map {
        it.id
    }.toSet()

    // Listen for reservation changes.
    DisposableEffect(
        owner.id,
        ownerCampsiteIds,
        campsiteModel.isLoading,
        campsiteModel.error,
        retryCount
    ) {
        var disposed = false

        reservations = emptyList()
        isLoading = true
        errorMessage = null

        val listeners = when {
            campsiteModel.error != null -> {
                errorMessage = "Unable to load campsites."
                isLoading = false
                emptyList()
            }

            campsiteModel.isLoading -> {
                emptyList()
            }

            else -> {
                controller.listenToOwnerActiveReservations(
                    ownerId = owner.id,
                    campsites = ownerCampsites,
                    onSuccess = { results ->
                        if (!disposed) {
                            reservations = results
                            isLoading = false
                            errorMessage = null
                        }
                    },
                    onError = {
                        if (!disposed) {
                            reservations = emptyList()
                            errorMessage =
                                "Unable to load reservations."
                            isLoading = false
                        }
                    }
                )
            }
        }

        onDispose {
            disposed = true
            controller.stopReservationListeners(listeners)
        }
    }

    // Refresh the current time so expired reservations
    // disappear even without a Firebase document update.
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Date()
            delay(1000L)
        }
    }

    val activeReservations =
        controller.filterOwnerActiveReservations(
            reservations = reservations,
            campsites = ownerCampsites,
            ownerId = owner.id,
            currentTime = currentTime
        )

    val entrantIds = activeReservations.map {
        it.accountId
    }.toSet()

    // Retrieve entrant information.
    DisposableEffect(owner.id, entrantIds) {
        var disposed = false

        entrantIds.forEach { accountId ->
            if (!entrantAccounts.containsKey(accountId)) {
                accountsModel.getAccount(
                    accountId,
                    onResult = { account ->
                        if (!disposed && account != null) {
                            entrantAccounts[accountId] = account
                        }
                    },
                    onError = {
                        // The UI displays Unknown entrant
                        // if account information is unavailable.
                    }
                )
            }
        }

        onDispose {
            disposed = true
        }
    }

    val selectedReservation =
        activeReservations.firstOrNull {
            it.id == selectedReservationId
        }

    // Return to the reservation list when the Android
    // back button is pressed from the details screen.
    BackHandler(enabled = selectedReservation != null) {
        selectedReservationId = null
    }

    // Return to the list if the selected reservation
    // is cancelled, completed or no longer accessible.
    LaunchedEffect(selectedReservation) {
        if (selectedReservation == null) {
            selectedReservationId = null
        }
    }

    if (selectedReservation != null) {
        OwnerReservationDetailsView(
            reservation = selectedReservation,
            campsite = ownerCampsites.firstOrNull {
                it.id == selectedReservation.campsiteId
            },
            entrant = entrantAccounts[
                selectedReservation.accountId
            ],
            onBack = {
                selectedReservationId = null
            }
        )
    } else {
        OwnerActiveReservationsView(
            reservations = activeReservations,
            campsiteForId = { id ->
                ownerCampsites.firstOrNull {
                    it.id == id
                }
            },
            accountForId = { id ->
                entrantAccounts[id]
            },
            isLoading = isLoading,
            errorMessage = errorMessage,
            onRetry = { retryCount++ },
            onViewDetails = { reservation ->
                selectedReservationId = reservation.id
            },
            onBack = onBack
        )
    }
}
