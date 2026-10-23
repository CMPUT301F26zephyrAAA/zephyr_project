package com.example.zephyrcampsites

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.location.Location
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.Date

/**
 * Sealed class of list of screens related to campsites.
 * Used for navigation in MainActivity (instead of enum class in order to pass arguments).
 *
 * @param route The resulting route, in most cases it'll just be the name itself.
 */
sealed class CampsiteScreen(val route: String) {
    data object SummaryView : CampsiteScreen("CampsiteScreen/SummaryView/false") {
        fun filter(filters: List<Any>) : String {
            return "CampsiteScreen/SummaryView/true"
        }
    }
    data object DetailView : CampsiteScreen("CampsiteScreen/campsite/{id}") {
        fun createRoute(id: Int) : String {
            return "CampsiteScreen/campsite/$id"
        }
    }
    data object FilterPage : CampsiteScreen("CampsiteScreen/FilterPage")
}

/**
 * View class for campsites.
 *
 * Should have all Composable functions related to campsites.
 */
class CampsiteView : ViewObserver<CampsiteModel> {
    var currentUserFilters : List<Any>? = null
    /**
     * Update function for MVC? But doesn't seem to be needed because of how Jetpack Compose works.
     */
    override fun update(model: CampsiteModel) {
        // ?
    }

    /**
     * Displaying the summary of a list of campsites.
     *
     * @param onClick (Temporary, for testing) Function to call when button is pressed.
     * @param openDetail Method that responds when a details page needs to be opened.
     * @param searchHandler Method that handles the search and returns the result list of campsites
     */
    @Composable
    fun SummaryView(
        // TODO: crashes on re-entering filter page second time, location fetching returns 0,0 every time
        onClick: () -> Unit,
        openDetail: (Int) -> Unit,
        locationGetter: FusedLocationProviderClient,
        searchHandler: (Location?, Int, Date, Date, Int, String) -> List<Campsite>,
        filterPage: () -> Unit,
    ) {
        var deviceLocation : Location? = Location("temp")
        if (ContextCompat.checkSelfPermission(LocalContext.current, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(LocalContext.current, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationGetter.lastLocation.addOnSuccessListener { result ->
                deviceLocation = result
            }
        } else {
            deviceLocation = null
        }
        val validLocation = (deviceLocation != null)

        var searchString = rememberTextFieldState()
        var distanceLimit by remember { mutableIntStateOf(Int.MAX_VALUE) }
        var startDate by remember { mutableStateOf(Date()) }
        var endDate by remember { mutableStateOf(Date()) }
        var guests by remember { mutableIntStateOf(10) }
        if (currentUserFilters != null) {
            searchString = rememberTextFieldState(initialText = currentUserFilters!![0] as String)
            distanceLimit = currentUserFilters!![1] as Int
            startDate = currentUserFilters!![2] as Date
            endDate = currentUserFilters!![3] as Date
            guests = currentUserFilters!![4] as Int
        }
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.size(30.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                TextField(
                    state = searchString,
                    label = { Text("Campsite Search (Name/Location) ") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                TextButton(onClick = { filterPage() }) {
                    Icon(Icons.Filled.Menu, contentDescription = "Filters")
                }
            }
            Spacer(modifier = Modifier.size(30.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = "Name",
                        fontSize = 15.sp,
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                ) {
                    Text(
                        text = "Max Occupant",
                        fontSize = 15.sp,
                    )
                }
            }
            val displayList = if (validLocation) {
                searchHandler(deviceLocation, distanceLimit, startDate, endDate, guests, searchString.text.toString())
            } else {
                searchHandler(null, distanceLimit, startDate, endDate, guests, searchString.text.toString())
            }
            LazyColumn(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                itemsIndexed(displayList) { _, campsite ->
                    CampsiteRow(item = campsite, onClick = { id ->
                        openDetail(id)
                    })
                }
            }
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 30.dp)) {
                Button(onClick = onClick) {
                    Text("testing button")
                }
            }
        }
    }



    @Composable
    fun FilterSettings(
        toParent : () -> Unit
    ) {
        var searchString = rememberTextFieldState()
        var initial by remember { mutableStateOf(true) }
        var dateSelect by remember { mutableStateOf(false) }
        var distanceLimit by remember { mutableIntStateOf(Int.MAX_VALUE) }
        var distanceSelect by remember { mutableStateOf(false) }
        var startDate by remember { mutableStateOf(Date()) }
        var endDate by remember { mutableStateOf(Date()) }
        var guests = rememberTextFieldState()
        var complete by remember { mutableStateOf(false) }
        if (currentUserFilters != null) {
            searchString = rememberTextFieldState(initialText = currentUserFilters!![0] as String)
            distanceLimit = currentUserFilters!![1] as Int
            startDate = currentUserFilters!![2] as Date
            endDate = currentUserFilters!![3] as Date
            guests = rememberTextFieldState(initialText = currentUserFilters!![4] as String)
        }
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    state = searchString,
                    label = { Text("Campsite Search (Name/Location) ") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Choose maximum distance for campsite: ")
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (initial) {
                        Text(text = "Select a distance (default: unlimited)", modifier = Modifier.clickable(onClick = {distanceSelect = !distanceSelect}))
                    } else {
                        Text(text = "${distanceLimit}km" , modifier = Modifier.clickable(onClick = {distanceSelect = !distanceSelect}))
                    }
                    DropdownMenu(
                        expanded = distanceSelect,
                        onDismissRequest = { distanceSelect = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("15km") },
                            onClick = { distanceLimit = 15; initial = false }
                        )
                        DropdownMenuItem(
                            text = { Text("30km") },
                            onClick = { distanceLimit = 30; initial = false }
                        )
                        DropdownMenuItem(
                            text = { Text("50km") },
                            onClick = { distanceLimit = 50; initial = false }
                        )
                        DropdownMenuItem(
                            text = { Text("100km") },
                            onClick = { distanceLimit = 100; initial = false }
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { dateSelect = true }) {
                    Text("Click to select dates you want to stay...")
                }
                if (dateSelect) {
                    DatePicker(onDateRangeSelected = { dateRange ->
                        startDate = Date(dateRange.first!!)
                        endDate = Date(dateRange.second!!)
                        dateSelect = false
                    }, onDismiss = { dateSelect = false })
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    state = guests,
                    label = { Text("Enter amount of guests") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    complete = true
                }) {
                    Text("Back")
                }
            }
        }
        if (complete) {
            currentUserFilters = listOf(searchString.text.toString(), distanceLimit, startDate, endDate, guests.text.toString().toInt())
            toParent()
        }
    }

