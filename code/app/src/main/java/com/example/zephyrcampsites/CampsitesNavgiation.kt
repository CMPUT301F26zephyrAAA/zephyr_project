// Hiral - [AI-02] OpenAI ChatGPT Re-distribute MainActivity.kt
// Connects the entrant, owner, and admin screens to their navigation routes.
// Hiral - [AI-08] OpenAI ChatGPT Implement US-02.04.02
// Hiral - [AI-10] OpenAI ChatGPT Add admin removal flow
package com.example.zephyrcampsites

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import android.content.Context
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import com.google.android.gms.location.FusedLocationProviderClient
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.coroutines.resume

@Composable
internal fun CampsitesNavigation(
    activityContext: Context,
    locationClient: FusedLocationProviderClient,
    accountsController: AccountsController,
    currentAccount: MutableState<Account?>,
    accountLoading: State<Boolean>,
    profileDeletionInProgress: MutableState<Boolean>,
    adminRefreshInProgress: State<Boolean>,
    onRetryAccount: () -> Unit,
    onFinishActivity: () -> Unit
) {
    val navHostController = rememberNavController()
    val onBack: () -> Unit = { navHostController.popBackStack() }

    val accountsModel = remember { AccountsModel() }
    val adminProfileView = remember { AdminBrowseProfileView() }
    val qrHandler = remember { QRCodeHandler() }
    val campsiteSummaryView = remember { CampsiteSummaryView() }
    val myCampsitesView = remember { MyCampsitesView() }
    val campsiteModel = remember { CampsiteModel() }
    val campsiteController = remember(campsiteModel) {
        CampsiteController(campsiteModel)
    }
    val campsiteDetailView = remember { CampsiteDetailView() }
    val ratingController = remember { RatingController() }

    val lotteryModel = remember { LotteryModel() }
    val lotteryController = remember(lotteryModel) {
        LotteryController(lotteryModel)
    }

    val waitingListModel = remember { WaitingListModel() }
    val waitingListController = remember(waitingListModel) {
        WaitingListController(waitingListModel)
    }
    val reservationOfferModel = remember { ReservationOfferModel() }
    val reservationOfferController = remember(reservationOfferModel) {
        ReservationOfferController(reservationOfferModel)
    }
    val lotteryView = remember(
        lotteryController,
        reservationOfferController
    ) {
        LotteryView(
            lotteryController,
            reservationOfferController
        )
    }
    val reservationsController = remember {
        ReservationsController()
    }
    val notificationController = remember { LotteryNotificationController() }

    val account = currentAccount.value
    val isLoading = accountLoading.value
    val isAdmin = !isLoading && account?.userType?.contains(ADMIN) == true

    var revokedOwners by remember(account?.id) { mutableStateOf<Set<String>?>(null) }
    DisposableEffect(account?.id) {
        val listener = account?.let {
            accountsModel.observeOwnerRevocations { flags ->
                if (flags != null) {
                    revokedOwners = flags
                }
            }
        }
        onDispose { listener?.remove() }
    }
    val ownerModeAvailable = account?.canUseOwnerMode(revokedOwners) == true
    val ownerModeRevoked = !isLoading && account != null && !ownerModeAvailable
    val activeEntry by navHostController.currentBackStackEntryAsState()
    LaunchedEffect(ownerModeRevoked, activeEntry) {
        val inOwnerMode = activeEntry?.destination?.hierarchy?.any {
            it.route == "mode/$OWNER"
        } == true
        if (ownerModeRevoked && inOwnerMode) {
            navHostController.popBackStack(GeneralScreen.RoleMenu.route, inclusive = false)
        }
    }

    fun updateCurrentProfile(updated: Account) {
        val latestAccount = currentAccount.value ?: return
        if (latestAccount.id == updated.id) {
            currentAccount.value = updated.copy(userType = latestAccount.userType)
        }
    }

    fun ownsCampsite(campsiteId: String?): Boolean {
        val owner = currentAccount.value ?: return false
        val id = campsiteId?.toIntOrNull() ?: return false
        val campsite = campsiteController.retrieveId(id) ?: return false
        return ownerModeAvailable && campsite.ownerID == owner.id
    }

    fun openRoleGraph(role: String) {
        val signedInAccount = currentAccount.value ?: return
        if (accountLoading.value) return
        if (role == ADMIN && ADMIN !in signedInAccount.userType) return
        if (role == OWNER && !ownerModeAvailable) return

        navHostController.navigate("mode/$role") {
            popUpTo(GeneralScreen.RoleMenu.route)
            launchSingleTop = true
        }
    }

    fun openCampsite(role: String, id: Int) {
        navHostController.navigate(
            roleRoute(role, CampsiteScreens.DetailView.createRoute(id))
        )
    }

    fun openLottery(role: String, id: String) {
        navHostController.navigate("$role/lotteryDetails/${Uri.encode(id)}")
    }

    @Composable
    fun AccountContent(content: @Composable (Account) -> Unit) {
        when {
            isLoading -> FeaturePage("Account loading…", onBack)
            account == null -> FeaturePage("Account unavailable", onBack)
            else -> content(account)
        }
    }

    @Composable
    fun AdminContent(content: @Composable () -> Unit) {
        when {
            isLoading -> FeaturePage("Account loading…", onBack)
            isAdmin -> content()
            else -> FeaturePage("Admin access required", onBack)
        }
    }

    @Composable
    fun AccountSettings(settingsAccount: Account) {
        SettingsView().SettingsScreen(
            account = settingsAccount,
            accountsController = accountsController,
            onSaved = { updated ->
                currentAccount.value = currentAccount.value?.copy(
                    notificationsEnabled = updated.notificationsEnabled
                )
            },
            onBack = onBack
        )
    }

    @Composable
    fun ProfileOverview(role: String, profileAccount: Account) {
        AccountProfilePage(
            role = role,
            profileAccount = profileAccount,
            activityContext = activityContext,
            accountsController = accountsController,
            accountsModel = accountsModel,
            currentAccount = currentAccount,
            profileDeletionInProgress = profileDeletionInProgress,
            navHostController = navHostController,
            onBack = onBack,
            onFinishActivity = onFinishActivity
        )
    }

    @Composable
    fun CampsiteSummary(role: String) {
        val isOwnerView = role == OWNER

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.padding(8.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    )
                ) {
                    Text("Back")

                }
            }

            Box(modifier = Modifier.weight(1f)) {
                campsiteSummaryView.SummaryView(
                    onClick = null,
                    openDetail = { id -> openCampsite(role, id) },
                    filterPage = {
                        navHostController.navigate(
                            roleRoute(role, CampsiteScreens.FilterPage.route)
                        )
                    },
                    searchHandler = { filters ->
                        val hidden = revokedOwners
                        if (hidden == null) {
                            emptyList()
                        } else {
                            campsiteController.search(filters).filter { it.ownerID !in hidden }
                        }
                    },
                    imageLoader = { id -> campsiteController.imageGetter(id) },
                    locationClient = locationClient,
                    showOwnerView = isOwnerView,
                    onViewChanged = null,
                    onViewAllLotteries = null
                )
            }
        }
    }

    // AI Citation [AI-27]
    // OpenAI, ChatGPT assisted with lottery draw status
    // lookups, sorting, and navigation integration.
    // AI usage and citation documented in the GitHub Wiki.
    @Composable
    fun CampsiteDetails(
        role: String,
        campsiteId: Int,
    ) {
        val canManage = role == OWNER && ownsCampsite(campsiteId.toString())
        val now = System.currentTimeMillis()
        var winningLotteryId by remember(account?.id, campsiteId) {
            mutableStateOf<String?>(null)
        }
        var offerLookupError by remember(account?.id, campsiteId) {
            mutableStateOf(false)
        }
        val entrantLotteries = if (role == ENTRANT) {
            lotteryModel.lotteries.filter { lottery ->
                campsiteId.toString() in lottery.participatingCampsiteIds()
            }
        } else {
            emptyList()
        }

        val activeWinningLottery = entrantLotteries.firstOrNull {
            it.id == winningLotteryId
        }

        val availableLotteries = entrantLotteries.filter {
            now < it.bookingStart
        }

        val campsiteLottery = activeWinningLottery
            ?: availableLotteries
                .filter { now >= it.registrationStart && now < it.registrationEnd }
                .maxByOrNull { it.registrationStart }
            ?: availableLotteries
                .filter { now >= it.registrationStart }
                .maxByOrNull { it.registrationStart }
            ?: availableLotteries.minByOrNull { it.registrationStart }

        LaunchedEffect(account?.id, campsiteId, entrantLotteries.toList()) {
            winningLotteryId = null
            offerLookupError = false

            val accountId = account?.id

            if (role != ENTRANT || accountId.isNullOrBlank()) {
                return@LaunchedEffect
            }

            // Check newer lotteries first.
            val orderedLotteries = entrantLotteries.sortedWith(
                compareByDescending<Lottery> { it.registrationStart }
                    .thenBy { it.id }
            )

            var declinedLotteryId: String? = null
            for (lottery in orderedLotteries) {
                val status = kotlinx.coroutines.suspendCancellableCoroutine<String?> {
                        continuation ->
                    reservationOfferController.getEntrantDrawStatus(
                        lotteryId = lottery.id,
                        accountId = accountId,
                        onResult = { result ->
                            if (continuation.isActive) {
                                continuation.resume(result)
                            }
                        },
                        onError = { error ->
                            android.util.Log.e(
                                "ReservationOffer",
                                "Could not check lottery ${lottery.id}: $error"
                            )
                            offerLookupError = true

                            if (continuation.isActive) {
                                continuation.resume(null)
                            }
                        }
                    )
                }

                if (status == "WINNER") {
                    winningLotteryId = lottery.id
                    break
                }

                if (status == "DECLINED" && declinedLotteryId == null) {
                    declinedLotteryId = lottery.id
                }
            }
            if (winningLotteryId == null) {
                winningLotteryId = declinedLotteryId
            }
        }
        if (offerLookupError) {
            android.util.Log.w(
                "ReservationOffer",
                "Some lottery results could not be loaded for campsite $campsiteId"
            )
        }
        campsiteDetailView.DetailScreen(
            campsiteId = campsiteId,
            controller = campsiteController,
            accountsController = accountsController,
            account = account,
            showOwnerView = role == OWNER,
            showEntrantActions = role == ENTRANT,
            showAdminActions = role == ADMIN,
            ratingRemovalEnabled = !adminRefreshInProgress.value,
            codeGenerator = { type, id -> qrHandler.qRGenerate(type, id) },
            onBack = onBack,
            onEdit = if (canManage) {
                { id ->
                    navHostController.navigate(GeneralScreen.EditCampsite.edit(id))
                }
            } else {
                null
            },
            onManageLottery = if (canManage) {
                { id ->
                    navHostController.navigate(
                        "${GeneralScreen.LotterySettings.route}/$id"
                    )
                }
            } else {
                null
            },
            onReservationMessages = if (canManage) {
                { navHostController.navigate(GeneralScreen.ReservationReplies.createRoute(campsiteId.toString())) }
            } else {
                null
            },
            onBookingCalendar = if (canManage) {
                {
                    navHostController.navigate(
                        GeneralScreen.OwnerBookingCalendar.createRoute(campsiteId.toString())
                    )
                }
            } else {
                null
            },
            lottery = campsiteLottery,
            lotteryCampsiteNames = campsiteModel.campsites
                .filter { role != ENTRANT || revokedOwners?.contains(it.ownerID) == false }
                .associate { it.id to it.name },
            waitingListController = waitingListController,
            reservationOfferController = reservationOfferController,
            ratingController = ratingController,
            onRateCampsite = if (role == ENTRANT) {
                { id ->
                    navHostController.navigate(
                        CampsiteScreens.RateCampsite.createRoute(id)
                    )
                }
            } else {
                null
            }
        )
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navHostController,
            startDestination = GeneralScreen.RoleMenu.route,
            modifier = Modifier.safeDrawingPadding()
        ) {
            composable(GeneralScreen.RoleMenu.route) {
                RoleSelectionMenu(
                    isLoading = isLoading,
                    hasAccount = account != null,
                    isAdmin = isAdmin,
                    onSelectRole = { role -> openRoleGraph(role) },
                    onRetryAccount = onRetryAccount,
                    canSelectOwner = ownerModeAvailable
                )
            }

            for (role in listOf(ENTRANT, OWNER)) {
                val isOwnerGraph = role == OWNER
                val menuRoute = if (isOwnerGraph) {
                    GeneralScreen.OwnerMenu.route
                } else {
                    GeneralScreen.EntrantMenu.route
                }
                val summaryRoute =
                    roleRoute(role, CampsiteScreens.SummaryView.route)
                val profileRoute = roleRoute(role, GeneralScreen.Profile.route)

                navigation(route = "mode/$role", startDestination = menuRoute) {
                    composable(menuRoute) {
                        AccountContent {
                            RoleHomeMenu(
                                role = role,
                                isAdmin = isAdmin,
                                onNavigate = { route -> navHostController.navigate(route) },
                                onBack = onBack,
                                canSelectOwner = ownerModeAvailable,
                                onSwitchRole = { targetRole -> openRoleGraph(targetRole) }
                            )
                        }
                    }

                    composable(summaryRoute) {
                        AccountContent { account ->
                            if (role == OWNER) {
                                myCampsitesView.MyCampsitesScreen(
                                    ownerId = account.id,
                                    controller = campsiteController,
                                    onBack = onBack,
                                    openDetail = { id ->
                                        openCampsite(OWNER, id)
                                    }
                                )
                            } else {
                                CampsiteSummary(role)
                            }
                        }
                    }

                    composable(roleRoute(role, CampsiteScreens.FilterPage.route)) {
                        FeaturePage("Campsite filters are unavailable.", onBack)
                    }

                    composable(
                        route = roleRoute(role, CampsiteScreens.DetailView.route),
                        arguments = listOf(navArgument("id") {
                            type = NavType.IntType
                        })
                    ) { entry ->
                        val id = entry.arguments?.getInt("id") ?: return@composable
                        AccountContent {
                            val site = campsiteController.retrieveId(id)
                            val hidden = revokedOwners
                            if (role == ENTRANT && (hidden == null || site == null || site.ownerID in hidden)) {
                                FeaturePage("Campsite unavailable", onBack)
                            } else {
                                CampsiteDetails(role, id)
                            }
                        }
                    }

                    composable(profileRoute) {
                        AccountContent { ProfileOverview(role, it) }
                    }

                    composable(roleRoute(role, GeneralScreen.Policy.route)) {
                        PolicyView().PolicyAndGuidelinesView(onBack = onBack)
                    }

                    composable(roleRoute(role, GeneralScreen.Settings.route)) {
                        AccountContent { AccountSettings(it) }
                    }

                    composable(
                        roleRoute(role, GeneralScreen.PersonalInformation.route)
                    ) {
                        AccountContent { profileAccount ->
                            AccountsView().ProfileView(
                                account = profileAccount,
                                accountsController = accountsController,
                                onSaved = ::updateCurrentProfile,
                                onBack = onBack
                            )
                        }
                    }

                    composable(
                        route = "$role/lotteryDetails/{lotteryId}",
                        arguments = listOf(
                            navArgument("lotteryId") { type = NavType.StringType }
                        )
                    ) { entry ->
                        val lotteryId = entry.arguments?.getString("lotteryId")
                            ?: return@composable

                        AccountContent {
                            val campsiteId = lotteryController
                                .getLotteryById(lotteryId)?.campsiteId

                            if (!isOwnerGraph || !ownsCampsite(campsiteId)) {
                                FeaturePage(
                                    "Only the campsite owner can view lottery details.",
                                    onBack
                                )
                            } else {
                                lotteryView.LotteryDetailsView(
                                    lotteryId = lotteryId,
                                    onEditClick = if (
                                        isOwnerGraph && ownsCampsite(campsiteId)
                                    ) {
                                        {
                                            navHostController.navigate(
                                                "$OWNER/editLottery/${
                                                    Uri.encode(
                                                        lotteryId
                                                    )
                                                }"
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                    onBackClick = onBack,
                                    onNotifyClick = {
                                        navHostController.navigate(
                                            "$OWNER/notifyEntrants/${Uri.encode(lotteryId)}"
                                        )
                                    }
                                )
                            }
                        }
                    }

                    if (isOwnerGraph) {
                        composable("$OWNER/allLotteries") {
                            AccountContent {
                                val ownedLotteries = lotteryModel.lotteries.filter {
                                    ownsCampsite(it.campsiteId)
                                }
                                LotteryListScreen(
                                    title = "My Lotteries",
                                    onBack = onBack,
                                    onCreateLottery = {
                                        navHostController.navigate("${GeneralScreen.LotterySettings.route}/select")
                                    }
                                ) {
                                    if (ownedLotteries.isEmpty()) {
                                        item {
                                            Text("No lotteries to show.", modifier = Modifier.padding(12.dp))
                                        }
                                    }
                                    items(ownedLotteries) { lottery ->
                                        LotteryListItem(lottery.lotteryName) {
                                            openLottery(OWNER, lottery.id)
                                        }
                                    }
                                }
                            }
                        }

                        // AI Citation [AI-39]
                        // OpenAI, Codex, US 02.10.01 owner navigation, 2026-11-27. See the team AI Documentation Google Doc.
                        composable(
                            route = "$OWNER/notifyEntrants/{lotteryId}",
                            arguments = listOf(navArgument("lotteryId") { type = NavType.StringType })
                        ) { entry ->
                            val lotteryId = entry.arguments?.getString("lotteryId")
                                ?: return@composable
                            AccountContent { owner ->
                                val lottery = lotteryController.getLotteryById(lotteryId)
                                if (lottery != null && OWNER in owner.userType &&
                                    ownsCampsite(lottery.campsiteId)
                                ) {
                                    NotifyLotteryEntrantsScreen(
                                        accountId = owner.id,
                                        lottery = lottery,
                                        controller = notificationController,
                                        onBack = onBack,
                                        onViewSent = {
                                            navHostController.navigate(GeneralScreen.SentNotifications.route)
                                        }
                                    )
                                } else {
                                    FeaturePage("Only the campsite owner can notify entrants.", onBack)
                                }
                            }
                        }

                        composable(GeneralScreen.ViewReservations.route) {
                            AccountContent { owner ->
                                if (OWNER in owner.userType) {
                                    OwnerReservationsScreen(
                                        owner = owner,
                                        controller = reservationsController,
                                        accountsController = accountsController,
                                        onBack = onBack
                                    )
                                } else {
                                    FeaturePage("Owner access required", onBack)
                                }
                            }
                        }

                        // AI Citation [AI-40]
                        // OpenAI, Codex, US 02.10.02, 2026-11-27. Generated the owner history routes.
                        // See Bhav's entry in the team's AI Documentation Google Doc.
                        composable(GeneralScreen.SentNotifications.route) {
                            AccountContent { owner ->
                                if (OWNER in owner.userType) {
                                    SentLotteryNotificationsScreen(owner.id, notificationController, onBack) { id ->
                                        val route = GeneralScreen.SentNotifications.route
                                        navHostController.navigate("$route/${Uri.encode(id)}")
                                    }
                                } else {
                                    FeaturePage("Owner access required", onBack)
                                }
                            }
                        }

                        composable(
                            route = GeneralScreen.SentNotificationDetails.route,
                            arguments = listOf(navArgument("notificationId") { type = NavType.StringType })
                        ) { entry ->
                            val id = entry.arguments?.getString("notificationId") ?: return@composable
                            AccountContent { owner ->
                                if (OWNER in owner.userType) {
                                    SentLotteryNotificationDetailsScreen(owner.id, id, notificationController, onBack)
                                } else {
                                    FeaturePage("Owner access required", onBack)
                                }
                            }
                        }

                        composable(
                            route = GeneralScreen.ReservationReplies.route,
                            arguments = listOf(navArgument("campsiteId") { type = NavType.StringType })
                        ) { entry ->
                            val campsiteId = entry.arguments?.getString("campsiteId") ?: return@composable
                            AccountContent { owner ->
                                val campsite = campsiteModel.campsites.firstOrNull {
                                    it.id == campsiteId && it.ownerID == owner.id
                                }
                                if (campsite != null && OWNER in owner.userType) {
                                    OwnerReservationReplies(
                                        owner, campsite, reservationsController, accountsController, onBack
                                    )
                                } else {
                                    FeaturePage("Only the campsite owner can open these reservations.", onBack)
                                }
                            }
                        }

                        composable(
                            route = GeneralScreen.OwnerBookingCalendar.route,
                            arguments = listOf(navArgument("campsiteId") { type = NavType.StringType })
                        ) { entry ->
                            val campsiteId = entry.arguments?.getString("campsiteId")
                                ?: return@composable
                            AccountContent { owner ->
                                val campsite = campsiteModel.campsites.firstOrNull {
                                    it.id == campsiteId && it.ownerID == owner.id
                                }
                                if (campsite != null && OWNER in owner.userType) {
                                    OwnerBookingCalendarScreen(
                                        campsite = campsite,
                                        owner = owner,
                                        controller = reservationsController,
                                        onBack = onBack
                                    )
                                } else {
                                    FeaturePage(
                                        "Only the campsite owner can open this booking calendar.",
                                        onBack
                                    )
                                }
                            }
                        }

                        composable(GeneralScreen.RegisterCampsite.route) {
                            AccountContent { owner ->
                                CampsiteFormView(
                                    controller = campsiteController,
                                    ownerId = owner.id,
                                    onDone = onBack,
                                    onBack = onBack
                                )
                            }
                        }

                        composable(
                            route = GeneralScreen.EditCampsite.route,
                            arguments = listOf(
                                navArgument("id") { type = NavType.IntType }
                            )
                        ) { entry ->
                            val campsiteId = entry.arguments?.getInt("id")
                                ?: return@composable

                            AccountContent { owner ->
                                val campsite =
                                    campsiteController.retrieveId(campsiteId)

                                if (campsite == null || campsite.ownerID != owner.id) {
                                    FeaturePage(
                                        "Only the campsite owner can edit it.",
                                        onBack
                                    )
                                } else {
                                    CampsiteFormView(
                                        controller = campsiteController,
                                        ownerId = owner.id,
                                        campsite = campsite,
                                        account = owner,
                                        onDone = onBack,
                                        onBack = onBack
                                    )
                                }
                            }
                        }

                        composable(
                            route = "${GeneralScreen.LotterySettings.route}/{campsiteId}",
                            arguments = listOf(
                                navArgument("campsiteId") {
                                    type = NavType.StringType
                                }
                            )
                        ) { entry ->
                            val campsiteId =
                                entry.arguments?.getString("campsiteId")
                                    ?: return@composable

                            AccountContent { owner ->
                                if (OWNER in owner.userType &&
                                    (campsiteId == "select" || ownsCampsite(campsiteId))
                                ) {
                                    lotteryView.LotterySettingsView(
                                        campsiteId = campsiteId.takeUnless { it == "select" },
                                        ownedCampsites = campsiteModel.campsites.filter {
                                            it.ownerID == owner.id
                                        },
                                        onLotteryCreated = { id ->
                                            if (campsiteId == "select") {
                                                onBack()
                                            } else {
                                                openLottery(OWNER, id)
                                            }
                                        },
                                        onBackClick = onBack
                                    )
                                } else {
                                    FeaturePage(
                                        "Only the campsite owner can manage its lottery.",
                                        onBack
                                    )
                                }
                            }
                        }

                        composable(
                            route = "$OWNER/editLottery/{lotteryId}",
                            arguments = listOf(
                                navArgument("lotteryId") {
                                    type = NavType.StringType
                                }
                            )
                        ) { entry ->
                            val lotteryId = entry.arguments?.getString("lotteryId")
                                ?: return@composable

                            AccountContent {
                                val campsiteId = lotteryController
                                    .getLotteryById(lotteryId)?.campsiteId

                                if (ownsCampsite(campsiteId)) {
                                    lotteryView.EditLotteryView(
                                        lotteryId = lotteryId,
                                        onSaveClick = onBack,
                                        onBackClick = onBack
                                    )
                                } else {
                                    FeaturePage(
                                        "Only the campsite owner can edit its lottery.",
                                        onBack
                                    )
                                }
                            }
                        }
                    } else {
                        // AI Citation [AI-39]
                        // OpenAI, Codex, US 02.10.01 entrant navigation, 2026-11-27. See the team AI Documentation Google Doc.
                        composable(GeneralScreen.LotteryNotifications.route) {
                            AccountContent { entrant ->
                                LotteryNotificationsScreen(entrant, notificationController, onBack)
                            }
                        }

                        composable(GeneralScreen.Trips.route) {
                            AccountContent { entrant ->
                                MyTripsNavigation(
                                    accountId = entrant.id,
                                    accountsController = accountsController,
                                    reservationsController = reservationsController,
                                    campsiteForId = { id ->
                                        campsiteModel.campsites.firstOrNull { it.id == id }
                                    },
                                    imageContent = { campsite ->
                                        CampsiteAdminImage(campsite.imagePath) { path, result ->
                                            campsiteController.loadCampsiteImage(
                                                path,
                                                { url -> result(url) },
                                                { result(null) }
                                            )
                                        }
                                    },
                                    onOpenCampsite = { campsiteId ->
                                        openCampsite(ENTRANT, campsiteId)
                                    },
                                    onRateCampsite = { campsiteId ->
                                        navHostController.navigate(
                                            CampsiteScreens.RateCampsite.createRoute(campsiteId)
                                        )
                                    },
                                    onBack = onBack
                                )
                            }
                        }

                        composable(GeneralScreen.QRScan.route) {
                            AccountContent {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    TextButton(
                                        onClick = onBack,
                                        modifier = Modifier.padding(8.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF2E7D32),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Text("Back")
                                    }

                                    Box(modifier = Modifier.weight(1f)) {
                                        qrHandler.QRScan(
                                            campsiteNav = { id ->
                                                openCampsite(ENTRANT, id)
                                            },
                                            accountNav = {},
                                            lotteryNav = {}
                                        )
                                    }
                                }
                            }
                        }

                        composable(
                            route = CampsiteScreens.RateCampsite.route,
                            arguments = listOf(
                                navArgument("id") { type = NavType.IntType }
                            )
                        ) { entry ->
                            val id = entry.arguments?.getInt("id")
                                ?: return@composable

                            AccountContent { profileAccount ->
                                RatingView().RatingScreen(
                                    campsite = campsiteController.retrieveId(id),
                                    account = profileAccount,
                                    controller = ratingController,
                                    onSaved = onBack,
                                    onBack = onBack
                                )
                            }
                        }
                    }
                }
            }

            navigation(
                route = "mode/$ADMIN",
                startDestination = GeneralScreen.AdminMenu.route
            ) {
                composable(GeneralScreen.AdminMenu.route) {
                    AdminContent {
                        RoleHomeMenu(
                            role = ADMIN,
                            isAdmin = isAdmin,
                            onNavigate = { route -> navHostController.navigate(route) },
                            onBack = onBack,
                            canSelectOwner = ownerModeAvailable,
                            onSwitchRole = { targetRole -> openRoleGraph(targetRole) }
                        )
                    }
                }

                composable(GeneralScreen.AdminBrowseCampsite.route) {
                    AdminContent {
                        AdminCampsiteListView(
                            account = account,
                            campsites = campsiteModel.campsites,
                            isLoading = campsiteModel.isLoading,
                            errorMessage = campsiteModel.error?.message,
                            loadImage = { path, result ->
                                campsiteController.loadCampsiteImage(
                                    path,
                                    { result(it) },
                                    { result(null) }
                                )
                            },
                            onCampsiteSelected = { campsite ->
                                openCampsite(ADMIN, campsite.id.toInt())
                            },
                            onBack = onBack
                        )
                    }
                }

                composable(
                    route = roleRoute(ADMIN, CampsiteScreens.DetailView.route),
                    arguments = listOf(navArgument("id") { type = NavType.IntType })
                ) { entry ->
                    val id = entry.arguments?.getInt("id") ?: return@composable
                    AdminContent { CampsiteDetails(ADMIN, id) }
                }

                composable(GeneralScreen.AdminBrowseProfile.route) {
                    AdminContent {
                        adminProfileView.AdminProfileListView(
                            account,
                            accountsModel,
                            registeredOwnerIds = campsiteModel.campsites
                                .mapTo(mutableSetOf()) { it.ownerID },
                            loadingCampsites = campsiteModel.isLoading,
                            campsiteLoadError = campsiteModel.error?.message,
                            onProfileSelected = { profile, fromOwners ->
                                navHostController.navigate(
                                    GeneralScreen.AdminProfileDetail.createRoute(
                                        profile.id, fromOwners,
                                    )
                                )
                            },
                            onBack = onBack,
                            loadImage = { path, result ->
                                campsiteController.loadCampsiteImage(
                                    path,
                                    { result(it) },
                                    { result(null) }
                                )
                            }
                        )
                    }
                }

                composable(
                    route = GeneralScreen.AdminProfileDetail.route,
                    arguments = listOf(
                        navArgument("id") { type = NavType.StringType },
                        navArgument("fromOwners") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    )
                ) { entry ->
                    val profileId = entry.arguments?.getString("id")
                        ?: return@composable

                    AdminContent {
                        adminProfileView.AdminProfileDetailScreen(
                            account,
                            profileId,
                            accountsController,
                            onRemoved = onBack,
                            onBack = onBack,
                            showOwnerRemoval = entry.arguments?.getBoolean("fromOwners") == true &&
                                campsiteModel.campsites.any { it.ownerID == profileId },
                            loadImage = { path, result ->
                                campsiteController.loadCampsiteImage(
                                    path,
                                    { result(it) },
                                    { result(null) }
                                )
                            }
                        )
                    }
                }

                composable(GeneralScreen.AdminBrowseImage.route) {
                    AdminContent {
                        AdminBrowseImageView(
                            account,
                            accountsModel,
                            campsiteModel,
                            onRemove = { image, success, failure ->

                                if (image.type == "profile") {

                                    accountsController.removeProfileImage(
                                        account,
                                        Account(image.ownerId),
                                        image.path,
                                        success,
                                        failure,
                                    )

                                } else {

                                    val campsite = campsiteModel.campsites.find {
                                        it.id == image.ownerId
                                    }

                                    if (campsite == null) {
                                        failure(
                                            IllegalStateException(
                                                "Campsite no longer exists."
                                            )
                                        )

                                    } else if (image.isCompressed) {

                                        campsiteController.removeCompressedImage(
                                            account = account,
                                            campsite = campsite,
                                            imageIndex = image.imageIndex,
                                            expectedImage = image.path,
                                            onSuccess = success,
                                            onError = failure
                                        )

                                    } else {

                                        campsiteController.removeImage(
                                            account,
                                            campsite,
                                            image.path,
                                            success,
                                            failure,
                                        )
                                    }
                                }
                            },
                            onBack = onBack,
                            loadImage = { path, result ->
                                campsiteController.loadCampsiteImage(
                                    path,
                                    { result(it) },
                                    { result(null) }
                                )
                            }
                        )
                    }
                }

                composable(roleRoute(ADMIN, GeneralScreen.Profile.route)) {
                    AdminContent {
                        account?.let { ProfileOverview(ADMIN, it) }
                    }
                }

                composable(roleRoute(ADMIN, GeneralScreen.PersonalInformation.route)) {
                    AdminContent {
                        account?.let { profileAccount ->
                            AccountsView().ProfileView(
                                account = profileAccount,
                                accountsController = accountsController,
                                onSaved = ::updateCurrentProfile,
                                onBack = onBack
                            )
                        }
                    }
                }

                composable(roleRoute(ADMIN, GeneralScreen.Policy.route)) {
                    AdminContent {
                        PolicyView().PolicyAndGuidelinesView(onBack = onBack)
                    }
                }

                composable(GeneralScreen.NotificationLogs.route) {
                    AdminContent { NotificationLogsScreen(onBack) }
                }

                composable(GeneralScreen.MessageLogs.route) {
                    AdminContent { MessageLogsScreen(onBack) }
                }

                composable(GeneralScreen.MakeAdmin.route) {
                    AdminContent { MakeAdminScreen(onBack) }
                }
            }
        }
    }
}
