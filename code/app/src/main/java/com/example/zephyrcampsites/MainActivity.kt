package com.example.zephyrcampsites

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme

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
                ) {
                    composable(route = CampsiteScreen.SummaryView.route) {
                        campsiteView.SummaryView(
                            campsiteList = campsiteModel.campsites,
                            onClick = { navHostController.navigate(GeneralScreen.QRScan.route) },
                            openDetail = { id -> navHostController.navigate(CampsiteScreen.DetailView.createRoute(id))
                        })
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