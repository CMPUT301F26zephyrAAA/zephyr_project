package com.example.zephyrcampsites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp


/**
 * Displays all registered campsites. Updates automatically when firestore updates.
 *
 * @param model Model of campsites
 * @param onCampsiteSelected When a campsite item is clicked
 * @param onBack Returns to the previous screen
 */
@Composable
fun AdminCampsiteListView(
    model: CampsiteModel,
    onCampsiteSelected: (Campsite) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        Text("Browse Campsites",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (model.isLoading) {
            Text("Loading...")
        } else if (model.error != null) {
            Text("Error: ${model.error}", color = MaterialTheme.colorScheme.error)
        }

        if(model.campsites.isEmpty()) {
            Text("No campsites registered.")
        }
        else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(
                    model.campsites,
                    key = { it.id }
                ) {
                    campsite -> AdminCampsiteItem(campsite, onClick = {onCampsiteSelected(campsite)})
                }
            }
        }
    }
}

/**
 * Displays details of a selected campsite with options to delete or go back to browse.
 *
 * @param campsite Selected Campsite
 * @param onClick Returns to the campsite browse list
 */
@Composable
fun AdminCampsiteItem(
    campsite: Campsite,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("CampsiteItem_${campsite.id}")
    ) {
        Column(modifier = Modifier
            .padding(8.dp)
            .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = if(campsite.name.isBlank()){
                    "Unnamed campsite"
                } else {
                    campsite.name
                },
                style = MaterialTheme.typography.titleMedium
            )
            Text("Location: ${campsite.location}")
            Text("OwnerID: ${campsite.ownerID}")
            Text("Max Occupancy: ${campsite.maxOccupancy}")
            Text("Price per night: ${campsite.pricePerNight}")

            val average = campsite.rating.average()

            Text(
                text = "Rating: ${
                    if(average.isNaN()) {
                        "Not Rated"
                    } else {
                        average
                    }
                }"
            )
        }
    }
}