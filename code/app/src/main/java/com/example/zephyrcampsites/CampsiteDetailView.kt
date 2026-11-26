package com.example.zephyrcampsites
// AI Citation [AI-26]
// OpenAI, ChatGPT assisted with reservation offer
// status display and decline confirmation UI.
// AI usage and citation documented in the GitHub Wiki.

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf

/**
 * Displays campsite details, promotional imagery, and existing owner controls.
 * Outstanding issue: availability uses the stored booking period until the
 * reservation feature provides a reservation-aware availability schedule.
 */
class CampsiteDetailView {
    /**
     * Loads the selected campsite's image and owner for the details screen.
     *
     * @param campsiteId ID of the campsite to display.
     * @param controller Controller used to retrieve campsite data.
     * @param accountsController Controller used to retrieve the owner's name.
     * @param account Current account used to identify the campsite's owner.
     * @param codeGenerator Function that generates the campsite QR code.
     * @param onBack Returns to the previous screen.
     * @param onManageLottery Opens lottery management for this campsite.
     * @param ratingController Controller used to check rating eligibility.
     * @param onRateCampsite Opens the rating screen for an eligible entrant.
     * @param onRemoved Returns to browsing after successful removal.
     */
    @Composable
    fun DetailScreen(
        campsiteId: Int,
        controller: CampsiteController,
        accountsController: AccountsController,
        account: Account?,
        showOwnerView: Boolean = false,
        codeGenerator: (String, Int) -> ImageBitmap,
        onBack: () -> Unit,
        onManageLottery: ((String) -> Unit)?,
        onEdit: ((Int) -> Unit)?,
        lottery: Lottery? = null,
        waitingListController: WaitingListController? = null,
        reservationOfferController: ReservationOfferController? = null,
        ratingController: RatingController? = null,
        onRateCampsite: ((String) -> Unit)? = null,
        onRemoved: () -> Unit = onBack,
        showEntrantActions: Boolean = false,
        showAdminActions: Boolean = false,
    ) {
        var removing by remember(campsiteId) { mutableStateOf(false) }
        var removalError by remember(campsiteId) { mutableStateOf<String?>(null) }
        val model = controller.model
        val campsite = if (model.isLoading || model.error != null) {
            null
        } else {
            controller.retrieveId(campsiteId)
        }
        val promotionalPath = campsite?.promoImagePaths
            ?.firstOrNull { path -> path.isNotBlank() } ?: campsite?.imagePath.orEmpty()
        val ownerId = campsite?.ownerID.orEmpty()
        var imageUrl by remember(promotionalPath) { mutableStateOf<String?>(null) }
        var ownerName by remember(ownerId) { mutableStateOf<String?>(null) }

        DisposableEffect(promotionalPath, ownerId) {
            var active = true
            if (promotionalPath.isNotBlank()) {
                controller.loadCampsiteImage(
                    path = promotionalPath,
                    onResult = { url ->
                        if (active) {
                            imageUrl = url
                        }
                    },
                    onError = {
                        if (active) {
                            imageUrl = null
                        }
                    }
                )
            }
            if (ownerId.isNotBlank()) {
                accountsController.findAccount(
                    deviceId = ownerId,
                    onResult = { owner ->
                        if (active) {
                            ownerName = owner?.name
                        }
                    },
                    onError = {
                        if (active) {
                            ownerName = null
                        }
                    }
                )
            }
            onDispose { active = false }
        }

        DetailView(
            campsite = campsite,
            codeGenerator = codeGenerator,
            onBack = onBack,
            account = account,
            showOwnerView = showOwnerView,
            showEntrantActions = showEntrantActions,
            showAdminActions = showAdminActions,
            imageUrl = imageUrl,
            ownerName = ownerName,
            isLoading = model.isLoading,
            hasError = model.error != null,
            statusText = when {
                model.error != null -> "Unable to load campsite details."
                model.isLoading -> "Loading campsite details..."
                else -> "Campsite not found"
            },
            onManageLottery = onManageLottery,
            onEdit = onEdit,
            lottery = lottery,
            waitingListController = waitingListController,
            reservationOfferController = reservationOfferController,
            ratingController = ratingController,
            onRateCampsite = onRateCampsite,
            removing = removing,
            removalError = removalError,
            onRemove = {
                if (
                    showAdminActions &&
                    account?.userType?.contains("admin") == true &&
                    campsite != null &&
                    !removing
                ) {
                    removing = true
                    removalError = null
                    controller.removeCampsite(
                        account,
                        campsite,
                        onSuccess = {
                            removing = false
                            onRemoved()
                        },
                        onFailure = {
                            removing = false
                            removalError = it
                        },
                    )
                }
            },
        )
    }

