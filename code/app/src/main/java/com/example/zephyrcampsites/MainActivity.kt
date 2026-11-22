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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.LocationServices

sealed class GeneralScreen(val route: String) {
    data object RoleMenu : GeneralScreen("GeneralScreen/RoleMenu")
    data object EntrantMenu : GeneralScreen("GeneralScreen/EntrantMenu")
    data object OwnerMenu : GeneralScreen("GeneralScreen/OwnerMenu")
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

    data object AdminMenu : GeneralScreen("GeneralScreen/AdminMenu")
    data object AdminBrowseCampsite : GeneralScreen("GeneralScreen/AdminBrowseCampsite")
    data object AdminBrowseProfile : GeneralScreen("GeneralScreen/AdminBrowseProfile")
    data object AdminBrowseImage : GeneralScreen("GeneralScreen/AdminBrowseImage")

    data object AdminProfileDetail :
        GeneralScreen("GeneralScreen/AdminProfileDetail/{id}") {
        fun createRoute(id: String) = "GeneralScreen/AdminProfileDetail/${Uri.encode(id)}"
    }
}

class MainActivity : ComponentActivity() {
    private val accountsController = AccountsController()
    private val currentAccount = mutableStateOf<Account?>(null)
    private val accountLoading = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val locationClient = LocationServices.getFusedLocationProviderClient(this)

        enableEdgeToEdge()
        initializeUser()

        setContent {
            ZephyrCampsitesTheme {
                val navHostController = rememberNavController()
                var showOwnerView by rememberSaveable { mutableStateOf(false) }
                var showOwnerRequired by rememberSaveable { mutableStateOf(false) }

                val adminAccountsModel = remember { AccountsModel() }
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

                fun ownsCampsite(campsiteId: String?): Boolean {
                    val owner = account ?: return false
                    val id = campsiteId?.toIntOrNull() ?: return false
                    val campsite = campsiteController.retrieveId(id) ?: return false
                    return owner.userType == "owner" && campsite.ownerID == owner.id
                }

                val isLoading = accountLoading.value
                val isAdmin = !isLoading && account?.userType == "admin"
                val onBack: () -> Unit = { navHostController.popBackStack() }

                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navHostController,
                        startDestination = GeneralScreen.RoleMenu.route,
                        modifier = Modifier.safeDrawingPadding()
                    ) {
                        composable(GeneralScreen.RoleMenu.route) {
                            RolePage(
                                actions = buildList {
                                    add(RoleAction("Entrant") {
                                        navHostController.navigate(GeneralScreen.EntrantMenu.route)
                                    })
                                    add(RoleAction("Owner") {
                                        navHostController.navigate(GeneralScreen.OwnerMenu.route)
                                    })
                                    if (isAdmin) {
                                        add(RoleAction("Admin") {
                                            navHostController.navigate(GeneralScreen.AdminMenu.route)
                                        })
                                    }
                                },
                                actionsEnabled = !isLoading && account != null,
                                statusMessage = when {
                                    isLoading -> "Account loading..."
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

                        composable(GeneralScreen.EntrantMenu.route) {
                            RolePage(
                                onBack = onBack,
                                actions = listOf(
                                    RoleAction("Browse Campsites") {
                                        showOwnerView = false
                                        navHostController.navigate(CampsiteScreens.SummaryView.route)
                                    },
                                    RoleAction("Trips") {
                                        navHostController.navigate(GeneralScreen.Trips.route)
                                    },
                                    RoleAction("Scan QR Code") {
                                        navHostController.navigate(GeneralScreen.QRScan.route)
                                    },
                                    RoleAction("My Profile") {
                                        navHostController.navigate(GeneralScreen.Profile.route)
                                    }
                                )
                            )
                        }

                        composable(GeneralScreen.OwnerMenu.route) {
                            RolePage(
                                onBack = onBack,
                                actions = listOf(
                                    RoleAction("Owned Campsites") {
                                        showOwnerView = true
                                        navHostController.navigate(CampsiteScreens.SummaryView.route)
                                    },
                                    RoleAction("Register Campsite") {
                                        navHostController.navigate(GeneralScreen.RegisterCampsite.route)
                                    },
                                    RoleAction("View Lotteries") {
                                        navHostController.navigate("allLotteries")
                                    },
                                    RoleAction("View Reservations") {
                                        navHostController.navigate(GeneralScreen.ViewReservations.route)
                                    },
                                    RoleAction("My Profile") {
                                        navHostController.navigate(GeneralScreen.Profile.route)
                                    }
                                )
                            )
                        }

                        composable(GeneralScreen.AdminMenu.route) {
                            AdminContent(isLoading, isAdmin, onBack) {
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
                                            navHostController.navigate(GeneralScreen.Settings.route)
                                        },
                                        RoleAction("Notification Logs") {
                                            navHostController.navigate(
                                                GeneralScreen.NotificationLogs.route
                                            )
                                        },
                                        RoleAction("Message Logs") {
                                            navHostController.navigate(GeneralScreen.MessageLogs.route)
                                        },
                                        RoleAction("Make Admin") {
                                            navHostController.navigate(GeneralScreen.MakeAdmin.route)
                                        }
                                    )
                                )
                            }
                        }

                        composable(GeneralScreen.Trips.route) {
                            TripsScreen(onBack = onBack) {
                                items(lotteryModel.lotteries) { lottery ->
                                    LotteryListItem(lottery.lotteryName) {
                                        navHostController.navigate("lotteryDetails/${lottery.id}")
                                    }
                                }
                            }
                        }

                        composable("allLotteries") {
                            LotteryListScreen(title = "All Lotteries", onBack = onBack) {
                                items(lotteryModel.lotteries) { lottery ->
                                    LotteryListItem(lottery.lotteryName) {
                                        navHostController.navigate("lotteryDetails/${lottery.id}")
                                    }
                                }
                            }
                        }

                        composable(GeneralScreen.ViewReservations.route) {
                            ReservationsScreen(onBack = onBack)
                        }

                        composable(GeneralScreen.NotificationLogs.route) {
                            AdminContent(isLoading, isAdmin, onBack) {
                                NotificationLogsScreen(onBack = onBack)
                            }
                        }

                        composable(GeneralScreen.MessageLogs.route) {
                            AdminContent(isLoading, isAdmin, onBack) {
                                MessageLogsScreen(onBack = onBack)
                            }
                        }

                        composable(GeneralScreen.MakeAdmin.route) {
                            AdminContent(isLoading, isAdmin, onBack) {
                                MakeAdminScreen(onBack = onBack)
                            }
                        }

                        composable(GeneralScreen.RegisterCampsite.route) {
                            CampsiteFormView(
                                controller = campsiteController,
                                ownerId = account?.id ?: return@composable,
                                onDone = onBack,
                                onBack = onBack
                            )
                        }

                        composable(CampsiteScreens.SummaryView.route) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = onBack) { Text("Back") }

                                    if (!isLoading && account != null) {
                                        Button(
                                            onClick = {
                                                navHostController.navigate(GeneralScreen.Profile.route)
                                            }
                                        ) {
                                            Text("My Profile")
                                        }
                                    }
                                }

