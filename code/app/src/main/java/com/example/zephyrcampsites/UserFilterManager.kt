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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.os.ConfigurationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import java.text.SimpleDateFormat
import java.util.Date

/**
 * Class for managing/editing user filters. Can be instantiated alone to access the FilterSetter method.
 *
 * @property dateSelected Tracks if user has ever selected a date across different instances of opening filter settings. Used to display selected date.
 */
class UserFilterManager {
    var dateSelected = false
    /**
     * Displays the settings page for filters to search campsites.
     * @OptIn is used for the dropdown menu for location (as it seems to be a new thing?)
     * @SuppressLint is used for shushing IDE about missing permissions, checks are implemented already
     *
     * @param locationClient Location provider client, used to get device location. Has to be instantiated inside MainActivity's onCreate method, therefore it is passed in.
     * @param clearFilter Method to respond when filter is cleared
     * @param onError Displays a validation error and its dismissal action.
     * @param currentUserFilters Filter values retained when returning from details.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("MissingPermission")
    @Composable
    fun FilterSetter(
        locationClient: FusedLocationProviderClient,
        clearFilter: () -> Unit,
        onError: @Composable ((String, String, () -> Unit) -> Unit),
        currentUserFilters: UserFilter
    ) {
        // Helpers for permissions and timezone converts
        val permissionLauncher = PermissionHandlers().locationHandler(locationClient, { location -> currentUserFilters.deviceLocation = location })
        val helpers = LocalConverter()

        // Acquires current filters from currentUserFilters
        var startDate by remember { mutableStateOf(currentUserFilters.startDate as Date) }
        var endDate by remember { mutableStateOf(currentUserFilters.endDate as Date) }
        val guests: TextFieldState = rememberTextFieldState(initialText = currentUserFilters.guests?.toString().orEmpty())

        // Configurations for filter options
        val distanceOptions = listOf(15, 30, 50, 100) // List of selectable distances (in km)
        var distanceDropdownMenuText by remember { mutableStateOf("Select distance (default: unlimited)") }
        if (currentUserFilters.distanceLimit != Int.MAX_VALUE) { // For setting what is displayed as text when dropdown is closed
            distanceDropdownMenuText = "${currentUserFilters.distanceLimit}km"
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

        val userLocale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] // Gets locale in a composable way, allows update if changed while app is open. Ref: https://stackoverflow.com/a/70617283

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
                            ).testTag("DistanceLimitMenu"),
                            colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        )

                        ExposedDropdownMenu(
                            expanded = distanceSelect,
                            onDismissRequest = { distanceSelect = false }
                        ) {
                            distanceOptions.forEach { selection ->
                                DropdownMenuItem(
                                    onClick = { // Records the chosen limit, changes the dropdown menu text (when closed), and closes menu
                                        currentUserFilters.distanceLimit = selection
                                        distanceDropdownMenuText = "${selection}km"
                                        distanceSelect = false
                                    },
                                    text = { Text(text = "${selection}km") },
                                    modifier = Modifier.testTag("DistanceOption${selection}")
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
                        val formatter = SimpleDateFormat("MMM dd, yyyy", userLocale)
                        Text(
                            text = "${formatter.format(currentUserFilters.startDate as Date)} to ${formatter.format(currentUserFilters.endDate as Date)}",
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { dateSelect = true }, modifier = Modifier.testTag("DateSelectButton")) {
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
                                        if (start != null) {
                                            currentUserFilters.startDate =
                                                Date(helpers.timezoneConvert(start)) // Sets startDate to chosen date in local time
                                        }
                                        if (end != null) {
                                            currentUserFilters.endDate =
                                                Date(helpers.timezoneConvert(end)) // Sets endDate to chosen date in local time
                                        }
                                        dateSelect = false
                                        dateSelected = true
                                        datesTriggered = true
                                    }, modifier = Modifier.testTag("DateSelectConfirm")
                                ) { Text("Confirm") }
                            },
                            dismissButton = {
                                TextButton(
                                    onClick = {
                                        dateSelect = false
                                    }, // Closes menu without saving/changing anything
                                    modifier = Modifier.testTag("DateSelectDismiss")
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
                            .fillMaxWidth().testTag("GuestsEntry"),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        inputTransformation = DigitOnlyInputTransformation(),
                        onKeyboardAction = { performDefaultAction ->
                            focusManager.clearFocus()
                            if ((guests.text.toString().toIntOrNull() ?: 0) > 10) { // Checks the guest limit without failing on an empty field.
                                alertTriggered = true
                            }
                            performDefaultAction()
                        }
                    )
                }
                val guestText = guests.text.toString()
                val guestCount = guestText.toIntOrNull()
                if (guestText.isNotEmpty() && (guestCount == null || guestCount > 10)) { // Triggers the guest-limit dialog.
                    alertTriggered = true
                }
                currentUserFilters.guests = guestCount
                Spacer(modifier = Modifier.size(20.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = { // Resets all function variables and currentUserFilters, alongside relevant flags
                            startDate = Date()
                            endDate = Date()
                            guests.edit { replace(0, length, "1") }
                            datesTriggered = false
                            distanceDropdownMenuText = "Select distance (default: unlimited)"
                            currentUserFilters.reset()
                            dateSelected = false
                            clearFilter()
                        }, modifier = Modifier.testTag("ResetFilter")
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
