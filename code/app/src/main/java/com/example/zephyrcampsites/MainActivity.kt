

/**
 * Sealed class for general screens that could be used across different categories.
 *
 * @param route The resulting navigation route.
 */


package com.example.zephyrcampsites


import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.zephyrcampsites.ui.theme.ZephyrCampsitesTheme
import com.google.android.gms.location.LocationServices


sealed class GeneralScreen(val route: String) {
    data object RoleMenu : GeneralScreen("GeneralScreen/RoleMenu")
    data object Entrant : GeneralScreen("GeneralScreen/Entrant")
    data object Owner : GeneralScreen("GeneralScreen/Owner")
    data object Admin : GeneralScreen("GeneralScreen/Admin")
    data object Profile : GeneralScreen("GeneralScreen/Profile")
    data object PersonalInformation : GeneralScreen("GeneralScreen/PersonalInformation")
    data object Policy : GeneralScreen("GeneralScreen/Policy")
    data object Settings : GeneralScreen("GeneralScreen/Settings")
    data object Trips : GeneralScreen("GeneralScreen/Trips")
    data object ViewReservations : GeneralScreen("GeneralScreen/ViewReservations")
    data object LotterySettings : GeneralScreen("GeneralScreen/LotterySettings")
    data object RegisterCampsite: GeneralScreen("GeneralScreen/RegisterCampsite")
    data object QRScan : GeneralScreen("GeneralScreen/QRScan")
    data object EditCampsite: GeneralScreen("EditCampsite/{id}") {
        fun edit(id: Int) = "EditCampsite/$id"
    }
    data object AdminMenu : GeneralScreen("GeneralScreen/AdminMenu")
    data object AdminBrowseCampsite : GeneralScreen("GeneralScreen/AdminBrowseCampsite")
    data object AdminBrowseProfile : GeneralScreen("GeneralScreen/AdminBrowseProfile")
    data object AdminBrowseImage : GeneralScreen("GeneralScreen/AdminBrowseImage")
    data object AdminNotificationLogs : GeneralScreen("GeneralScreen/AdminNotificationLogs")
    data object AdminMessageLogs : GeneralScreen("GeneralScreen/AdminMessageLogs")
    data object MakeAdmin : GeneralScreen("GeneralScreen/MakeAdmin")
    data object AdminProfileDetail : GeneralScreen("GeneralScreen/AdminProfileDetail/{id}") {
        fun detail(id: String) = "GeneralScreen/AdminProfileDetail/$id"
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
                val navHostController: NavHostController = rememberNavController()


                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navHostController,
                        startDestination = GeneralScreen.RoleMenu.route,
                        modifier = Modifier.safeDrawingPadding()
                    ) {
                        composable(GeneralScreen.RoleMenu.route) {
                            RolePage(navHostController)
                        }


                        composable(route = GeneralScreen.Entrant.route) {
                            RolePage(navHostController, showRoleButtons = true)
                        }


                        composable(route = GeneralScreen.Owner.route) {
                            RolePage(navHostController, showRoleButtons = true)
                        }


                        composable(route = GeneralScreen.Admin.route) {
                            RolePage(navHostController, showRoleButtons = true)
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
@Composable
private fun RolePage(navHostController: NavHostController,
                     showRoleButtons: Boolean = false,) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (!showRoleButtons) {
            Box(modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.bg),
                    contentDescription = "Background",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Column(modifier = Modifier.size(width = 600.dp, height = 600.dp).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "CAMPS\nALBERTA",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontSize = 55.sp,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 25.dp),
                        lineHeight = 60.sp
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(25.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Button(modifier = Modifier.size(width = 200.dp, height = 60.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White),
                            onClick = { navHostController.navigate(GeneralScreen.Entrant.route) }) {
                            Text("Entrant",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                        }
                        Button(modifier = Modifier.size(width = 200.dp, height = 60.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White),
                            onClick = { navHostController.navigate(GeneralScreen.Owner.route) }) {
                            Text("Owner",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall)
                        }
                        //if(userType == "admin") {
                        Button(
                            modifier = Modifier.size(width = 200.dp, height = 60.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = Color.White
                            ),
                            onClick = { navHostController.navigate(GeneralScreen.Admin.route) }) {
                            Text(
                                "Admin",
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        //}
                    }
                }
            }
        }
        else {
            Button(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                onClick = { navHostController.popBackStack() },
                shape = RoundedCornerShape(10.dp),
            ) { Text("Back") }
        }
    }
}