                                Box(modifier = Modifier.weight(1f)) {
                                    campsiteSummaryView.SummaryView(
                                        onClick = {
                                            navHostController.navigate(
                                                GeneralScreen.RegisterCampsite.route
                                            )
                                        },
                                        openDetail = { id ->
                                            navHostController.navigate(
                                                CampsiteScreens.DetailView.createRoute(id)
                                            )
                                        },
                                        filterPage = {
                                            navHostController.navigate(CampsiteScreens.FilterPage.route)
                                        },
                                        searchHandler = { userFilters ->
                                            campsiteController.search(userFilters)
                                        },
                                        imageLoader = { id -> campsiteController.imageGetter(id) },
                                        locationClient = locationClient,
                                        showOwnerView = showOwnerView,
                                        onViewChanged = { isOwner -> showOwnerView = isOwner },
                                        onViewAllLotteries = {
                                            navHostController.navigate("allLotteries")
                                        }
                                    )
                                }
                            }
                        }

                        composable(GeneralScreen.QRScan.route) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                TextButton(onClick = onBack) { Text("Back") }

                                Box(modifier = Modifier.weight(1f)) {
                                    qrHandler.QRScan(
                                        campsiteNav = { id ->
                                            navHostController.navigate(
                                                CampsiteScreens.DetailView.createRoute(id)
                                            )
                                        },
                                        accountNav = {},
                                        lotteryNav = {}
                                    )
                                }
                            }
                        }

                        composable(
                            route = CampsiteScreens.DetailView.route,
                            arguments = listOf(navArgument("id") { type = NavType.IntType })
                        ) { entry ->
                            val campsiteId = entry.arguments?.getInt("id") ?: return@composable
                            val now = System.currentTimeMillis()
                            val campsiteLotteries = lotteryModel.lotteries.filter { lottery ->
                                lottery.campsiteId == campsiteId.toString() &&
                                        now < lottery.bookingStart
                            }
                            val campsiteLottery = campsiteLotteries
                                .filter { now >= it.registrationStart && now < it.registrationEnd }
                                .maxByOrNull { it.registrationStart }
                                ?: campsiteLotteries
                                    .filter { now >= it.registrationStart }
                                    .maxByOrNull { it.registrationStart }
                                ?: campsiteLotteries.minByOrNull { it.registrationStart }

                            campsiteDetailView.DetailScreen(
                                campsiteId = campsiteId,
                                controller = campsiteController,
                                accountsController = accountsController,
                                account = account,
                                showOwnerView = showOwnerView,
                                codeGenerator = { type, id -> qrHandler.qRGenerate(type, id) },
                                onBack = onBack,
                                onEdit = { id ->
                                    navHostController.navigate(GeneralScreen.EditCampsite.edit(id))
                                },
                                onManageLottery = { id ->
                                    navHostController.navigate(
                                        "${GeneralScreen.LotterySettings.route}/$id"
                                    )
                                },
                                lottery = campsiteLottery,
                                waitingListController = waitingListController,
                                ratingController = ratingController,
                                onRateCampsite = { id ->
                                    navHostController.navigate(
                                        CampsiteScreens.RateCampsite.createRoute(id)
                                    )
                                }
                            )
                        }

                        composable(
                            route = CampsiteScreens.RateCampsite.route,
                            arguments = listOf(navArgument("id") { type = NavType.IntType })
                        ) { entry ->
                            val id = entry.arguments?.getInt("id") ?: return@composable

                            RatingView().RatingScreen(
                                campsite = campsiteController.retrieveId(id),
                                account = account,
                                controller = ratingController,
                                onSaved = onBack,
                                onBack = onBack
                            )
                        }

                        composable(
                            route = GeneralScreen.EditCampsite.route,
                            arguments = listOf(navArgument("id") { type = NavType.IntType })
                        ) { entry ->
                            val campsiteId = entry.arguments?.getInt("id") ?: return@composable
                            val campsite = campsiteController.retrieveId(campsiteId)
                            val owner = account ?: return@composable

                            if (
                                campsite == null ||
                                owner.userType != "owner" ||
                                campsite.ownerID != owner.id
                            ) {
                                FeaturePage("You do not have permission to edit this campsite.", onBack)
                            } else {
                                CampsiteFormView(
                                    controller = campsiteController,
                                    ownerId = owner.id,
                                    campsite = campsite,
                                    account = owner,
                                    onDone = {
                                        Toast.makeText(
                                            this@MainActivity,
                                            "Campsite updated successfully!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onBack()
                                    },
                                    onBack = onBack
                                )
                            }
                        }

                        composable(
                            route = "${GeneralScreen.LotterySettings.route}/{campsiteId}",
                            arguments = listOf(navArgument("campsiteId") { type = NavType.StringType })
                        ) { entry ->
                            val campsiteId = entry.arguments?.getString("campsiteId")
                                ?: return@composable

                            if (!ownsCampsite(campsiteId)) {
                                Text("Only the campsite owner can manage its lottery.")
                                return@composable
                            }

                            lotteryView.LotterySettingsView(
                                campsiteId = campsiteId,
                                onLotteryCreated = { lotteryId ->
                                    navHostController.navigate("lotteryDetails/$lotteryId")
                                },
                                onBackClick = onBack
                            )
                        }

                        composable(
                            route = "lotteryDetails/{lotteryId}",
                            arguments = listOf(navArgument("lotteryId") { type = NavType.StringType })
                        ) { entry ->
                            val lotteryId = entry.arguments?.getString("lotteryId")
                                ?: return@composable

                            lotteryView.LotteryDetailsView(
                                lotteryId = lotteryId,
                                onEditClick = {
                                    val campsiteId = lotteryController.getLotteryById(lotteryId)?.campsiteId
                                    if (ownsCampsite(campsiteId)) {
                                        navHostController.navigate("editLottery/$lotteryId")
                                    } else {
                                        showOwnerRequired = true
                                    }
                                },
                                onBackClick = onBack
                            )
                            if (showOwnerRequired) {
                                AlertDialog(
                                    onDismissRequest = { showOwnerRequired = false },
                                    title = { Text("Owner access required") },
                                    text = { Text("Only the campsite owner can edit its lottery.") },
                                    confirmButton = {
                                        TextButton(onClick = { showOwnerRequired = false }) {
                                            Text("OK")
                                        }
                                    }
                                )
                            }
                        }

                        composable(
                            route = "editLottery/{lotteryId}",
                            arguments = listOf(navArgument("lotteryId") { type = NavType.StringType })
                        ) { entry ->
                            val lotteryId = entry.arguments?.getString("lotteryId")
                                ?: return@composable

                            if (!ownsCampsite(lotteryController.getLotteryById(lotteryId)?.campsiteId)) {
                                Text("Only the campsite owner can edit its lottery.")
                                return@composable
                            }

                            lotteryView.EditLotteryView(
                                lotteryId = lotteryId,
                                onSaveClick = onBack,
                                onBackClick = onBack
                            )
                        }

                        composable(GeneralScreen.Profile.route) {
                            val profileAccount = account ?: return@composable

                            AccountsView().ProfileOverviewView(
                                account = profileAccount,
                                onEdit = {
                                    navHostController.navigate(GeneralScreen.PersonalInformation.route)
                                },
                                onPolicy = { navHostController.navigate(GeneralScreen.Policy.route) },
                                onSettings = { navHostController.navigate(GeneralScreen.Settings.route) },
                                onBack = onBack
                            )
                        }

                        composable(GeneralScreen.Policy.route) {
                            PolicyView().PolicyAndGuidelinesView(onBack = onBack)
                        }

                        composable(GeneralScreen.Settings.route) {
                            val settingsAccount = account ?: return@composable

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

                        composable(GeneralScreen.PersonalInformation.route) {
                            val profileAccount = account ?: return@composable

                            AccountsView().ProfileView(
                                account = profileAccount,
                                accountsController = accountsController,
                                onSaved = { updatedAccount -> currentAccount.value = updatedAccount },
                                onBack = onBack
                            )
                        }

                        composable(GeneralScreen.AdminBrowseCampsite.route) {
                            AdminContent(isLoading, isAdmin, onBack) {
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
                                        showOwnerView = false
                                        navHostController.navigate(
                                            CampsiteScreens.DetailView.createRoute(campsite.id.toInt())
                                        )
                                    },
                                    onBack = onBack
                                )
                            }
                        }

                        composable(GeneralScreen.AdminBrowseProfile.route) {
                            AdminContent(isLoading, isAdmin, onBack) {
                                adminProfileView.AdminProfileListView(
                                    account,
                                    adminAccountsModel,
                                    onProfileSelected = { profile ->
                                        navHostController.navigate(
                                            GeneralScreen.AdminProfileDetail.createRoute(profile.id)
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
                            val profileId = entry.arguments?.getString("id") ?: return@composable

                            AdminContent(isLoading, isAdmin, onBack) {
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
                            AdminContent(isLoading, isAdmin, onBack) {
                                AdminBrowseImageView(
                                    account,
                                    adminAccountsModel,
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
                                                    IllegalStateException("Campsite no longer exists.")
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
                    }
                }
            }
        }
    }

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
    val buttonShape = RoundedCornerShape(10.dp)

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
                verticalArrangement = if (isLandingPage) {
                    Arrangement.Top
                } else {
                    Arrangement.Center
                }
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
                            shape = buttonShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isLandingPage) Color.White else Color(0xFF2E7D32),
                                contentColor = if (isLandingPage) Color.Black else Color.White
                            )
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
                        Button(onClick = onRetry, shape = buttonShape) {
                            Text("Retry")
                        }
                    }
                }
            }
        }

        if (onBack != null) {
            Button(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                shape = buttonShape,
                colors = ButtonDefaults.buttonColors(Color(0xFF2E7D32), Color.White)
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun AdminContent(
    isLoading: Boolean,
    isAdmin: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit
) {
    when {
        isLoading -> FeaturePage("Account loading...", onBack)
        isAdmin -> content()
        else -> FeaturePage("Admin access required", onBack)
    }
}

@Composable
private fun TripsScreen(
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    LotteryListScreen(title = "Trips", onBack = onBack, content = content)
}

@Composable
private fun LotteryListScreen(
    title: String,
    onBack: () -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp)
    )
}

@Composable
private fun ReservationsScreen(onBack: () -> Unit) {
    FeaturePage(title = "View Reservations", onBack = onBack)
}

@Composable
private fun NotificationLogsScreen(onBack: () -> Unit) {
    FeaturePage(title = "Notification Logs", onBack = onBack)
}

@Composable
private fun MessageLogsScreen(onBack: () -> Unit) {
    FeaturePage(title = "Message Logs", onBack = onBack)
}

@Composable
private fun MakeAdminScreen(onBack: () -> Unit) {
    FeaturePage(title = "Make Admin", onBack = onBack)
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