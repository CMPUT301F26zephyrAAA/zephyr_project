
package com.example.zephyrcampsites

import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.dp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Sealed class for general screens that could be used across different categories.
 *
 * @param route The resulting navigation route.
 */
sealed class GeneralScreen(val route: String) {
    data object QRScan : GeneralScreen("GeneralScreen/QRScan")
    data object Profile : GeneralScreen("GeneralScreen/Profile")
    data object Trips : GeneralScreen("GeneralScreen/Trips")
    data object DeleteProfile : GeneralScreen("GeneralScreen/DeleteProfile")
    data object LotterySettings : GeneralScreen("GeneralScreen/LotterySettings")
    data object RegisterCampsite: GeneralScreen("GeneralScreen/RegisterCampsite")
    data object AccountLoading : GeneralScreen("GeneralScreen/AccountLoading")
    data object AccountError : GeneralScreen("GeneralScreen/AccountError")
    data object PersonalInformation : GeneralScreen("GeneralScreen/PersonalInformation")
    data object Policy : GeneralScreen("GeneralScreen/Policy")
    data object EditCampsite: GeneralScreen("EditCampsite/{id}") {
        fun edit(id: Int) = "EditCampsite/$id"
    }
    data object Settings : GeneralScreen("GeneralScreen/Settings")
    data object AdminMenu : GeneralScreen("GeneralScreen/AdminMenu")
    data object AdminBrowseCampsite : GeneralScreen("GeneralScreen/AdminBrowseCampsite")
    data object AdminBrowseProfile : GeneralScreen("GeneralScreen/AdminBrowseProfile")

    data object AdminProfileDetail : GeneralScreen("GeneralScreen/AdminProfileDetail/{id}")

    data object AdminBrowseImage : GeneralScreen("GeneralScreen/AdminBrowseImage")
}

class MainActivity : ComponentActivity() {

