package com.example.zephyrcampsites

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.isDigitsOnly
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberAsyncImagePainter
import coil.compose.rememberImagePainter
import coil.request.ImageRequest
import coil.request.ImageRequest.Builder
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        imageLoader: (Int) -> String,
        locationClient: FusedLocationProviderClient
    ) {
        // Acquires current filters from currentUserFilters
        val searchString : TextFieldState = rememberTextFieldState(initialText = currentUserFilters[0] as String)
        var distanceLimit by remember { mutableIntStateOf(currentUserFilters[2] as Int) }
        var startDate by remember { mutableStateOf(currentUserFilters[3] as Date) }
        var endDate by remember { mutableStateOf(currentUserFilters[4] as Date) }
        var guests by remember { mutableIntStateOf(currentUserFilters[5] as Int) }

        var filterOpen by remember {mutableStateOf(false)}

        currentUserFilters[0] = searchString.text // For syncing currentUserFilters when user enters search string on summary page

        Column(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)) {
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
                    filterOpen = !filterOpen }) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Filters")
                }
            }
            if (filterOpen) {
                FilterSettings(
                    locationClient = locationClient,
                    toParent = { filterOpen = false },
                    clearFilter = {
                        searchString.clearText()
                        currentUserFilters[0] = ""
                    })
            }
            Spacer(modifier = Modifier.size(30.dp))
            val displayList = searchHandler(currentUserFilters[1] as Location?, distanceLimit, startDate, endDate, guests, searchString.text.toString()) // Search function can handle any nulls
            LazyColumn(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                itemsIndexed(displayList) { _ , campsite ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CampsiteRow(item = campsite, onClick = { id -> openDetail(id) }, imageGrabber = imageLoader)
                    }
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
        toParent : () -> Unit,
        clearFilter: () -> Unit
    ) {
        // For grabbing user location
        var deviceLocation by remember {mutableStateOf(currentUserFilters[1] as Location?)}
        val permissionLauncher = rememberLauncherForActivityResult( // Launcher to request location permissions
            ActivityResultContracts.RequestMultiplePermissions()
        ) { isGranted ->
            val fineLocationCheck = isGranted[Manifest.permission.ACCESS_FINE_LOCATION]
            val coarseLocationCheck = isGranted[Manifest.permission.ACCESS_COARSE_LOCATION]
            if (fineLocationCheck == true || coarseLocationCheck == true) {
                locationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        deviceLocation = location
                        currentUserFilters[1] = deviceLocation
                    } else {
                        locationClient.lastLocation.addOnSuccessListener { location -> deviceLocation = location }
                    }
                }
            } // If no permission, location stays null
        }

        // Acquires current filters from currentUserFilters
