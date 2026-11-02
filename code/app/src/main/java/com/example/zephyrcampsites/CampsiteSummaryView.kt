package com.example.zephyrcampsites

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.isDigitsOnly
import coil.annotation.ExperimentalCoilApi
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest.Builder
import com.google.android.gms.location.FusedLocationProviderClient
import java.util.Date

/**
 * View class for campsites.
 *
 * Should have all Composable functions related to campsites.
 */
class CampsiteSummaryView : ViewObserver<CampsiteModel> {
    var currentUserFilters = UserFilter("", null, Int.MAX_VALUE, Date(), Date(), 1) // Holds user filters that they have set, in (Search string, device location, distance limit, start date, end date, # of guests)

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
        searchHandler: (UserFilter) -> List<Campsite>,
        filterPage: () -> Unit,
        imageLoader: (Int) -> String,
        locationClient: FusedLocationProviderClient
    ) {
        val searchString : TextFieldState = rememberTextFieldState(initialText = currentUserFilters.searchString) // Creates text field state based on existing search query

        var filterOpen by remember {mutableStateOf(false)} // For tracking if the filter dialog is open

        currentUserFilters.searchString = searchString.text as String // For syncing currentUserFilters when user enters search string on summary page

        val focusManager = LocalFocusManager.current

        var errorState by remember { mutableStateOf(false) }

        Column(modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
        ) {
            Spacer(modifier = Modifier.size(50.dp))
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                TextField(
                    state = searchString,
                    label = { Text("Campsite Search (Name/Location) ") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    onKeyboardAction = { performDefaultAction ->
                        focusManager.clearFocus()
                        performDefaultAction()
                    }
                )
                TextButton(onClick = {
                    println("filter clicked")
                    filterOpen = !filterOpen }) {
                    Icon(Icons.Filled.FilterList, contentDescription = "Filters")
                }
            }
            if (filterOpen) {
                UserFilterManager().FilterSetter(
                    locationClient = locationClient,
                    clearFilter = {
                        searchString.clearText()
                        currentUserFilters.searchString = ""
                    },
                    currentUserFilters = currentUserFilters,
                    onError = { title, text, dismiss -> ErrorDialog(title = title, text = text, dismiss = dismiss) })
            }
            Spacer(modifier = Modifier.size(30.dp))
            var displayList: List<Campsite> = listOf()
            try {
                displayList = searchHandler(currentUserFilters)
            } catch (_: java.lang.IllegalStateException) {
                errorState = true
            }
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
        if (errorState) {
            ErrorDialog(
                title = "Failed to load campsites!",
                text = "Fail to acquire list of campsites. Please check your connection and try again later.",
                dismiss = {
                    println("dismissed")
                    errorState = false
                }
            )
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
                        ).apply(block = { crossfade(false) }).build()
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