    private val accountsController = AccountsController()
    private val currentAccount = mutableStateOf<Account?>(null)
    private val accountLoading = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val locationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)

        enableEdgeToEdge()

        // Identify the user automatically when the app launches.
        initializeUser()

        setContent {
            ZephyrCampsitesTheme {
                val navHostController: NavHostController =
                    rememberNavController()
                var showOwnerView by rememberSaveable { mutableStateOf(false) }

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

                // Lottery setup and registration management.
                val lotteryModel = remember { LotteryModel() }

                val lotteryController = remember(lotteryModel) {
                    LotteryController(lotteryModel)
                }

                val lotteryView = remember(lotteryController) {
                    LotteryView(lotteryController)
                }

                // Waiting-list registration for entrants.
                val waitingListModel = remember { WaitingListModel() }
                val waitingListController = remember(waitingListModel) {
                    WaitingListController(waitingListModel)
                }

                //Entrant reservation management
                val reservationsController = remember {
                    ReservationsController()
                }

                // Navigate after account initialization finishes.
                val account = currentAccount.value
                fun ownsCampsite(campsiteId: String?): Boolean {
                    return account?.userType == "owner" && campsiteModel.campsites.any {
                        it.id == campsiteId && it.ownerID == account.id
                    }
                }

                LaunchedEffect(accountLoading.value, account?.id) {
                    if (!accountLoading.value) {

                        val destination = if (account != null) {
                            CampsiteScreens.SummaryView.route
                        } else {
                            GeneralScreen.AccountError.route
                        }

                        navHostController.navigate(destination) {
                            popUpTo(GeneralScreen.AccountLoading.route) {
                                inclusive = true
                            }
                            launchSingleTop = true
                        }
                    }
                }

                // Column containing the profile button and navigation.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                ) {


                    val currentBackStackEntry by
                    navHostController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route

                    // Show the profile button only on the campsite browsing screen.
                    if (
                        !accountLoading.value &&
                        account != null &&
                        currentRoute == CampsiteScreens.SummaryView.route
                    ) {
                       Row(
                           modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                           horizontalArrangement = Arrangement.SpaceBetween,
                           verticalAlignment = Alignment.CenterVertically
                       ) {
                           Button(onClick = {
                               navHostController.navigate(GeneralScreen.Profile.route) })
                           {
                               Text("My Profile")
                           }
                           Button(
                               onClick = {
                                   navHostController.navigate(GeneralScreen.Trips.route)
                               }
                           ) {
                               Text("My Trips")
                           }
                           if (account.userType == "admin") {
                               Button(onClick = { navHostController.navigate(GeneralScreen.AdminMenu.route) }) { Text("ADMIN") }
                           }
                       }
                    }

                    NavHost(
                        navController = navHostController,
                        startDestination = GeneralScreen.AccountLoading.route,
                        modifier = Modifier.weight(1f)
                    ) {
                        // AI Citation [AI-20], [AI-21]
                        // OpenAI, ChatGPT assisted with reservation screen
                        // integration, loading states, and details navigation.
                        // AI usage and citations documented in the GitHub Wiki.
                        composable(route = GeneralScreen.Trips.route) {
                            val entrantAccount = currentAccount.value

                            if (entrantAccount == null) {
                                Text("Unable to load account.")
                            } else {
                                var allReservations by remember(entrantAccount.id) {
                                    mutableStateOf<List<ReservedUser>>(emptyList())
                                }

                                var cutoffTime by remember(entrantAccount.id) {
                                    mutableStateOf(java.util.Date())
                                }

                                val activeReservations = reservationsController.filterActiveReservations(
                                    allReservations,
                                    cutoffTime
                                )

                                val pastReservations = reservationsController.filterPastReservations(
                                    allReservations,
                                    cutoffTime
                                )

                                var activeLoaded by remember(entrantAccount.id) {
                                    mutableStateOf(false)
                                }

                                var pastLoaded by remember(entrantAccount.id) {
                                    mutableStateOf(false)
                                }

                                var errorMessage by remember(entrantAccount.id) {
                                    mutableStateOf<String?>(null)
                                }

                                var retryKey by remember(entrantAccount.id) {
                                    mutableIntStateOf(0)
                                }

                                var selectedReservation by remember {
                                    mutableStateOf<ReservedUser?>(null)
                                }

                                var selectedTripsTab by rememberSaveable {
                                    mutableIntStateOf(1)
                                }


                                LaunchedEffect(entrantAccount.id, retryKey) {
                                    activeLoaded = false
                                    pastLoaded = false
                                    errorMessage = null

                                    reservationsController.getReservations(
                                        accountId = entrantAccount.id,
                                        onSuccess = { reservations ->
                                            allReservations = reservations
                                            cutoffTime = java.util.Date()

                                            activeLoaded = true
                                            pastLoaded = true
                                        },
                                        onError = { exception ->
                                            errorMessage = exception.message
                                                ?: "Unable to load reservations."

                                            activeLoaded = true
                                            pastLoaded = true
                                        }
                                    )
                                }

                                LaunchedEffect(entrantAccount.id) {
                                    while (true) {
                                        delay(1_000L)
                                        cutoffTime = java.util.Date()
                                    }
                                }


                                when {
                                    !activeLoaded || !pastLoaded ||
                                            (campsiteModel.isLoading &&
                                                    campsiteModel.error == null) -> {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            CircularProgressIndicator()
                                            Text("Loading reservations...")
                                        }
                                    }

                                    errorMessage != null || campsiteModel.error != null -> {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text(
                                                errorMessage
                                                    ?: "Unable to load campsite information."
                                            )

                                            Button(onClick = {
                                                retryKey++
                                            }) {
                                                Text("Retry")
                                            }
                                        }
                                    }


                                    else -> {
                                        val selected = selectedReservation

                                        if (selected != null) {
                                            // Check whether the selected reservation is past.
                                            val isPast = pastReservations.any {
                                                it.id == selected.id
                                            }

                                            // Find the matching campsite from already-loaded data.
                                            val campsite = campsiteModel.campsites.find {
                                                it.id == selected.campsiteId
                                            }

                                            ReservationDetailsView(
                                                reservation = selected,
                                                campsite = campsite,
                                                isPast = isPast,

                                                imageContent = { currentCampsite ->
                                                    CampsiteAdminImage(
                                                        path = currentCampsite.imagePath,
                                                        loadImage = { path, callback ->
                                                            campsiteController.loadCampsiteImage(
                                                                path = path,
                                                                onResult = { url -> callback(url) },
                                                                onError = { callback(null) }
                                                            )
                                                        }
                                                    )
                                                },

                                                onViewMap = {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Map integration pending",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },

                                                onMessageOwner = {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Messaging integration pending",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },

                                                onCancelReservation = {
                                                    Toast.makeText(
                                                        this@MainActivity,
                                                        "Cancellation integration pending",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },

                                                onRateCampsite = {
                                                    navHostController.navigate(
                                                        CampsiteScreens.RateCampsite.createRoute(
                                                            selected.campsiteId
                                                        )
                                                    )
                                                },

                                                onBack = {
                                                    selectedReservation = null
                                                }
                                            )
                                        } else {
                                            ReservationsView(
                                                activeReservations = activeReservations,
                                                pastReservations = pastReservations,
                                                selectedTab = selectedTripsTab,
                                                onSelectedTabChange = { selectedTripsTab = it },

                                                campsiteForId = { campsiteId ->
                                                    campsiteModel.campsites.find {
                                                        it.id == campsiteId
                                                    }
                                                },

                                                imageContent = { campsite ->
                                                    CampsiteAdminImage(
                                                        path = campsite.imagePath,
                                                        loadImage = { path, callback ->
                                                            campsiteController.loadCampsiteImage(
                                                                path = path,
                                                                onResult = { url -> callback(url) },
                                                                onError = { callback(null) }
                                                            )
                                                        }
                                                    )
                                                },

                                                onLotteriesClick = {
                                                    navHostController.navigate("allLotteries")
                                                },

                                                onViewDetails = { reservation ->
                                                    selectedReservation = reservation
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        composable("allLotteries") {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                TextButton(
                                    onClick = { navHostController.popBackStack() }
                                ) {
                                    Text("← Back")
                                }
                                Text(
                                    text = "All Lotteries",
                                    style = MaterialTheme.typography.headlineMedium
                                )

                                LazyColumn {
                                    items(lotteryModel.lotteries) { lottery ->
                                        Text(
                                            text = lottery.lotteryName,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    navHostController.navigate("lotteryDetails/${lottery.id}")
                                                }
                                                .padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                        composable(route = GeneralScreen.RegisterCampsite.route) {
                            CampsiteFormView(
                                controller = campsiteController,
                                ownerId = account?.id ?: return@composable,
                                onDone = { navHostController.popBackStack() },
                                onBack = { navHostController.popBackStack() }
                            )
                        }

                        // Account loading screen.
                        composable(
                            route = GeneralScreen.AccountLoading.route
                        ) {
                            Text("Account loading...")
                        }

                        // Account error screen with Retry.
                        composable(
                            route = GeneralScreen.AccountError.route
                        ) {
                            Column {
                                Text("Unable to load account.")

                                Button(
                                    onClick = {
                                        navHostController.navigate(
                                            GeneralScreen.AccountLoading.route
                                        ) {
                                            popUpTo(GeneralScreen.AccountLoading.route) {
                                                inclusive = true
                                            }
                                            launchSingleTop = true
                                        }

                                        initializeUser()
                                    }
                                ) {
                                    Text("Retry")
                                }
                            }
                        }

                        // Campsite summary screen.
                        composable(
                            route = CampsiteScreens.SummaryView.route
                        ) {
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
                                    navHostController.navigate(
                                        CampsiteScreens.FilterPage.route
                                    )
                                },
                                searchHandler = { userFilters ->
                                    campsiteController.search(userFilters)
                                },
                                imageLoader = { id ->
                                    campsiteController.imageGetter(id)
                                },
                                locationClient = locationClient,
                                showOwnerView = showOwnerView,
                                onViewChanged = { isOwner ->
                                    showOwnerView = isOwner
                                },
                                onViewAllLotteries = {
                                    navHostController.navigate("allLotteries")
                                }
                            )
                        }

                        // QR scanning screen.
                        composable(
                            route = GeneralScreen.QRScan.route
                        ) {
                            qrHandler.QRScan(
                                campsiteNav = { id ->
                                    navHostController.navigate(
                                        CampsiteScreens.DetailView.createRoute(id)
                                    )
                                },
                                accountNav = { },
                                lotteryNav = { }
                            )
                        }

                        // Campsite details screen.
                        composable(
                            route = CampsiteScreens.DetailView.route,
                            arguments = listOf(
                                navArgument("id") {
                                    type = NavType.IntType
                                }
                            )
                        ) { backStackEntry ->

                            val campsiteId =
                                backStackEntry.arguments?.getInt("id")
                                    ?: return@composable

                            val now = System.currentTimeMillis()

                            val campsiteLotteries = lotteryModel.lotteries.filter { lottery ->
                                lottery.campsiteId == campsiteId.toString() &&
                                        now < lottery.bookingStart
                            }

                            val campsiteLottery = campsiteLotteries
                                .filter { lottery ->
                                    now >= lottery.registrationStart &&
                                            now < lottery.registrationEnd
                                }
                                .maxByOrNull { it.registrationStart }
                                ?: campsiteLotteries
                                    .filter { lottery ->
                                        now >= lottery.registrationStart
                                    }
                                    .maxByOrNull { it.registrationStart }
                                ?: campsiteLotteries.minByOrNull { it.registrationStart }

                            campsiteDetailView.DetailScreen(
                                campsiteId = campsiteId,
                                controller = campsiteController,
                                accountsController = accountsController,
                                account = account,
                                showOwnerView = showOwnerView,
                                codeGenerator = { type, id -> qrHandler.qRGenerate(type, id) },
                                onBack = { navHostController.popBackStack() },
                                onEdit = { id ->
                                    navHostController.navigate(GeneralScreen.EditCampsite.edit(id))
                                },
                                onManageLottery = { id ->
                                    navHostController.navigate("${GeneralScreen.LotterySettings.route}/$id")
                                },
                                lottery = campsiteLottery,
                                waitingListController = waitingListController,
                                ratingController = ratingController,
                                onRateCampsite = { id -> navHostController.navigate(CampsiteScreens.RateCampsite.createRoute(id)) }
                            )
                        }

                        composable(CampsiteScreens.RateCampsite.route,
                            arguments = listOf(navArgument("id") { type = NavType.IntType })) { entry ->
                            val id = entry.arguments?.getInt("id") ?: return@composable
                            RatingView().RatingScreen(
                                campsite = campsiteController.retrieveId(id), account = account, controller = ratingController,
                                onSaved = { navHostController.popBackStack() }, onBack = { navHostController.popBackStack() }
                            )
                        }

                        //Edit campsite details
                        // The following code was generated by OpenAI, ChatGPT,
                        // "Create an edit campsite navigation route with owner verification",
                        // 2026-11-06.
                        composable(
                            route = GeneralScreen.EditCampsite.route,
                            arguments = listOf(navArgument("id") { type = NavType.IntType })
                        ) { backStackEntry ->

                            val campsiteId = backStackEntry.arguments?.getInt("id")
                                ?: return@composable

                            val campsite = campsiteController.retrieveId(campsiteId)
                            val owner = account ?: return@composable

                            if (
                                campsite == null ||
                                owner.userType != "owner" ||
                                campsite.ownerID != owner.id
                            ) {
                                Text("You do not have permission to edit this campsite.")
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

                                        navHostController.popBackStack()
                                    },
                                    onBack = {
                                        navHostController.popBackStack()
                                    }
                                )
                            }
                        }


                        // Lottery settings screen.
                        composable(
                            route = "${GeneralScreen.LotterySettings.route}/{campsiteId}",
                            arguments = listOf(
                                navArgument("campsiteId") {
                                    type = NavType.StringType
                                }
                            )
                        ) { backStackEntry ->

                            val campsiteId = backStackEntry.arguments
                                ?.getString("campsiteId")
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
                                onBackClick = { navHostController.popBackStack() }
                            )
                        }

                        composable(
                            route = "lotteryDetails/{lotteryId}",
                            arguments = listOf(
                                navArgument("lotteryId") {
                                    type = NavType.StringType
                                }
                            )
                        ) { backStackEntry ->
                            val lotteryId = backStackEntry.arguments
                                ?.getString("lotteryId") ?: return@composable

                            lotteryView.LotteryDetailsView(
                                lotteryId = lotteryId,
                                canEdit = ownsCampsite(lotteryController.getLotteryById(lotteryId)?.campsiteId),
                                onEditClick = {
                                    navHostController.navigate("editLottery/$lotteryId")
                                },
                                onBackClick = {
                                    navHostController.popBackStack()
                                }
                            )
                        }

                        composable(
                            route = "editLottery/{lotteryId}",
                            arguments = listOf(
                                navArgument("lotteryId") {
                                    type = NavType.StringType
                                }
                            )
                        ) { backStackEntry ->
                            val lotteryId = backStackEntry.arguments
                                ?.getString("lotteryId") ?: return@composable

                            if (!ownsCampsite(lotteryController.getLotteryById(lotteryId)?.campsiteId)) {
                                Text("Only the campsite owner can edit its lottery.")
                                return@composable
                            }

                            lotteryView.EditLotteryView(
                                lotteryId = lotteryId,
                                onSaveClick = {
                                    navHostController.popBackStack()
                                },
                                onBackClick = {
                                    navHostController.popBackStack()
                                }
                            )
                        }

                        // Entrant profile screen
                        composable(route = GeneralScreen.Profile.route) {
                            val profileAccount = currentAccount.value

                            if (profileAccount != null) {
                                AccountsView().ProfileOverviewView(
                                    account = profileAccount,
                                    onEdit = {
                                        navHostController.navigate(GeneralScreen.PersonalInformation.route)
                                    },
                                    onPolicy = { navHostController.navigate(GeneralScreen.Policy.route) },
                                    onSettings = { navHostController.navigate(GeneralScreen.Settings.route) },
                                    onDelete = { navHostController.navigate(GeneralScreen.DeleteProfile.route) },
                                    onBack = { navHostController.popBackStack() }
                                )
                            }
                        }


                        // AI Citation [AI-13]
                        // OpenAI, ChatGPT was used to assist with Delete Profile
                        // navigation, Compose state management, Firebase callbacks,
                        // and device account reinitialization.
                        // AI usage and citation documented in the GitHub Wiki.
                        composable(route = GeneralScreen.DeleteProfile.route) {

                            val profileAccount = currentAccount.value
                            val context = LocalContext.current
                            val accountsModel = remember { AccountsModel() }

                            var hasActiveReservations by remember {
                                mutableStateOf(false)
                            }

                            var isCheckingReservations by remember {
                                mutableStateOf(true)
                            }

                            var isDeleting by remember {
                                mutableStateOf(false)
                            }

                            var errorMessage by remember {
                                mutableStateOf<String?>(null)
                            }

                            // Check reservations when the screen opens.
                            LaunchedEffect(profileAccount?.id) {
                                isCheckingReservations = true
                                errorMessage = null

                                if (profileAccount == null) {
                                    errorMessage = "Account not found."
                                    isCheckingReservations = false
                                } else {
                                    accountsModel.hasActiveReservations(
                                        accountId = profileAccount.id,
                                        onResult = { hasActive ->
                                            hasActiveReservations = hasActive
                                            isCheckingReservations = false
                                        },
                                        onError = { exception ->
                                            errorMessage = exception.message
                                                ?: "Unable to check reservations."
                                            isCheckingReservations = false
                                        }
                                    )
                                }
                            }

                            AccountsView().DeleteProfileView(
                                hasActiveReservations = hasActiveReservations,
                                isCheckingReservations = isCheckingReservations,
                                isDeleting = isDeleting,
                                errorMessage = errorMessage,

                                onConfirm = {
                                    if (
                                        !isDeleting &&
                                        !isCheckingReservations &&
                                        errorMessage == null &&
                                        profileAccount != null
                                    ) {
                                        isDeleting = true
                                        errorMessage = null

                                        accountsController.deleteOwnProfile(
                                            context = context,
                                            account = profileAccount,

                                            onSuccess = {
                                                // Reset the current account state.
                                                accountLoading.value = true
                                                currentAccount.value = null

                                                // Return to the account loading screen.
                                                navHostController.navigate(
                                                    GeneralScreen.AccountLoading.route
                                                ) {
                                                    popUpTo(navHostController.graph.id) {
                                                        inclusive = false
                                                    }
                                                    launchSingleTop = true
                                                }

                                                // Initialize a fresh account for this device.
                                                accountsController.initializeAccount(
                                                    context = context,

                                                    onSuccess = { newAccount ->
                                                        currentAccount.value = newAccount
                                                        accountLoading.value = false
                                                    },

                                                    onError = {
                                                        currentAccount.value = null
                                                        accountLoading.value = false
                                                    }
                                                )
                                            },

                                            onError = { message ->
                                                isDeleting = false
                                                errorMessage = message
                                            }
                                        )
                                    }
                                },

                                onCancel = {
                                    if (!isDeleting) {
                                        navHostController.popBackStack()
                                    }
                                }
                            )
                        }



                        composable(route = GeneralScreen.Policy.route) {
                            PolicyView().PolicyAndGuidelinesView(onBack = { navHostController.popBackStack() })
                        }

                        composable(route = GeneralScreen.Settings.route) {
                            val settingsAccount = currentAccount.value ?: return@composable
                            SettingsView().SettingsScreen(
                                account = settingsAccount,
                                accountsController = accountsController,
                                onSaved = { updated ->
                                    currentAccount.value = currentAccount.value?.copy(
                                        notificationsEnabled = updated.notificationsEnabled
                                    )
                                },
                                onBack = { navHostController.popBackStack() }
                            )
                        }

                        composable(route = GeneralScreen.PersonalInformation.route) {
                            val profileAccount = currentAccount.value

                            if (profileAccount != null) {
                                AccountsView().ProfileView(
                                    account = profileAccount,
                                    accountsController = accountsController,
                                    onSaved = {updatedAccount ->
                                        currentAccount.value = updatedAccount
                                    },
                                    onBack = {navHostController.popBackStack()
                                    }
                                )
                            }
                        }

                        // Admin Route
                        composable(route = GeneralScreen.AdminMenu.route) {
                            AdminTestingMenu(
                                onBrowseCampsites = {
                                    navHostController.navigate(GeneralScreen.AdminBrowseCampsite.route)
                                },
                                onBrowseProfiles = { navHostController.navigate(GeneralScreen.AdminBrowseProfile.route) },
                                onBrowseImages = { navHostController.navigate(GeneralScreen.AdminBrowseImage.route) },
                                onBack = { navHostController.popBackStack() }
                            )
                        }

                        // Browse all campsites
                        composable(route = GeneralScreen.AdminBrowseCampsite.route) {
                            AdminCampsiteListView(
                                account = account, campsites = campsiteModel.campsites,
                                isLoading = campsiteModel.isLoading, errorMessage = campsiteModel.error?.message,
                                loadImage = { path, result -> campsiteController.loadCampsiteImage(path, { result(it) }, { result(null) }) },
                                onCampsiteSelected = { navHostController.navigate(CampsiteScreens.DetailView.createRoute(it.id.toInt())) },
                                onBack = { navHostController.popBackStack() }
                            )
                        }

                        // Browse all profiles.
                        composable(
                            route = GeneralScreen.AdminBrowseProfile.route
                        ) {
                            adminProfileView.AdminProfileListView(account, adminAccountsModel,
                                onProfileSelected = { navHostController.navigate("GeneralScreen/AdminProfileDetail/${Uri.encode(it.id)}") },
                                onBack = { navHostController.popBackStack() },
                                loadImage = { path, result ->
                                    campsiteController.loadCampsiteImage(
                                        path,
                                        { result(it) },
                                        { result(null) },
                                    )
                                },
                            )
                        }

                        composable(
                            GeneralScreen.AdminProfileDetail.route,
                            arguments = listOf(navArgument("id") { type = NavType.StringType }),
                        ) { entry ->
                            adminProfileView.AdminProfileDetailScreen(
                                account,
                                entry.arguments?.getString("id") ?: return@composable,
                                accountsController,
                                onRemoved = { navHostController.popBackStack() },
                                onBack = { navHostController.popBackStack() },
                                loadImage = { path, result ->
                                    campsiteController.loadCampsiteImage(
                                        path,
                                        { result(it) },
                                        { result(null) },
                                    )
                                },
                            )
                        }

                        // Browse all images.
                        composable(
                            route = GeneralScreen.AdminBrowseImage.route
                        ) {
                            AdminBrowseImageView(account, adminAccountsModel, campsiteModel,
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
                                onBack = { navHostController.popBackStack() },
                                loadImage = { path, result ->
                                    campsiteController.loadCampsiteImage(
                                        path,
                                        { result(it) },
                                        { result(null) },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Loads the current account and updates the account initialization status.
     */
    // [AI-03] OpenAI, ChatGPT, assistance understanding asynchronous account initialization and callback state. Also was unsure of how to us Toast.makeText
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


@Composable
fun AdminBrowseScreen(
    title: String,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BACK")
        }
    }
}

/**
 * Admin testing menu.
 *
 * Allows navigation to the three admin browse pages.
 * BACK returns to the previous page.
 */
@Composable
fun AdminTestingMenu(
    onBrowseCampsites: () -> Unit,
    onBrowseProfiles: () -> Unit,
    onBrowseImages: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "ADMIN TESTING",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = onBrowseCampsites,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BROWSE CAMPSITES")
        }

        Button(
            onClick = onBrowseProfiles,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BROWSE PROFILES")
        }

        Button(
            onClick = onBrowseImages,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BROWSE IMAGES")
        }

        Spacer(modifier = Modifier.weight(1f))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BACK")
        }
    }
}