    /**
     * Displays a campsite and provides navigation back to the previous screen.
     *
     * @param campsite Campsite to display, or null while unavailable.
     * @param codeGenerator Function that generates the campsite QR code.
     * @param onBack Called when the entrant presses Back.
     * @param account Current account used to determine which controls are displayed.
     * @param imageUrl Resolved promotional image URL, or null if unavailable.
     * @param ownerName Owner's display name, falling back to the stored owner ID.
     * @param statusText Message displayed when the campsite is unavailable.
     * @param onManageLottery Called when the owner opens lottery management.
     * @param ratingController Controller used to offer ratings only after a completed stay.
     * @param onRateCampsite Opens the rating screen for this campsite.
     * @param removing Whether removal is pending.
     * @param removalError Error from the last removal request.
     * @param onRemove Requests removal after confirmation.
     */
    @Composable
    fun DetailView(
        campsite: Campsite?,
        codeGenerator: (String, Int) -> ImageBitmap,

        onBack: () -> Unit,
        account: Account? = null,
        showOwnerView: Boolean = false,
        imageUrl: String? = null,
        ownerName: String? = null,
        statusText: String = "Campsite not found.",
        isLoading: Boolean = false,
        hasError: Boolean = false,
        onEdit: ((Int) -> Unit)? = null,
        onManageLottery: ((String) -> Unit)? = null,
        lottery: Lottery? = null,
        waitingListController: WaitingListController? = null,
        reservationOfferController: ReservationOfferController? = null,
        previewOwnerMode: Boolean = false,
        ratingController: RatingController? = null,
        onRateCampsite: ((String) -> Unit)? = null,
        removing: Boolean = false,
        removalError: String? = null,
        onRemove: () -> Unit = {},
        showEntrantActions: Boolean = false,
        showAdminActions: Boolean = false,
    ) {

        val canRemoveCampsite = showAdminActions && account?.userType?.contains("admin") == true
        var showRemoveConfirmation by remember(campsite?.id) { mutableStateOf(false) }
        BackHandler(enabled = removing) {}

        if (showRemoveConfirmation && canRemoveCampsite && campsite != null) {
            AlertDialog(
                onDismissRequest = { showRemoveConfirmation = false },
                title = { Text("Remove campsite?") },
                text = {
                    Text("Remove ${campsite.name} and its associated lotteries and waiting lists?")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRemoveConfirmation = false
                            onRemove()
                        },
                        modifier = Modifier.testTag("ConfirmRemoveCampsite"),
                    ) {
                        Text("REMOVE")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRemoveConfirmation = false }) { Text("CANCEL") }
                },
            )
        }
        val accountId = account?.id
        var joined by remember(lottery?.id, accountId) { mutableStateOf(false) }
        var checkingMembership by remember(lottery?.id, accountId) { mutableStateOf(true) }
        var membershipError by remember(lottery?.id, accountId) { mutableStateOf(false) }
        var joining by remember(lottery?.id, accountId) { mutableStateOf(false) }
        var leaving by remember(lottery?.id, accountId) { mutableStateOf(false) }
        var showLeaveConfirmation by remember(lottery?.id, accountId) { mutableStateOf(false) }
        var joinError by remember(lottery?.id, accountId) { mutableStateOf("") }
        var entrantDrawStatus by remember(lottery?.id, accountId) { mutableStateOf<String?>(null) }
        var checkingDrawStatus by remember(lottery?.id, accountId) { mutableStateOf(true) }
        var drawStatusError by remember(lottery?.id, accountId) { mutableStateOf("") }

        LaunchedEffect(showEntrantActions, lottery?.id, accountId, waitingListController) {
            joinError = ""
            membershipError = false

            if (!showEntrantActions || lottery == null || accountId.isNullOrBlank() || waitingListController == null) {
                checkingMembership = false
            } else {
                checkingMembership = true

                waitingListController.isEntrantJoined(
                    lotteryId = lottery.id,
                    accountId = accountId,
                    onResult = { isJoined ->
                        joined = isJoined
                        membershipError = false
                        checkingMembership = false
                    },
                    onError = { error ->
                        joinError = error
                        membershipError = true
                        checkingMembership = false
                    }
                )
            }
        }
        LaunchedEffect(
            showEntrantActions,
            lottery?.id,
            accountId,
            reservationOfferController
        ) {
            entrantDrawStatus = null
            drawStatusError = ""

            if (
                !showEntrantActions ||
                lottery == null ||
                accountId.isNullOrBlank() ||
                reservationOfferController == null
            ) {
                checkingDrawStatus = false
            } else {
                checkingDrawStatus = true

                reservationOfferController.getEntrantDrawStatus(
                    lotteryId = lottery.id,
                    accountId = accountId,
                    onResult = { status ->
                        entrantDrawStatus = status
                        checkingDrawStatus = false
                    },
                    onError = { error ->
                        drawStatusError = error
                        checkingDrawStatus = false
                    }
                )
            }
        }
        Scaffold(
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !removing,
                        modifier = Modifier.testTag("DetailsBack"),
                    ) {
                        Text("BACK")
                    }
                }
            }
        ) { contentPadding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .testTag("CampsiteDetailsScreen")

            ) {
                /** The following code was generated by OpenAI, ChatGPT,
                 * "Implement loading and error states for the campsite details screen
                 * using Jetpack Compose",
                 * 2026-11-06.
                 */
                if (campsite == null) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (isLoading && !hasError) {
                            CircularProgressIndicator()
                            Text("Loading campsite details...")
                        } else {
                            Text(
                                text = if (hasError) {
                                    "Unable to load campsite details"
                                } else {
                                    "Campsite not found"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )

                            Text(
                                text = if (hasError) {
                                    "Something went wrong."
                                } else {
                                    "This campsite could not be found."
                                }
                            )
                        }
                    }
                } else {
                    promotionalImage(imageUrl = imageUrl, campsiteName = campsite.name, compressedImages = campsite.compressedImages)

                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = campsite.name.ifBlank { "Unnamed campsite" },
                            style = MaterialTheme.typography.headlineLarge
                        )
                        Text("Location: ${campsite.location.ifBlank { "Not provided" }}")

                        val occupancy = if (campsite.maxOccupancy > 0) {
                            campsite.maxOccupancy.toString()
                        } else {
                            "Not provided"
                        }
                        Text("Maximum occupancy: $occupancy")
                        Text("Rating: ${ratingText(campsite)}")
                        Text("Price per night: ${priceText(campsite.pricePerNight)}")

                        val owner = ownerName?.takeIf{ it.isNotBlank() }
                            ?: "Not Provided"
                        Text("Owner: $owner")
                        Text("Description", style = MaterialTheme.typography.titleMedium)
                        Text(campsite.description.ifBlank { "Not provided" })
                        Text(
                            "Rules and cancellation policy",
                            style = MaterialTheme.typography.titleMedium
                        )

                        val policies = (listOf(campsite.rules) + campsite.rulesPolicies)
                            .filter { policy -> policy.isNotBlank() }
                            .distinct()
                        Text(policies.joinToString("\n").ifBlank { "Not provided" })
                        Text("Availability (booking period): ${availabilityText(campsite)}")
                    }

                    CampsiteMapView().MapView(campsite.geolocation, campsite.name)

                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (showEntrantActions && account != null && ratingController != null && onRateCampsite != null) {
                            RatingView().RatingEntry(
                                campsite.id,
                                account,
                                ratingController,
                                onRateCampsite
                            )
                        }

                        // Entrant lottery registration
                        if (showEntrantActions &&
                            account != null &&
                            lottery != null &&
                            !accountId.isNullOrBlank() &&
                            waitingListController != null
                        ) {
                            Spacer(modifier = Modifier.size(16.dp))
                            Button(
                                onClick = {
                                    joining = true
                                    joinError = ""
                                    waitingListController.joinWaitingList(
                                        lotteryId = lottery.id,
                                        accountId = accountId,
                                        onSuccess = {
                                            joined = true
                                            joining = false
                                        },
                                        onError = { error ->
                                            joinError = error
                                            joining = false
                                        }
                                    )
                                },
                                enabled = !joined &&
                                        !checkingMembership &&
                                        !membershipError &&
                                        !joining &&
                                        !leaving &&
                                        System.currentTimeMillis() >= lottery.registrationStart &&
                                        System.currentTimeMillis() < lottery.registrationEnd,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    when {
                                        checkingMembership -> "Checking..."
                                        joining -> "Joining..."
                                        joined -> "Joined"
                                        else -> "Join Lottery"
                                    }
                                )
                            }

                            if (joinError.isNotEmpty()) {
                                Text(
                                    text = joinError,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (joined && !checkingDrawStatus) {
                                when (entrantDrawStatus) {
                                    "WINNER" -> {
                                        Text(
                                            text = "Congratulations! You won the lottery.",
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }

                                    "BACKUP" -> {
                                        Text("You are on the lottery backup list.")
                                    }

                                    "DECLINED" -> {
                                        Text("You have declined this reservation opportunity.")
                                    }
                                }
                            }

                            if (drawStatusError.isNotEmpty()) {
                                Text(
                                    text = drawStatusError,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            if (joined) {
                                val canLeave = !lottery.drawCompleted &&
                                        lottery.drawStatus == "PENDING"
                                Spacer(modifier = Modifier.size(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        showLeaveConfirmation = true
                                    },
                                    enabled = !leaving && !joining && canLeave,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        if (leaving) "Leaving..."
                                        else "Leave Waiting List"
                                    )
                                }
                                if (!canLeave) {
                                    Text(
                                        text = "You cannot leave after the lottery draw has started.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (showLeaveConfirmation) {
                                Dialog( //https://developer.android.com/develop/ui/compose/components/dialog
                                    onDismissRequest = {
                                        if (!leaving) showLeaveConfirmation = false
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surface)
                                            .padding(20.dp)
                                    ) {
                                        Text("Are you sure you want to leave the waiting list?")
                                        Button(
                                            onClick = {
                                                leaving = true
                                                joinError = ""

                                                waitingListController.leaveWaitingList(
                                                    lotteryId = lottery.id,
                                                    accountId = accountId,
                                                    onSuccess = {
                                                        joined = false
                                                        leaving = false
                                                        showLeaveConfirmation = false
                                                    },
                                                    onError = { error ->
                                                        leaving = false
                                                        showLeaveConfirmation = false
                                                        joinError = error
                                                    }
                                                )
                                            },
                                            enabled = !leaving
                                        ) {
                                            Text(if (leaving) "Leaving..." else "Yes, Leave")
                                        }

                                        Button(
                                            onClick = { showLeaveConfirmation = false },
                                            enabled = !leaving
                                        ) {
                                            Text("Cancel")
                                        }
                                    }
                                }
                            }

                        }

                        if (canRemoveCampsite) {
                            removalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                            Button(
                                onClick = { showRemoveConfirmation = true },
                                enabled = !removing,
                                modifier = Modifier.fillMaxWidth().testTag("RemoveCampsite"),
                            ) {
                                Text(
                                    if (removing) {
                                        "Removing..."
                                    } else {
                                        "REMOVE CAMPSITE"
                                    }
                                )
                            }
                        } else if (showOwnerView && account?.id == campsite.ownerID) {
                            ownerControls(
                                campsite = campsite,
                                codeGenerator = codeGenerator,
                                onEdit = onEdit,
                                onManageLottery = onManageLottery
                            )

                        }
                    }
                }
            }
        }
    }

    /**
     * Formats the average of valid stored ratings for display.
     *
     * @param campsite Campsite whose ratings are displayed.
     * @return Formatted average, or a message if no valid ratings exist.
     */
    private fun ratingText(campsite: Campsite): String {
        val ratings = campsite.rating.filter { rating -> rating in 1..5 }
        return if (ratings.isEmpty()) {
            "Not rated"
        } else {
            String.format(Locale.CANADA, "%.2f / 5", ratings.average())
        }
    }

    /**
     * Formats a nightly price in Canadian dollars.
     *
     * @param price Nightly price, or null if no price has been supplied.
     * @return Formatted currency, or a message if the price is unavailable.
     */
    private fun priceText(price: Double?): String {
        return if (price == null || !price.isFinite() || price < 0.0) {
            "Not provided"
        } else {
            NumberFormat.getCurrencyInstance(Locale.CANADA).format(price)
        }
    }

    /**
     * Formats the stored booking period without treating placeholder dates as availability.
     *
     * @param campsite Campsite whose booking period is displayed.
     * @return Formatted booking period, or a message if it is unavailable.
     */
    private fun availabilityText(campsite: Campsite): String {
        if (
            campsite.bookingPeriodStart.time <= 1L ||
            !campsite.bookingPeriodEnd.after(campsite.bookingPeriodStart)
        ) {
            return "Not provided"
        }

        val formatter = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.CANADA)
        return "${formatter.format(campsite.bookingPeriodStart)} to " +
                formatter.format(campsite.bookingPeriodEnd)
    }

    /**
     * Displays a promotional image with loading and missing-image states.
     *
     * @param imageUrl Resolved image URL, or null if unavailable.
     * @param campsiteName Campsite name used in the image's accessibility description.
     */

    @Composable
    private fun promotionalImage(
        imageUrl: String?,
        campsiteName: String,
        compressedImages: List<String>
    ) {
        val images = compressedImages.filter { it.isNotBlank() }

        var currentIndex by remember(images) {
            mutableIntStateOf(0)
        }

        val currentImage = images.getOrNull(currentIndex)

        val decodedImage by produceState<Pair<Boolean, android.graphics.Bitmap?>>(
            initialValue = false to null,
            key1 = currentImage
        ) {
            val decoded = withContext(Dispatchers.Default) {
                try {
                    if (currentImage.isNullOrBlank()) {
                        null
                    } else {
                        val bytes = Base64.decode(
                            currentImage,
                            Base64.DEFAULT
                        )
                        BitmapFactory.decodeByteArray(
                            bytes,
                            0,
                            bytes.size
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            }

            value = true to decoded
        }

        val decodingFinished = decodedImage.first
        val bitmap = decodedImage.second

        var loading by remember(imageUrl) {
            mutableStateOf(true)
        }

        var failed by remember(imageUrl) {
            mutableStateOf(false)
        }

        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when {
                    bitmap != null -> {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription =
                                "Image ${currentIndex + 1} of $campsiteName",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("CampsitePromoImage")
                        )
                    }

                    !currentImage.isNullOrBlank() && !decodingFinished -> {
                        Text(
                            "Loading image...",
                            modifier = Modifier.testTag("CampsiteImagePlaceholder")
                        )
                    }

                    !currentImage.isNullOrBlank() && decodingFinished -> {
                        ImagePlaceholder()
                    }

                    !imageUrl.isNullOrBlank() && !failed -> {
                        AsyncImage(
                            model = imageUrl,
                            contentDescription = "Promotional image of $campsiteName",
                            contentScale = ContentScale.Crop,
                            onSuccess = { loading = false },
                            onError = {
                                failed = true
                                loading = false
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("CampsitePromoImage")
                        )

                        if (loading) {
                            Text("Loading image...")
                        }
                    }

                    else -> {
                        ImagePlaceholder()
                    }
                }
            }

            if (images.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            currentIndex =
                                (currentIndex - 1 + images.size) % images.size
                        }
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous image"
                        )
                    }

                    Text(
                        text = "${currentIndex + 1} / ${images.size}",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    IconButton(
                        onClick = {
                            currentIndex = (currentIndex + 1) % images.size
                        }
                    ) {
                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next image"
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ImagePlaceholder() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Landscape,
                contentDescription = "Default campsite image"
            )

            Text(
                text = "No promotional image",
                modifier = Modifier.testTag("CampsiteImagePlaceholder")
            )
        }
    }

    /**
     * Displays the existing QR export and lottery-management controls for an owner.
     *
     * @param campsite Campsite being managed.
     * @param codeGenerator Function that generates the campsite QR code.
     * @param onManageLottery Called when the owner opens lottery management.
     */
    @Composable
    private fun ownerControls(
        campsite: Campsite,
        codeGenerator: (String, Int) -> ImageBitmap,
        onEdit: ((Int) -> Unit)?,
        onManageLottery: ((String) -> Unit)?
    ) {
        val numericId = campsite.id.toIntOrNull() ?: return

        // Rendering Edit Campsite only when there is an edit callback.
        onEdit?.let { edit ->
            Button(
                onClick = { edit(numericId) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("EDIT DETAILS")
            }
        }
        var codeShow by remember(campsite.id) { mutableStateOf(false) }
        val code = remember(campsite.id) { codeGenerator("campsite", numericId) }
        val fileSaver = PermissionHandlers().imageSaveHandler(code)

        if (codeShow) {
            Image(bitmap = code, contentDescription = "Generated QR code of ${campsite.name}")
        }
        Button(onClick = { codeShow = !codeShow }) {
            Text("Generate/Hide QR")
        }
        if (codeShow) {
            Button(onClick = {
                fileSaver.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "image/png"
                    putExtra(Intent.EXTRA_TITLE, "campsite-qr.png")
                })
            }) {
                Text("Download/Save QR")
            }
        }
        // Rendering Manage Lottery only when there is a management callback.
        onManageLottery?.let { manage ->
            Button(
                onClick = { manage(campsite.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Manage Lottery")
            }
        }
    }
}