//        val searchString : TextFieldState = rememberTextFieldState(initialText = currentUserFilters[0] as String)
//        var distanceLimit by remember { mutableIntStateOf(currentUserFilters[2] as Int) }
        var startDate by remember { mutableStateOf(currentUserFilters[3] as Date) }
        var endDate by remember { mutableStateOf(currentUserFilters[4] as Date) }
        val guests : TextFieldState = rememberTextFieldState(initialText = currentUserFilters[5].toString())

        // Configurations for filter options
        val distanceOptions = listOf(15, 30, 50, 100) // List of selectable distances (in km)
        var distanceDropdownMenuText by remember { mutableStateOf("Select a distance (default: unlimited)") }
        if (currentUserFilters[2] != Int.MAX_VALUE) { // For setting what is displayed as text when dropdown is closed
            distanceDropdownMenuText = "${currentUserFilters[2]}km"
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
//        var complete by remember { mutableStateOf(false) } // Flag to track if user is done setting filters
        var datesTriggered by remember { mutableStateOf(false) } // Flag to track if user has selected date at least once during this time opening the filters page
        var alertTriggered by remember { mutableStateOf(false) } // Flag to track if an alert is being triggered (for invalid input)

        Card(modifier = Modifier) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Choose maximum distance for campsite: ",
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                            modifier = Modifier.menuAnchor(
                                type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                enabled = true
                            ),
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        )

                        ExposedDropdownMenu(
                            expanded = distanceSelect,
                            onDismissRequest = { distanceSelect = false }
                        ) {
                            distanceOptions.forEach { selection ->
                                DropdownMenuItem(
                                    onClick = { // Records the chosen limit, changes the dropdown menu text (when closed), and closes menu
                                        currentUserFilters[2] = selection
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
                        Text(
                            text = "Current selected date: ",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    if (dateSelected || datesTriggered) {
                        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        Text(
                            text = "${formatter.format(currentUserFilters[3])} to ${formatter.format(currentUserFilters[4])}",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { dateSelect = true }) {
                        Text(text = "Click to select dates you want to stay...")
                    }
                    if (dateSelect) {
                        DatePickerDialog(
                            onDismissRequest = { dateSelect = false },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val difference = TimeZone.getDefault()
                                            .getOffset(System.currentTimeMillis()) // Default in picker is UTC, so gets difference between local time and UTC first
                                        val start = dateRangePickerState.selectedStartDateMillis
                                        val end = dateRangePickerState.selectedEndDateMillis
                                        if (start != null) {
                                            currentUserFilters[3] =
                                                Date(start - difference) // Sets startDate to chosen date in local time
                                        }
                                        if (end != null) {
                                            currentUserFilters[4] =
                                                Date(end - difference) // Sets endDate to chosen date in local time
                                        }
                                        dateSelect = false
                                        dateSelected = true
                                        datesTriggered = true
                                    }
                                ) { Text("Confirm") }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        dateSelect = false
                                    } // Closes menu without saving/changing anything
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
                        label = { Text("Enter amount of guests (max: 10)") },
                        modifier = Modifier
                            .fillMaxWidth(),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        inputTransformation = DigitOnlyInputTransformation()
                    )
                }
                if (guests.text.toString() == "") {
                    currentUserFilters[5] = null
                } else if (guests.text.toString().toInt() > 10) { // Triggers error dialog (by setting flag) if anything entered is not allowed
                        alertTriggered = true
                } else {
                    currentUserFilters[5] = guests.text.toString().toInt()
                }
                Spacer(modifier = Modifier.size(20.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { // Resets all function variables and currentUserFilters, alongside relevant flags
                            currentUserFilters[2] = Int.MAX_VALUE
                            startDate = Date()
                            endDate = Date()
                            guests.edit { replace(0, length, "1") }
                            datesTriggered = false
                            distanceDropdownMenuText = "Select a distance (default: unlimited)"
                            currentUserFilters =
                                mutableListOf("", null, Int.MAX_VALUE, Date(), Date(), 1)
                            dateSelected = false
                        }
                    ) {
                        Text("Reset filters")
                    }
                }
            }
        }
        if (alertTriggered) {
            ErrorDialog(title = "Max guests exceeded!",
                text = "You have entered a guest amount more than the maximum allowed (10). Please try again.",
                dismiss = { alertTriggered = false })
        }
//        BackHandler(enabled = true) { // Catches system back button/gesture and also applies filters
//            complete = true
//        }
//        if (complete) { // Updates currentUserFilters whenever filter page is closed or system back is used
//            if (guests.text.toString().toInt() > 10) { // Triggers error dialog (by setting flag) if anything entered is not allowed
//                alertTriggered = true
//                complete = false
//            } else {
//                currentUserFilters[0] = searchString.text.toString()
//                currentUserFilters[1] = deviceLocation
//                currentUserFilters[2] = distanceLimit
//                currentUserFilters[3] = startDate
//                currentUserFilters[4] = endDate
//                if (guests.text.toString() == "") {
//                    currentUserFilters[5] = null
//                } else {
//                    currentUserFilters[5] = guests.text.toString().toInt()
//                }
//                toParent()
//            }
//        }
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
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var codeShow by remember {mutableStateOf(false)}
        val code = codeGenerator("campsite", campsite!!.id.toInt())
        val launcher = rememberLauncherForActivityResult( // Launcher to save QR code (Opens destination picker)
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val image = code.asAndroidBitmap()
            val data = result.data
            val uri = data?.data
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.let { outputStream ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            image.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            outputStream.flush()
                            outputStream.close()
                        }
                    }
                }
            }
        }
        Column(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp)) {
            Spacer(modifier = Modifier.size(30.dp))
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(text = campsite.name, fontSize = 30.sp, color = MaterialTheme.colorScheme.onBackground)
            }
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(text = campsite.maxOccupancy.toString(), fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                if (codeShow) {
                    Image(
                        bitmap = code,
                        contentDescription = "Generated QR code of user"
                    )
                }
            }
            Spacer(modifier = Modifier.size(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { codeShow = !codeShow }) {
                    Text(text = "Generate/Hide QR")
                }
                Spacer(modifier = Modifier.weight(1f))
                if (codeShow) {
                    Button(onClick = {
                        launcher.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "image/png"
                            putExtra(Intent.EXTRA_TITLE, "campsite-qr.png")
                        }) }) {
                        Text(text = "Download/Save QR")
                    }
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
    @OptIn(ExperimentalCoilApi::class) // For image loader
    @Composable
    private fun CampsiteRow(item: Campsite, onClick: (Int) -> Unit, imageGrabber: (Int) -> String) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 40.dp, vertical = 16.dp)
                .clickable { onClick(item.id.toInt()) },
            elevation = CardDefaults.cardElevation(
                defaultElevation = 5.dp
            )
        ) {
            // For loading campsite main image
            // TODO: set up cloud firebase storage. do not use rn
            if (item.imagePath != "") {
                Row() {
                    Image(
                        painter = rememberAsyncImagePainter(
                            Builder(LocalContext.current).data(
                            data = imageGrabber(item.id.toInt())
                        ).apply(block = { -> crossfade(false) }).build()
                        ),
                        contentDescription = "Campsite ${item.id} main image",
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Row() {
                Column(modifier = Modifier.fillMaxSize().padding(vertical = 20.dp, horizontal = 15.dp)) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = item.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.size(5.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "\uD83D\uDCCD ${item.location}",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.size(20.dp))
                    val ratingText : String = if (item.rating == mutableListOf<Int>()) {
                        "⭐ Rating: N/A"
                    } else {
                        "⭐ Rating: ${"%.2f".format(item.rating.average())}"
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "\uD83D\uDC65 Max Occupancy: ${item.maxOccupancy}",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = ratingText,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    Spacer(modifier = Modifier.size(30.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Button(onClick = { onClick(item.id.toInt()) }) {
                            Text("View Details")
                        }
                    }
                }
            }
        }
    }

    /**
     * Private helper function to generate error dialogs.
     *
     * @param title Title of the dialog
     * @param text Main body text of the dialog
     * @param dismiss Method that responds when dialog is dismissed
     */
    @Composable
    private fun ErrorDialog(title : String, text: String, dismiss: () -> Unit) {
        AlertDialog(
            icon = { Icon(Icons.Default.Error, contentDescription = "Error Icon") },
            title = { Text(text = title) },
            text = { Text(text = text) },
            onDismissRequest = { dismiss() },
            confirmButton = { },
            dismissButton = { TextButton(
                onClick = { dismiss() }
            ) {
                Text("Dismiss")
            } }
        )
    }
}

/**
 * Helper class for transforming inputs to digits only.
 * Use in TextField composables by setting parameter "inputtransformation" to an instance of this class.
 */
class DigitOnlyInputTransformation : InputTransformation {
    override fun TextFieldBuffer.transformInput() {
        if (!asCharSequence().isDigitsOnly()) {
            revertAllChanges()
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
