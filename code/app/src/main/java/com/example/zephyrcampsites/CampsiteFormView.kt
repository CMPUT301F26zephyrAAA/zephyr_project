package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
private fun FormFields(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    error: String? = null,
    singleLine: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        isError = error != null,
        supportingText = {
            if (error != null) {
                Text(error)
            }
        },
        singleLine = singleLine,
        modifier = modifier
    )
}

@Composable
fun CampsiteFormView(
    controller: CampsiteController,
    ownerId: String,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var maxOccupancy by remember { mutableStateOf("") }
    var pricePerNight by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf("") }
    var toggle by remember { mutableStateOf(true) }
    var errors by remember { mutableStateOf(CampsiteFormErrors()) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var registered by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(1) } //Step 1 (Owner Based on UI)

    if (step == 1) {
        Scaffold{ padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "+ REGISTER CAMPSITE",
                    style = MaterialTheme.typography.titleLarge
                )

                FormFields(
                    "Campsite Name:",
                    name,
                    { name = it },
                    error = errors.name
                )
                FormFields(
                    "Location:",
                    location,
                    { location = it },
                    error = errors.location
                )
                FormFields(
                    "Description (Amenities):",
                    description,
                    { description = it },
                    singleLine = false
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormFields(
                        "Max Occupancy:",
                        maxOccupancy,
                        { maxOccupancy = it },
                        modifier = Modifier.weight(1f),
                        error = errors.maxOccupancy
                    )

                    FormFields(
                        "Price / Night:",
                        pricePerNight,
                        { pricePerNight = it },
                        modifier = Modifier.weight(1f),
                        error = errors.pricePerNight
                    )
                }
                FormFields(
                    "Tags:",
                    tags,
                    { tags = it }
                )
                FormFields(
                    "Rules & Cancellation Policy:",
                    rules,
                    { rules = it },
                    singleLine = false
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Entrants must accept rules before reserving",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(checked = toggle, onCheckedChange = { toggle = it })
                }
                if (feedback != null) {
                    Text(
                        text = feedback ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = !registered,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val input = CampsiteFormInput(
                                name,
                                location,
                                description,
                                maxOccupancy,
                                pricePerNight,
                                tags,
                                rules,
                                toggle
                            )
                            errors = controller.validate(input)
                            feedback = null

                            if (!errors.hasErrors) {
                                step = 2 // Based on UI for owner (register Campsite)
                            }
                        }
                    ) {
                        Text("NEXT")
                    }
                    OutlinedButton(
                        onClick = onBack,
                        enabled = !registered,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("BACK")
                    }
                }
            }
        }
    } else {
        CampsiteLocationView(
            onPublish = { geoPoint, startDate, endDate ->
                val input = CampsiteFormInput(
                    name,
                    location,
                    description,
                    maxOccupancy,
                    pricePerNight,
                    tags,
                    rules,
                    toggle
                )
                registered = true
                feedback = null

                controller.registerCampsite(
                    ownerId = ownerId,
                    input = input,
                    geolocation = geoPoint,
                    startDate = startDate,
                    endDate = endDate,
                    onSuccess = { onDone() },
                    onFailure = {
                        registered = false
                        feedback = it
                    }
                )
            },
            onBack = { step = 1 }
        )

        if (feedback != null){
            Text(
                text = feedback ?: "",
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}