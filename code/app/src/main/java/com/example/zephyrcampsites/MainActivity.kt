package com.example.zephyrcampsites

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.LocationServices


/**
 * Sealed class for general screens that could be used across different categories.
 *
 * @param route The resulting navigation route.
 */
sealed class GeneralScreen(val route: String) {
    data object QRScan : GeneralScreen("GeneralScreen/QRScan")
    data object Profile : GeneralScreen("GeneralScreen/Profile")
    data object LotterySettings : GeneralScreen("GeneralScreen/LotterySettings")
    data object RegisterCampsite : GeneralScreen("GeneralScreen/RegisterCampsite")
    data object AccountLoading : GeneralScreen("GeneralScreen/AccountLoading")
    data object AccountError : GeneralScreen("GeneralScreen/AccountError")
    data object PersonalInformation : GeneralScreen("GeneralScreen/PersonalInformation")
    data object Policy : GeneralScreen("GeneralScreen/Policy")
    data object Settings : GeneralScreen("GeneralScreen/Settings")
}

/**
 * Identifies the current device and connects the models, controllers, and app screens.
 */
class MainActivity : ComponentActivity() {
    private val accountsController = AccountsController()
    private val currentAccount = mutableStateOf<Account?>(null)
    private val accountLoading = mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val locationClient = LocationServices.getFusedLocationProviderClient(this)
        enableEdgeToEdge()

        // Identify the user automatically when the app launches.
        initializeUser()

        setContent {
            ZephyrCampsitesTheme {
                val navHostController = rememberNavController()
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

                // Navigate after account initialization finishes.
                val account = currentAccount.value
                LaunchedEffect(accountLoading.value, account?.id) {
                    if (!accountLoading.value) {
                        val destination = if (account != null) {
                            CampsiteScreens.SummaryView.route
                        } else {
                            GeneralScreen.AccountError.route
                        }
                        navHostController.navigate(destination) {
                            popUpTo(GeneralScreen.AccountLoading.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    val currentBackStackEntry by navHostController.currentBackStackEntryAsState()
                    val currentRoute = currentBackStackEntry?.destination?.route

                    // Show the profile button only on the campsite browsing screen.
                    if (!accountLoading.value && account != null &&
                        currentRoute == CampsiteScreens.SummaryView.route
                    ) {
                        Button(onClick = { navHostController.navigate(GeneralScreen.Profile.route) }) {
                            Text("My Profile")
                        }
                    }

                    NavHost(
                        navController = navHostController,
                        startDestination = GeneralScreen.AccountLoading.route,
                        modifier = Modifier.weight(1f)
                    ) {
                        composable(route = GeneralScreen.RegisterCampsite.route) {
                            CampsiteFormView(
                                controller = campsiteController,
                                ownerId = account?.id ?: return@composable,
                                onDone = { navHostController.popBackStack() },
                                onBack = { navHostController.popBackStack() }
                            )
                        }

                        // Account loading screen.
                        composable(route = GeneralScreen.AccountLoading.route) {
                            Text("Account loading...")
                        }

                        // Account error screen with Retry.
                        composable(route = GeneralScreen.AccountError.route) {
                            Column {
                                Text("Unable to load account.")
                                Button(onClick = {
                                    navHostController.navigate(GeneralScreen.AccountLoading.route) {
                                        popUpTo(GeneralScreen.AccountError.route) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                    initializeUser()
                                }) {
                                    Text("Retry")
                                }
                            }
                        }

                    composable(route = CampsiteScreens.SummaryView.route) {
                        campsiteSummaryView.SummaryView(
                            onClick = { navHostController.navigate(GeneralScreen.RegisterCampsite.route) },
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
                            locationClient = locationClient
                        )
                    }

                        // QR scanning screen.
                        composable(route = GeneralScreen.QRScan.route) {
                            qrHandler.QRScan(
                                campsiteNav = { id ->
                                    navHostController.navigate(CampsiteScreens.DetailView.createRoute(id))
                                },
                                accountNav = {},
                                lotteryNav = {}
                            )
                        }

                        // Campsite details screen.
                        composable(
                            route = CampsiteScreens.DetailView.route,
                            arguments = listOf(navArgument("id") { type = NavType.IntType })
                        ) { backStackEntry ->
                            val campsiteId = backStackEntry.arguments?.getInt("id")
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
                                codeGenerator = { type, id -> qrHandler.qRGenerate(type, id) },
                                onBack = { navHostController.popBackStack() },
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

                        // Lottery settings screen.
                        composable(
                            route = "${GeneralScreen.LotterySettings.route}/{campsiteId}",
                            arguments = listOf(navArgument("campsiteId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val campsiteId = backStackEntry.arguments?.getString("campsiteId")
                                ?: return@composable
                            lotteryView.LotterySettingsView(campsiteId)
                        }

                        // Entrant profile screen.
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
                                    onBack = { navHostController.popBackStack() }
                                )
                            }
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
                                    onSaved = { currentAccount.value = it },
                                    onBack = { navHostController.popBackStack() }
                                )
                            }
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
