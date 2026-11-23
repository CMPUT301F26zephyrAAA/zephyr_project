package com.example.zephyrcampsites

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.LocationServices
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.delay

sealed class GeneralScreen(val route: String) {
    data object RoleMenu : GeneralScreen("GeneralScreen/RoleMenu")
    data object EntrantMenu : GeneralScreen("GeneralScreen/EntrantMenu")
    data object OwnerMenu : GeneralScreen("GeneralScreen/OwnerMenu")
    data object AdminMenu : GeneralScreen("GeneralScreen/AdminMenu")
    data object Trips : GeneralScreen("entrant/trips")
    data object ViewReservations : GeneralScreen("owner/reservations")
    data object NotificationLogs : GeneralScreen("admin/notification-logs")
    data object MessageLogs : GeneralScreen("admin/message-logs")
    data object MakeAdmin : GeneralScreen("admin/make-admin")
    data object QRScan : GeneralScreen("GeneralScreen/QRScan")
    data object Profile : GeneralScreen("GeneralScreen/Profile")
    data object LotterySettings : GeneralScreen("GeneralScreen/LotterySettings")
    data object RegisterCampsite : GeneralScreen("GeneralScreen/RegisterCampsite")
    data object PersonalInformation : GeneralScreen("GeneralScreen/PersonalInformation")
    data object Policy : GeneralScreen("GeneralScreen/Policy")
    data object Settings : GeneralScreen("GeneralScreen/Settings")

    data object EditCampsite : GeneralScreen("EditCampsite/{id}") {
        fun edit(id: Int) = "EditCampsite/$id"
    }

    data object AdminBrowseCampsite : GeneralScreen("GeneralScreen/AdminBrowseCampsite")
    data object AdminBrowseProfile : GeneralScreen("GeneralScreen/AdminBrowseProfile")
    data object AdminBrowseImage : GeneralScreen("GeneralScreen/AdminBrowseImage")

    data object AdminProfileDetail : GeneralScreen("GeneralScreen/AdminProfileDetail/{id}") {
        fun createRoute(id: String) = "GeneralScreen/AdminProfileDetail/${Uri.encode(id)}"
    }
}

private const val ENTRANT = "entrant"
private const val OWNER = "owner"
private const val ADMIN = "admin"

private fun roleRoute(role: String, route: String): String = "$role/$route"

class MainActivity : ComponentActivity() {
    private val accountsController = AccountsController()
    private val currentAccount = mutableStateOf<Account?>(null)
    private val accountLoading = mutableStateOf(true)
    private val profileDeletionInProgress = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val locationClient = LocationServices.getFusedLocationProviderClient(this)

        enableEdgeToEdge()
        initializeUser()

