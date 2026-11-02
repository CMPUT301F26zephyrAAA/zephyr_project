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
                val campsiteSummaryView = CampsiteSummaryView()
                val campsiteModel = CampsiteModel()
                val campsiteController = CampsiteController(campsiteModel)
                val campsiteDetailView = CampsiteDetailView()
                NavHost(
                    navController = navHostController,
                    startDestination = CampsiteScreens.SummaryView.route
                ) { // Start of routes definitions, information on how to use arguments taken from here: https://levelup.gitconnected.com/navigation-in-android-jetpack-compose-a-practical-guide-4d8037b07a87
                    composable(route = CampsiteScreens.SummaryView.route) {
                        campsiteSummaryView.SummaryView(
                            onClick = { campsiteController.addCampsite() },
                            openDetail = { id -> navHostController.navigate(CampsiteScreens.DetailView.createRoute(id))},
                            filterPage = { navHostController.navigate(CampsiteScreens.FilterPage.route) },
                            searchHandler = { userFilters -> campsiteController.search(userFilters) },
                            imageLoader = { id -> campsiteController.imageGetter(id) },
                            locationClient = locationClient
                            )
                    }
                    composable(route = GeneralScreen.QRScan.route) {
                        qrHandler.QRScan(
                            campsiteNav = { id -> navHostController.navigate(CampsiteScreens.DetailView.createRoute(id)) },
                            accountNav = { },
                            lotteryNav = { }
                        )
                    }
                    composable(
                        route = CampsiteScreens.DetailView.route,
                        arguments = listOf(navArgument("id") {type = NavType.IntType})
                    ) { backStackEntry ->
                        val campsiteId = backStackEntry.arguments?.getInt("id") ?: return@composable
                        campsiteDetailView.DetailView(
                            campsite = campsiteController.retrieveId(campsiteId),
                            codeGenerator = { type, id -> qrHandler.qRGenerate(type, id) })
                    }
                }
            }
        }
    }
}