    @Composable
    private fun DatePicker(
        onDateRangeSelected: (Pair<Long?, Long?>) -> Unit,
        onDismiss: () -> Unit
    ) {
        val dateRangePickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(
                    onClick = {
                        onDateRangeSelected(
                            Pair(
                                dateRangePickerState.selectedStartDateMillis,
                                dateRangePickerState.selectedEndDateMillis
                            )
                        )
                        onDismiss()
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        ) {
            DateRangePicker(
                state = dateRangePickerState,
                title = { Text(text = "Select date range") },
                showModeToggle = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(16.dp)
            )
        }
    }

    /**
     * Displaying the details of a certain campsite.
     *
     * @param campsite The campsite to display the details of
     * @param codeGenerator Function to generate the QR code to this campsite
     */
    @Composable
    fun DetailView(
        campsite: Campsite?,
        codeGenerator: (String, Int) -> ImageBitmap
    ) {
        var codeShow by remember {mutableStateOf(false)}
        val code = codeGenerator("campsite", campsite!!.id.toInt())
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.size(30.dp))
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(text = campsite.name, fontSize = 30.sp)
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(text = campsite.maxOccupancy.toString(), fontSize = 20.sp)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                if (codeShow) {
                    Image(
                        bitmap = code,
                        contentDescription = "Generated QR code of user"
                    )
                }
                Button(onClick = { codeShow = !codeShow }) {
                    Text(text = "Generate/Hide QR")
                }
            }
        }
    }

    /**
     * Private helper function to display each campsite in its own row.
     * Used in SummaryView
     *
     * @param item Campsite to display
     * @param onClick Function passed in to trigger when row is clicked
     */
    @Composable
    private fun CampsiteRow(item: Campsite, onClick: (Int) -> Unit) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .clickable { onClick(item.id.toInt()) }
        ) {
            Text(
                text = item.name,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = item.maxOccupancy.toString(),
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }

}

//@Preview
//@Composable
//fun TestPreview() {
//    val camp1 = Campsite("1")
//    camp1.name = "Test Site 1"
//    camp1.maxOccupancy = 10
//    val camp2 = Campsite("2")
//    camp2.name = "Test Site 2"
//    camp2.maxOccupancy = 20
//    val campsites = listOf(camp1, camp2)
//    val campsiteView = CampsiteView()
//    campsiteView.SummaryView(campsiteList = campsites, onClick = {}, openDetail = {})
//}
