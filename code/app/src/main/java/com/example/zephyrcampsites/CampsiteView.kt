package com.example.zephyrcampsites

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.location.FusedLocationProviderClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Sealed class of list of screens related to campsites.
 * Used for navigation in MainActivity (instead of enum class in order to pass arguments).
 *
 * @param route The resulting route, in most cases it'll just be the name itself.
 */
sealed class CampsiteScreen(val route: String) {
    data object SummaryView : CampsiteScreen("CampsiteScreen/SummaryView/")
    data object DetailView : CampsiteScreen("CampsiteScreen/campsite/{id}") {
        /**
         * Creates an individual route for provided campsite ID
         *
         * @param id ID of campsite to generate route for.
         */
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
    var currentUserFilters : MutableList<Any?> = mutableListOf("", null, Int.MAX_VALUE, Date(), Date(), 1) // Holds user filters that they have set, in (Search string, device location, distance limit, start date, end date, # of guests)
    var dateSelected = false

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
     * @param filterPage Method that responds when a filter page needs to be opened.
     */

    @Composable
    fun SummaryView(
        onClick: () -> Unit,
        openDetail: (Int) -> Unit,
        searchHandler: (Location?, Int, Date, Date, Int, String) -> List<Campsite>,
        filterPage: () -> Unit,
    ) {
        // Acquires current filters from currentUserFilters
        val searchString : TextFieldState = rememberTextFieldState(initialText = currentUserFilters[0] as String)
        var distanceLimit by remember { mutableIntStateOf(currentUserFilters[2] as Int) }
        var startDate by remember { mutableStateOf(currentUserFilters[3] as Date) }
        var endDate by remember { mutableStateOf(currentUserFilters[4] as Date) }
        var guests by remember { mutableIntStateOf(currentUserFilters[5] as Int) }

        currentUserFilters[0] = searchString.text // For syncing currentUserFilters when user enters search string on summary page

        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.size(50.dp))
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)) {
                TextField(
                    state = searchString,
                    label = { Text("Campsite Search (Name/Location) ") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                TextButton(onClick = {
                    filterPage() }) {
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
            val displayList = searchHandler(currentUserFilters[1] as Location?, distanceLimit, startDate, endDate, guests, searchString.text.toString()) // Search function can handle any nulls
            LazyColumn(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                itemsIndexed(displayList) { _, campsite ->
                    CampsiteRow(item = campsite, onClick = { id ->
                        openDetail(id) // TODO: make rectangular popup that opens with summary and pushes down other rows?
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

    /**
     * Displays the settings page for filters to search campsites.
     * @OptIn is used for the dropdown menu for location (as it seems to be a new thing?)
     * @SuppressLint is used for shushing IDE about missing permissions, checks are implemented already
     *
     * @param locationClient Location provider client, used to get device location. Has to be instantiated inside MainActivity's onCreate method, therefore it is passed in.
     * @param toParent Method to respond for returning to parent page (SummaryView)
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("MissingPermission")
    @Composable
    fun FilterSettings(
        locationClient: FusedLocationProviderClient,
        toParent : () -> Unit
    ) {
        // For grabbing user location
        var deviceLocation by remember {mutableStateOf<Location?>(currentUserFilters[1] as Location?)}
        val permissionLauncher = rememberLauncherForActivityResult( // Launcher to request location permissions
            ActivityResultContracts.RequestMultiplePermissions()
        ) { isGranted ->
            val fineLocationCheck = isGranted[Manifest.permission.ACCESS_FINE_LOCATION]
            val coarseLocationCheck = isGranted[Manifest.permission.ACCESS_COARSE_LOCATION]
            if (fineLocationCheck == true || coarseLocationCheck == true) {
                locationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        deviceLocation = location
                    } else {
                        locationClient.lastLocation.addOnSuccessListener { location -> deviceLocation = location }
                    }
                }
            } // If no permission, location stays null
        }

        // Acquires current filters from currentUserFilters
        val searchString : TextFieldState = rememberTextFieldState(initialText = currentUserFilters[0] as String)
        var distanceLimit by remember { mutableIntStateOf(currentUserFilters[2] as Int) }
        var startDate by remember { mutableStateOf(currentUserFilters[3] as Date) }
        var endDate by remember { mutableStateOf(currentUserFilters[4] as Date) }
        val guests : TextFieldState = rememberTextFieldState(initialText = currentUserFilters[5].toString())

        // Configurations for filter options
        val distanceOptions = listOf(15, 30, 50, 100) // List of selectable distances (in km)
        var distanceDropdownMenuText by remember { mutableStateOf("Select a distance (default: unlimited)") }
        if (distanceLimit != Int.MAX_VALUE) { // For setting what is displayed as text when dropdown is closed
            distanceDropdownMenuText = "${distanceLimit}km"
        }
        val dateRangePickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = startDate.time,
            initialSelectedEndDateMillis = endDate.time,
        ) // For setting what the user has already selected as the displayed date in picker

        // Flags to track current states
        var dateSelect by remember { mutableStateOf(false) } // Flag to track if user is selecting date
        var distanceSelect by remember { mutableStateOf(false) } // Flag to track if user is selecting distance
        if (distanceSelect) { // If user is selecting distance, check for permission and request if needed
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
        }
        var complete by remember { mutableStateOf(false) } // Flag to track if user is done setting filters
        var datesTriggered by remember { mutableStateOf(false) } // Flag to track if user has selected date at least once during this time opening the filters page

        Column(modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)) {
            Spacer(modifier = Modifier.size(50.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    state = searchString,
                    label = { Text("Campsite Search (Name/Location) ") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
            Spacer(modifier = Modifier.size(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Choose maximum distance for campsite: ")
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                ExposedDropdownMenuBox(
                    expanded = distanceSelect,
                    onExpandedChange = {
                        distanceSelect = !distanceSelect
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        readOnly = true,
                        value = distanceDropdownMenuText,
                        onValueChange = { },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = distanceSelect
                            )
                        },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true),
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                    )

                    ExposedDropdownMenu(
                        expanded = distanceSelect,
                        onDismissRequest = { distanceSelect = false }
                    ) {
                        distanceOptions.forEach { selection ->
                            DropdownMenuItem(
                                onClick = { // Records the chosen limit, changes the dropdown menu text (when closed), and closes menu
                                    distanceLimit = selection
                                    distanceDropdownMenuText = "${selection}km"
                                    distanceSelect = false
                                },
                                text = { Text(text = "${selection}km") }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.size(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (dateSelected || datesTriggered) { // If not first time setting date overall or not first time setting date within this filter page opening, show previously selected date
                    Text("Current selected date: ")
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                if (dateSelected || datesTriggered) {
                    val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    Text("${formatter.format(startDate)} to ${formatter.format(endDate)}")
                }
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { dateSelect = true }) {
                    Text("Click to select dates you want to stay...")
                }
                if (dateSelect) {
                    DatePickerDialog(
                        onDismissRequest = { dateSelect = false },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val difference = TimeZone.getDefault().getOffset(System.currentTimeMillis()) // Default in picker is UTC, so gets difference between local time and UTC first
                                    val start = dateRangePickerState.selectedStartDateMillis
                                    val end = dateRangePickerState.selectedEndDateMillis
                                    if (start != null) {
                                        startDate = Date(start - difference) // Sets startDate to chosen date in local time
                                    }
                                    if (end != null) {
                                        endDate = Date(end - difference) // Sets endDate to chosen date in local time
                                    }
                                    dateSelect = false
                                    dateSelected = true
                                    datesTriggered = true
                                }
                            ) { Text("Confirm") }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { dateSelect = false } // Closes menu without saving/changing anything
                            ) { Text("Cancel") }
                        }
                    ) {
                        DateRangePicker(
                            state = dateRangePickerState,
                            title = { Text(text = "Choose dates you want to stay...") },
                            showModeToggle = false,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.size(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                TextField(
                    state = guests,
                    label = { Text("Enter amount of guests") },
                    modifier = Modifier
                        .fillMaxWidth(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number) // TODO: Filter for only numbers in field
                )
            }
            Spacer(modifier = Modifier.size(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { // Resets all function variables and currentUserFilters, alongside relevant flags
                        searchString.clearText()
                        distanceLimit = Int.MAX_VALUE
                        startDate = Date()
                        endDate = Date()
                        guests.edit { replace(0, length, "1") }
                        datesTriggered = false
                        distanceDropdownMenuText = "Select a distance (default: unlimited)"
                        currentUserFilters = mutableListOf("", null, Int.MAX_VALUE, Date(), Date(), 1)
                        dateSelected = false
                    }
                ) {
                    Text("Reset filters")
                }
            }
            Spacer(modifier = Modifier
                .size(30.dp)
                .weight(1f))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = {
                    complete = true
                }) {
                    Text("Back")
                }
            }
        }
        BackHandler(enabled = true) { // Catches system back button/gesture and also applies filters
            complete = true
        }
        if (complete) { // Updates currentUserFilters whenever filter page is closed or system back is used
            currentUserFilters[0] = searchString.text.toString()
            currentUserFilters[1] = deviceLocation
            currentUserFilters[2] = distanceLimit
            currentUserFilters[3] = startDate
            currentUserFilters[4] = endDate
            if (guests.text.toString() == "") {
                currentUserFilters[5] = null
            } else {
                currentUserFilters[5] = guests.text.toString().toInt()
            }
            toParent()
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
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(text = campsite.name, fontSize = 30.sp)
            }
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)) {
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
