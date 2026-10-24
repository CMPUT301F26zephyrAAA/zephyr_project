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

/**
 * Sealed class for general screens that could be used across different categories.
 * (STC for location, and using sealed class instead of enum for consistency.)
 *
 * @param route The resulting route, in most cases it'll just be the name itself.
 */
sealed class GeneralScreen(val route: String) {
    data object QRScan : GeneralScreen("GeneralScreen/QRScan")
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
                NavHost(
                    navController = navHostController,
                    startDestination = CampsiteScreen.SummaryView.route
                ) { // Start of routes definitions, information on how to use arguments taken from here: https://levelup.gitconnected.com/navigation-in-android-jetpack-compose-a-practical-guide-4d8037b07a87
                    composable(route = CampsiteScreen.SummaryView.route) {
                        campsiteView.SummaryView(
                            onClick = { campsiteController.addCampsite() },
                            openDetail = { id -> navHostController.navigate(CampsiteScreen.DetailView.createRoute(id))},
                            filterPage = { navHostController.navigate(CampsiteScreen.FilterPage.route) },
                            searchHandler = { devLocation, locationLimit, startDate, endDate, guests, search -> campsiteController.search(devLocation, locationLimit, startDate, endDate, guests, search) }
                            )
                    }
                    composable(route = CampsiteScreen.FilterPage.route) {
                        campsiteView.FilterSettings (
                            locationClient = locationClient,
                            toParent = {navHostController.navigate(CampsiteScreen.SummaryView.route)}
                        )
                    }
                    composable(route = GeneralScreen.QRScan.route) {
                        qrHandler.QRScan(
                            campsiteNav = { id -> navHostController.navigate(CampsiteScreen.DetailView.createRoute(id)) },
                            accountNav = { },
                            lotteryNav = { }
                        )
                    }
                    composable(
                        route = CampsiteScreen.DetailView.route,
                        arguments = listOf(navArgument("id") {type = NavType.IntType})
                    ) { backStackEntry ->
                        val campsiteId = backStackEntry.arguments?.getInt("id") ?: return@composable
                        campsiteView.DetailView(campsite = campsiteController.retrieveId(campsiteId), codeGenerator = { type, id -> qrHandler.qRGenerate(type, id)})
                    }
                }
            }
        }
    }
}