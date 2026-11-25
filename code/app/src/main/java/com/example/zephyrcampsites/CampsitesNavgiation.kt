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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import com.google.android.gms.location.FusedLocationProviderClient

@Composable
internal fun CampsitesNavigation(
    activityContext: Context,
    locationClient: FusedLocationProviderClient,
    accountsController: AccountsController,
    currentAccount: MutableState<Account?>,
    accountLoading: State<Boolean>,
    profileDeletionInProgress: MutableState<Boolean>,
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
    val lotteryView = remember(lotteryController) {
        LotteryView(lotteryController)
    }

    val waitingListModel = remember { WaitingListModel() }
    val waitingListController = remember(waitingListModel) {
        WaitingListController(waitingListModel)
    }
    val reservationsController = remember {
        ReservationsController()
    }

    val account = currentAccount.value
    val isLoading = accountLoading.value
    val isAdmin = !isLoading && account?.userType?.contains(ADMIN) == true

    fun ownsCampsite(campsiteId: String?): Boolean {
        val owner = currentAccount.value ?: return false
        val id = campsiteId?.toIntOrNull() ?: return false
        val campsite = campsiteController.retrieveId(id) ?: return false
        return campsite.ownerID == owner.id
    }

    fun openRoleGraph(role: String) {
        val signedInAccount = currentAccount.value ?: return
        if (accountLoading.value) return
        if (role == ADMIN && ADMIN !in signedInAccount.userType) return

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
                    searchHandler = { filters -> campsiteController.search(filters) },
                    imageLoader = { id -> campsiteController.imageGetter(id) },
                    locationClient = locationClient,
                    showOwnerView = isOwnerView,
                    onViewChanged = null,
                    onViewAllLotteries = null
                )
            }
        }
    }

    @Composable
    fun CampsiteDetails(
        role: String,
        campsiteId: Int,
        onSelectedLottery: Lottery? = null,
        onDetailsBack: () -> Unit = onBack
    ) {
        val canManage = role == OWNER && ownsCampsite(campsiteId.toString())
        val now = System.currentTimeMillis()
        val entrantLotteries = if (role == ENTRANT) {
            lotteryModel.lotteries.filter { lottery ->
                lottery.campsiteId == campsiteId.toString() && now < lottery.bookingStart
            }
        } else {
            emptyList()
        }

        val campsiteLottery = entrantLotteries
            .filter { now >= it.registrationStart && now < it.registrationEnd }
            .maxByOrNull { it.registrationStart }
            ?: entrantLotteries
                .filter { now >= it.registrationStart }
                .maxByOrNull { it.registrationStart }
            ?: entrantLotteries.minByOrNull { it.registrationStart }

        campsiteDetailView.DetailScreen(
            campsiteId = campsiteId,
            controller = campsiteController,
            accountsController = accountsController,
            account = account,
            showOwnerView = role == OWNER,
            showEntrantActions = role == ENTRANT,
            showAdminActions = role == ADMIN,
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
            lottery = campsiteLottery,
            waitingListController = waitingListController,
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
                    onRetryAccount = onRetryAccount
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
                        AccountContent { CampsiteDetails(role, id) }
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
                                onSaved = { updated ->
                                    currentAccount.value = updated
                                },
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
                                onBackClick = onBack
                            )
                        }
                    }

                    if (isOwnerGraph) {
                        composable("$OWNER/allLotteries") {
                            AccountContent {
                                LotteryListScreen("All Lotteries", onBack) {
                                    items(lotteryModel.lotteries) { lottery ->
                                        LotteryListItem(lottery.lotteryName) {
                                            openLottery(OWNER, lottery.id)
                                        }
                                    }
                                }
                            }
                        }

                        composable(GeneralScreen.ViewReservations.route) {
                            AccountContent { ReservationsScreen(onBack) }
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

                            AccountContent {
                                if (ownsCampsite(campsiteId)) {
                                    lotteryView.LotterySettingsView(
                                        campsiteId = campsiteId,
                                        onLotteryCreated = { id ->
                                            openLottery(
                                                OWNER,
                                                id
                                            )
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
                            onProfileSelected = { profile ->
                                navHostController.navigate(
                                    GeneralScreen.AdminProfileDetail.createRoute(
                                        profile.id
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
                    arguments = listOf(navArgument("id") {
                        type = NavType.StringType
                    })
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
                                onSaved = { updated -> currentAccount.value = updated },
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