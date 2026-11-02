package com.example.zephyrcampsites

import android.Manifest
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.FusedLocationProviderClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UserFilterManager {
    var dateSelected = false
    /**
     * Displays the settings page for filters to search campsites.
     * @OptIn is used for the dropdown menu for location (as it seems to be a new thing?)
     * @SuppressLint is used for shushing IDE about missing permissions, checks are implemented already
     *
     * @param locationClient Location provider client, used to get device location. Has to be instantiated inside MainActivity's onCreate method, therefore it is passed in.
     * @param clearFilter Method to respond when filter is cleared
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("MissingPermission")
    @Composable
    fun FilterSetter(
        locationClient: FusedLocationProviderClient,
        clearFilter: () -> Unit,
        onError: @Composable (String, String, () -> Unit) -> Unit,
        currentUserFilters: MutableList<Any?>
    ) {
        // Helpers for permissions and timezone converts
        val permissionLauncher = PermissionHandlers().locationHandler(locationClient, { location -> currentUserFilters[1] = location })
        val helpers = LocalConverter()

        // Acquires current filters from currentUserFilters
        var startDate by remember { mutableStateOf(currentUserFilters[3] as Date) }
        var endDate by remember { mutableStateOf(currentUserFilters[4] as Date) }
        val guests : TextFieldState = rememberTextFieldState(initialText = currentUserFilters[5].toString())

        // Configurations for filter options
        val distanceOptions = listOf(15, 30, 50, 100) // List of selectable distances (in km)
        var distanceDropdownMenuText by remember { mutableStateOf("Select distance (default: unlimited)") }
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
        var datesTriggered by remember { mutableStateOf(false) } // Flag to track if user has selected date at least once during this time opening the filters page
        var alertTriggered by remember { mutableStateOf(false) } // Flag to track if an alert is being triggered (for invalid input)

        val focusManager = LocalFocusManager.current

        Card(modifier = Modifier
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
            .padding(horizontal = 10.dp)) {
            Column(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.tertiary)
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
                                        val start = dateRangePickerState.selectedStartDateMillis
                                        val end = dateRangePickerState.selectedEndDateMillis
                                        println("filter date select")
                                        if (start != null) {
                                            currentUserFilters[3] =
                                                Date(helpers.timezoneConvert(start)) // Sets startDate to chosen date in local time
                                            println(currentUserFilters[3])
                                        }
                                        if (end != null) {
                                            currentUserFilters[4] =
                                                Date(helpers.timezoneConvert(end)) // Sets endDate to chosen date in local time
                                            println(currentUserFilters[4])
                                        }
                                        println("filter date select end")
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
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        inputTransformation = DigitOnlyInputTransformation(),
                        onKeyboardAction = { performDefaultAction ->
                            focusManager.clearFocus()
                            if (guests.text.toString().toInt() > 10) { // Triggers error dialog (by setting flag) if anything entered is not allowed
                                alertTriggered = true
                            }
                            performDefaultAction()
                        }
                    )
                }
                if (guests.text.toString() == "") {
                    currentUserFilters[5] = null
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
                            distanceDropdownMenuText = "Select distance (default: unlimited)"
                            currentUserFilters.clear()
                            currentUserFilters.addAll(listOf("", null, Int.MAX_VALUE, Date(), Date(), 1))
                            dateSelected = false
                            clearFilter()
                        }
                    ) {
                        Text("Reset filters")
                    }
                }
            }
        }
        if (alertTriggered) {
            onError("Max guests exceeded!",
                "You have entered a guest amount more than the maximum allowed (10). Please try again.",
                {
                    alertTriggered = false
                    guests.edit { replace(0, length, "10") }
                })
        }
    }
}