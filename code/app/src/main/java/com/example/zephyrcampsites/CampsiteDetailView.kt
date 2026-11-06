package com.example.zephyrcampsites

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size

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
        onManageLottery: (String) -> Unit,
        lottery: Lottery? = null,
        waitingListController: WaitingListController? = null,
        ratingController: RatingController? = null,
        onRateCampsite: (String) -> Unit = {}
    ) {
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
            imageUrl = imageUrl,
            ownerName = ownerName,
            statusText = when {
                model.error != null -> "Unable to load campsite details."
                model.isLoading -> "Loading campsite details..."
                else -> "Campsite not found."
            },
            onManageLottery = onManageLottery,
            lottery = lottery,
            waitingListController = waitingListController,
            ratingController = ratingController,
            onRateCampsite = onRateCampsite
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
        onManageLottery: (String) -> Unit = {},
        lottery: Lottery? = null,
        waitingListController: WaitingListController? = null,
        previewOwnerMode: Boolean = false,
        ratingController: RatingController? = null,
        onRateCampsite: (String) -> Unit = {}
    ) {

        var previewAsOwner by remember { mutableStateOf(false) }
        val accountId = account?.id
        var joined by remember(lottery?.id, accountId) { mutableStateOf(false) }
        var checkingMembership by remember(lottery?.id, accountId) { mutableStateOf(true) }
        var membershipError by remember (lottery?.id, accountId) {mutableStateOf(false)}
        var joining by remember(lottery?.id, accountId) { mutableStateOf(false) }
        var leaving by remember(lottery?.id, accountId) { mutableStateOf(false) }
        var showLeaveConfirmation by remember(lottery?.id, accountId) { mutableStateOf(false) }
        var joinError by remember(lottery?.id, accountId) { mutableStateOf("") }

        LaunchedEffect(lottery?.id, accountId, waitingListController) {
            joinError = ""
            membershipError = false

            if (lottery == null || accountId.isNullOrBlank() || waitingListController == null) {
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
        Scaffold(
            bottomBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.testTag("DetailsBack")) {
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
                if (campsite == null) {
                    Text(statusText, modifier = Modifier.padding(20.dp))
                } else {
                    promotionalImage(imageUrl = imageUrl, campsiteName = campsite.name)

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

                        val owner = if (!ownerName.isNullOrBlank()) {
                            ownerName
                        } else {
                            campsite.ownerID.ifBlank { "Not provided" }
                        }
                        Text("Owner: $owner")
                        Text("Description", style = MaterialTheme.typography.titleMedium)
                        Text(campsite.description.ifBlank { "Not provided" })
                        Text("Rules and cancellation policy", style = MaterialTheme.typography.titleMedium)

                        val policies = (listOf(campsite.rules) + campsite.rulesPolicies)
                            .filter { policy -> policy.isNotBlank() }
                            .distinct()
                        Text(policies.joinToString("\n").ifBlank { "Not provided" })
                        Text("Availability (booking period): ${availabilityText(campsite)}")

                        if (account?.userType == "entrant" && ratingController != null) {
                            RatingView().RatingEntry(campsite.id, account, ratingController, onRateCampsite)
                        }

                        // Entrant lottery registration
                        if (
                            !showOwnerView &&
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

                            if (joined){
                                Spacer(modifier = Modifier.size(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        showLeaveConfirmation = true
                                    },
                                    enabled = !leaving && !joining,
                                    modifier = Modifier.fillMaxWidth()
                                ){
                                    Text(
                                        if (leaving) "Leaving..."
                                        else "Leave Waiting List"
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

                        // Replace preview mode with actual campsite ownership validation
                        // when account integration is completed. this is just for the testing purpose.
                        if (showOwnerView) {
                            ownerControls(
                                campsite = campsite,
                                codeGenerator = codeGenerator,
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
    private fun promotionalImage(imageUrl: String?, campsiteName: String) {
        var loading by remember(imageUrl) { mutableStateOf(true) }
        var failed by remember(imageUrl) { mutableStateOf(false) }

        Box(
            modifier = Modifier.fillMaxWidth().height(153.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank() && !failed) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Promotional image of $campsiteName",
                    contentScale = ContentScale.Crop,
                    onSuccess = { loading = false },
                    onError = {
                        failed = true
                        loading = false
                    },
                    modifier = Modifier.fillMaxSize().testTag(
                        if (loading) {
                            "CampsitePromoImageLoading"
                        } else {
                            "CampsitePromoImage"
                        }
                    )
                )
            }

            if (imageUrl.isNullOrBlank() || failed || loading) {
                Text(
                    text = if (imageUrl.isNullOrBlank() || failed) {
                        "No promotional image"
                    } else {
                        "Loading image..."
                    },
                    modifier = Modifier.testTag("CampsiteImagePlaceholder")
                )
            }
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
        onManageLottery: (String) -> Unit
    ) {
        val numericId = campsite.id.toIntOrNull() ?: return
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
        Button(onClick = { onManageLottery(campsite.id) }, modifier = Modifier.fillMaxWidth()) {
            Text("Manage Lottery")
        }
    }

}