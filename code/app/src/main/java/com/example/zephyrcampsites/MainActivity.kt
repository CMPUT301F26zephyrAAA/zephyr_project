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
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text

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
    private val accountsController = AccountsController()
    private val currentAccount = mutableStateOf<Account?>(null)
    private val accountLoading = mutableStateOf(true)
    override fun onCreate(savedInstanceState: Bundle?) {
        val locationClient: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initializeUser() //identify the user when the app launches
        setContent {
            ZephyrCampsitesTheme {
                if (accountLoading.value) {
                    Text("Account loading...")
                } else if (currentAccount.value == null) {
                    Column {
                        Text("Unable to load account.")
                        Button(onClick = { initializeUser() }) {
                            Text("Retry")
                        }
                    }
                } else {
                    val navHostController: NavHostController = rememberNavController()
                    val qrHandler = QRCodeHandler()
                    val campsiteSummaryView = CampsiteSummaryView()
                    val campsiteModel = CampsiteModel()
                    val campsiteController = CampsiteController(campsiteModel)
                    val campsiteDetailView = CampsiteDetailView()

                    NavHost(
                        navController = navHostController,
                        startDestination = CampsiteScreens.SummaryView.route
                    ) {
                        composable(route = CampsiteScreens.SummaryView.route) {
                            campsiteSummaryView.SummaryView(
                                onClick = { campsiteController.addCampsite() },
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

                        composable(route = GeneralScreen.QRScan.route) {
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

                            campsiteDetailView.DetailView(
                                campsite = campsiteController.retrieveId(campsiteId),
                                codeGenerator = { type, id ->
                                    qrHandler.qRGenerate(type, id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    //AI use : chatgpt - https://chatgpt.com/share/6ae95606-2174-83ed-945d-28ed82129f67
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