        setContent {
            ZephyrCampsitesTheme {
                val navHostController = rememberNavController()
                val onBack: () -> Unit = { navHostController.popBackStack() }

                val accountsModel = remember { AccountsModel() }
                val adminProfileView = remember { AdminBrowseProfileView() }
                val qrHandler = remember { QRCodeHandler() }
                val campsiteSummaryView = remember { CampsiteSummaryView() }
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
                    var showDeleteDialog by rememberSaveable(profileAccount.id) {
                        mutableStateOf(false)
                    }
                    var deleteError by remember(profileAccount.id) {
                        mutableStateOf(false)
                    }
                    val isDeleting = profileDeletionInProgress.value

                    AccountsView().ProfileOverviewView(
                        account = profileAccount,
                        onEdit = {
                            navHostController.navigate(
                                roleRoute(role, GeneralScreen.PersonalInformation.route)
                            )
                        },
                        onPolicy = {
                            navHostController.navigate(roleRoute(role, GeneralScreen.Policy.route))
                        },
                        onSettings = {
                            navHostController.navigate(
                                roleRoute(role, GeneralScreen.Settings.route)
                            )
                        },
                        onBack = onBack,
                        onDelete = {
                            deleteError = false
                            showDeleteDialog = true
                        }
                    )

                    if (showDeleteDialog) {
                        var hasActiveReservations by remember(profileAccount.id) {
                            mutableStateOf(false)
                        }
                        var isCheckingReservations by remember(profileAccount.id) {
                            mutableStateOf(true)
                        }
                        var reservationCheckError by remember(profileAccount.id) {
                            mutableStateOf<String?>(null)
                        }
                        var deletionError by remember(profileAccount.id) {
                            mutableStateOf<String?>(null)
                        }
                        LaunchedEffect(profileAccount.id) {
                            accountsModel.hasActiveReservations(
                                accountId = profileAccount.id,
                                onResult = { hasActive ->
                                    hasActiveReservations = hasActive
                                    isCheckingReservations = false
                                },
                                onError = { exception ->
                                    reservationCheckError = exception.message
                                        ?: "Unable to check reservations."
                                    isCheckingReservations = false
                                }
                            )
                        }

                        AccountsView().DeleteProfileDialog(
                            hasActiveReservations = hasActiveReservations,
                            isCheckingReservations = isCheckingReservations,
                            isDeleting = profileDeletionInProgress.value,
                            reservationCheckError = reservationCheckError,
                            deletionError = deletionError,
                            onConfirm = {
                                if (
                                    !profileDeletionInProgress.value &&
                                    !isCheckingReservations &&
                                    reservationCheckError == null &&
                                    currentAccount.value?.id == profileAccount.id
                                ) {
                                    profileDeletionInProgress.value = true
                                    deletionError = null

                                    accountsController.deleteOwnProfile(
                                        context = this@MainActivity,
                                        account = profileAccount,
                                        onSuccess = {
                                            showDeleteDialog = false
                                            currentAccount.value = null
                                            profileDeletionInProgress.value = false
                                            finish()
                                        },
                                        onError = { exception ->
                                            profileDeletionInProgress.value = false
                                            deletionError = exception.message
                                                ?: "Unable to delete your profile."
                                        }
                                    )
                                }
                            },
                            onCancel = {
                                if (!profileDeletionInProgress.value) {
                                    showDeleteDialog = false
                                }
                            }
                        )
                    }
                }

                @Composable
                fun CampsiteSummary(role: String) {
                    val isOwnerView = role == OWNER
                    val onRegister: (() -> Unit)? = if (isOwnerView) {
                        { navHostController.navigate(GeneralScreen.RegisterCampsite.route) }
                    } else {
                        null
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = onBack) { Text("Back") }

                            Button(
                                onClick = {
                                    navHostController.navigate(
                                        roleRoute(role, GeneralScreen.Profile.route)
                                    )
                                }
                            ) {
                                Text("My Profile")
                            }
                        }

                        Box(modifier = Modifier.weight(1f)) {
                            campsiteSummaryView.SummaryView(
                                onClick = onRegister,
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
                                onViewAllLotteries = {
                                    val route = if (isOwnerView) {
                                        "$OWNER/allLotteries"
                                    } else {
                                        GeneralScreen.Trips.route
                                    }

                                    navHostController.navigate(route)
                                }
                            )
                        }
                    }
                }

                @Composable
                fun CampsiteDetails(role: String, campsiteId: Int) {
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
                            RolePage(
                                actions = buildList {
                                    add(RoleAction("Entrant") { openRoleGraph(ENTRANT) })
                                    add(RoleAction("Owner") { openRoleGraph(OWNER) })

                                    if (isAdmin) {
                                        add(RoleAction("Admin") { openRoleGraph(ADMIN) })
                                    }
                                },
                                actionsEnabled = !isLoading && account != null,
                                statusMessage = when {
                                    isLoading -> "Account loading…"
                                    account == null -> "Unable to load account."
                                    else -> null
                                },
                                onRetry = if (!isLoading && account == null) {
                                    { initializeUser() }
                                } else {
                                    null
                                }
                            )
                        }

                        for (role in listOf(ENTRANT, OWNER)) {
                            val isOwnerGraph = role == OWNER
                            val menuRoute = if (isOwnerGraph) {
                                GeneralScreen.OwnerMenu.route
                            } else {
                                GeneralScreen.EntrantMenu.route
                            }
                            val summaryRoute = roleRoute(role, CampsiteScreens.SummaryView.route)
                            val profileRoute = roleRoute(role, GeneralScreen.Profile.route)

                            navigation(route = "mode/$role", startDestination = menuRoute) {
                                composable(menuRoute) {
                                    AccountContent {
                                        val actions = if (isOwnerGraph) {
                                            listOf(
                                                RoleAction("Owned Campsites") {
                                                    navHostController.navigate(summaryRoute)
                                                },
                                                RoleAction("Register Campsite") {
                                                    navHostController.navigate(
                                                        GeneralScreen.RegisterCampsite.route
                                                    )
                                                },
                                                RoleAction("View Lotteries") {
                                                    navHostController.navigate("$OWNER/allLotteries")
                                                },
                                                RoleAction("View Reservations") {
                                                    navHostController.navigate(
                                                        GeneralScreen.ViewReservations.route
                                                    )
                                                },
                                                RoleAction("My Profile") {
                                                    navHostController.navigate(profileRoute)
                                                }
                                            )
                                        } else {
                                            listOf(
                                                RoleAction("Browse Campsites") {
                                                    navHostController.navigate(summaryRoute)
                                                },
                                                RoleAction("Trips") {
                                                    navHostController.navigate(
                                                        GeneralScreen.Trips.route
                                                    )
                                                },
                                                RoleAction("Scan QR Code") {
                                                    navHostController.navigate(
                                                        GeneralScreen.QRScan.route
                                                    )
                                                },
                                                RoleAction("My Profile") {
                                                    navHostController.navigate(profileRoute)
                                                }
                                            )
                                        }

                                        RolePage(actions = actions, onBack = onBack)
                                    }
                                }

                                composable(summaryRoute) {
                                    AccountContent { CampsiteSummary(role) }
                                }

                                composable(roleRoute(role, CampsiteScreens.FilterPage.route)) {
                                    FeaturePage("Campsite filters are unavailable.", onBack)
                                }

                                composable(
                                    route = roleRoute(role, CampsiteScreens.DetailView.route),
                                    arguments = listOf(navArgument("id") { type = NavType.IntType })
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
                                                        "$OWNER/editLottery/${Uri.encode(lotteryId)}"
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
                                            val campsite = campsiteController.retrieveId(campsiteId)

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
                                            navArgument("campsiteId") { type = NavType.StringType }
                                        )
                                    ) { entry ->
                                        val campsiteId = entry.arguments?.getString("campsiteId")
                                            ?: return@composable

                                        AccountContent {
                                            if (ownsCampsite(campsiteId)) {
                                                lotteryView.LotterySettingsView(
                                                    campsiteId = campsiteId,
                                                    onLotteryCreated = { id -> openLottery(OWNER, id) },
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
                                            navArgument("lotteryId") { type = NavType.StringType }
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
                                        AccountContent {
                                            TripsScreen(onBack) {
                                                items(lotteryModel.lotteries) { lottery ->
                                                    LotteryListItem(lottery.lotteryName) {
                                                        openLottery(ENTRANT, lottery.id)
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    composable(GeneralScreen.QRScan.route) {
                                        AccountContent {
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                TextButton(onClick = onBack) { Text("Back") }

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
                                    RolePage(
                                        onBack = onBack,
                                        actions = listOf(
                                            RoleAction("Browse Campsites") {
                                                navHostController.navigate(
                                                    GeneralScreen.AdminBrowseCampsite.route
                                                )
                                            },
                                            RoleAction("Browse Profiles") {
                                                navHostController.navigate(
                                                    GeneralScreen.AdminBrowseProfile.route
                                                )
                                            },
                                            RoleAction("Browse Images") {
                                                navHostController.navigate(
                                                    GeneralScreen.AdminBrowseImage.route
                                                )
                                            },
                                            RoleAction("Settings") {
                                                navHostController.navigate(
                                                    roleRoute(ADMIN, GeneralScreen.Settings.route)
                                                )
                                            },
                                            RoleAction("Notification Logs") {
                                                navHostController.navigate(
                                                    GeneralScreen.NotificationLogs.route
                                                )
                                            },
                                            RoleAction("Message Logs") {
                                                navHostController.navigate(
                                                    GeneralScreen.MessageLogs.route
                                                )
                                            },
                                            RoleAction("Make Admin") {
                                                navHostController.navigate(
                                                    GeneralScreen.MakeAdmin.route
                                                )
                                            }
                                        )
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
                                arguments = listOf(navArgument("id") { type = NavType.StringType })
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
                                                    failure
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
                                                } else {
                                                    campsiteController.removeImage(
                                                        account,
                                                        campsite,
                                                        image.path,
                                                        success,
                                                        failure
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

                            composable(roleRoute(ADMIN, GeneralScreen.Settings.route)) {
                                AdminContent {
                                    account?.let { AccountSettings(it) }
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
        }
    }

    // [AI-03] OpenAI, ChatGPT, assistance with account initialization and callbacks.
    private fun initializeUser() {
        accountLoading.value = true

        accountsController.initializeAccount(
            context = this,
            onSuccess = { account ->
                currentAccount.value = account
                accountLoading.value = false
            },
            onError = { exception ->
                currentAccount.value = null
                accountLoading.value = false

                Toast.makeText(
                    this,
                    "Failed to load account: ${exception.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}

private data class RoleAction(
    val label: String,
    val onClick: () -> Unit
)

@Composable
private fun RolePage(
    actions: List<RoleAction>,
    onBack: (() -> Unit)? = null,
    actionsEnabled: Boolean = true,
    statusMessage: String? = null,
    onRetry: (() -> Unit)? = null
) {
    val isLandingPage = onBack == null
    val shape = RoundedCornerShape(10.dp)
    val green = Color(0xFF2E7D32)
    val menuButtonColors = ButtonDefaults.buttonColors(
        containerColor = if (isLandingPage) Color.White else green,
        contentColor = if (isLandingPage) Color.Black else Color.White
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (isLandingPage) {
            Image(
                painter = painterResource(id = R.drawable.bg),
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (isLandingPage) 0.dp else 72.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .size(width = 600.dp, height = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement =
                    if (isLandingPage) Arrangement.Top else Arrangement.Center
            ) {
                if (isLandingPage) {
                    Text(
                        text = "CAMPS\nALBERTA",
                        modifier = Modifier.padding(vertical = 25.dp),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 55.sp,
                        lineHeight = 60.sp,
                        color = Color.White
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(25.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (action in actions) {
                        Button(
                            onClick = action.onClick,
                            enabled = actionsEnabled,
                            modifier = Modifier.size(
                                width = if (isLandingPage) 200.dp else 240.dp,
                                height = 60.dp
                            ),
                            shape = shape,
                            colors = menuButtonColors
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.titleSmall,
                                fontSize = if (isLandingPage) 25.sp else 18.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    if (statusMessage != null) {
                        Text(
                            text = statusMessage,
                            color = if (isLandingPage) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            textAlign = TextAlign.Center
                        )
                    }

                    if (onRetry != null) {
                        Button(onClick = onRetry, shape = shape, colors = menuButtonColors) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        if (onBack != null) {
            Button(onClick = onBack,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = green,
                    contentColor = Color.White
                )
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun TripsScreen(onBack: () -> Unit, content: LazyListScope.() -> Unit) {
    LotteryListScreen(title = "Trips", onBack = onBack, content = content)
}

@Composable
private fun LotteryListScreen(
    title: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium)
        LazyColumn(modifier = Modifier.weight(1f), content = content)
    }
}

@Composable
private fun LotteryListItem(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp)
    )
}

@Composable
private fun ReservationsScreen(onBack: () -> Unit) {
    FeaturePage("View Reservations", onBack)
}

@Composable
private fun NotificationLogsScreen(onBack: () -> Unit) {
    FeaturePage("Notification Logs", onBack)
}

@Composable
private fun MessageLogsScreen(onBack: () -> Unit) {
    FeaturePage("Message Logs", onBack)
}

@Composable
private fun MakeAdminScreen(onBack: () -> Unit) {
    FeaturePage("Make Admin", onBack)
}

@Composable
private fun FeaturePage(title: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = onBack) { Text("Back") }
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}