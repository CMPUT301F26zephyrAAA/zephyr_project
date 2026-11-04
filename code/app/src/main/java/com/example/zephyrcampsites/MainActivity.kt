package com.example.zephyrcampsites

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Sealed class for general screens that could be used across different categories.
 * (STC for location, and using sealed class instead of enum for consistency.)
 *
 * @param route The resulting route, in most cases it'll just be the name itself.
 */
sealed class GeneralScreen(val route: String) {
    data object QRScan : GeneralScreen("GeneralScreen/QRScan")
    data object LotterySettings : GeneralScreen("GeneralScreen/LotterySettings")
}
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val locationClient : FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZephyrCampsitesTheme {
                val navHostController : NavHostController = rememberNavController()
                val qrHandler = QRCodeHandler()
                val campsiteView = CampsiteView()
                val campsiteModel = CampsiteModel()
                val campsiteController = CampsiteController(campsiteModel)
                // Create the lottery model to manage lottery data and Firebase operations.
                val lotteryModel = remember { LotteryModel() }

                // Create the controller to handle requests between the UI and model.
                val lotteryController = remember(lotteryModel) {
                    LotteryController(lotteryModel)
                }

                // Create the lottery view to display and manage lottery settings.
                val lotteryView = remember(lotteryController) {
                    LotteryView(lotteryController)
                }
                NavHost(
                    navController = navHostController,
                    startDestination = CampsiteScreen.SummaryView.route
                ) { // Start of routes definitions, information on how to use arguments taken from here: https://levelup.gitconnected.com/navigation-in-android-jetpack-compose-a-practical-guide-4d8037b07a87
                    composable(route = CampsiteScreen.SummaryView.route) {
                        Box {
                            // Display the campsite summary screen.
                            campsiteView.SummaryView(
                                onClick = { campsiteController.addCampsite() },
                                openDetail = { id ->
                                    navHostController.navigate(
                                        CampsiteScreen.DetailView.createRoute(id)
                                    )
                                },
                                filterPage = {
                                    navHostController.navigate(CampsiteScreen.FilterPage.route)
                                },
                                searchHandler = { devLocation, locationLimit, startDate, endDate, guests, search ->
                                    campsiteController.search(
                                        devLocation,
                                        locationLimit,
                                        startDate,
                                        endDate,
                                        guests,
                                        search
                                    )
                                },
                                imageLoader = { id -> campsiteController.imageGetter(id) },
                                locationClient = locationClient
                            )


                        }
                    }
                    composable(route = GeneralScreen.QRScan.route) {
                        qrHandler.QRScan(
                            campsiteNav = { id -> navHostController.navigate(CampsiteScreen.DetailView.createRoute(id)) },
                            accountNav = { },
                            // Lottery settings require a selected campsite.
                            // QR navigation is not connected to a campsite yet.
                            lotteryNav = {
                                // Navigation will be added when a campsite ID is available.
                            }
                        )
                    }
                    // Opens the lottery settings screen for the selected campsite.
                    composable(
                        route = "${GeneralScreen.LotterySettings.route}/{campsiteId}",
                        arguments = listOf(
                            navArgument("campsiteId") {
                                type = NavType.StringType
                            }
                        )
                    ) { backStackEntry ->
                        val campsiteId = backStackEntry.arguments
                            ?.getString("campsiteId") ?: return@composable

                        lotteryView.LotterySettingsView(campsiteId)
                    }
                    composable(
                        route = CampsiteScreen.DetailView.route,
                        arguments = listOf(navArgument("id") {type = NavType.IntType})
                    ) { backStackEntry ->
                        val campsiteId = backStackEntry.arguments?.getInt("id") ?: return@composable
                        campsiteView.DetailView(
                            campsite = campsiteController.retrieveId(campsiteId),
                            codeGenerator = { type, id ->
                                qrHandler.qRGenerate(type, id)
                            },
                            onManageLottery = { campsiteId ->
                                // Open lottery settings for the selected campsite.
                                navHostController.navigate(
                                    "${GeneralScreen.LotterySettings.route}/$campsiteId"
                                )
                            }
                        )

                    }
                }
            }
        }
    }
}