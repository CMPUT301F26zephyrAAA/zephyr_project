
package com.example.zephyrcampsites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

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
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "BROWSE CAMPSITES",
            style = MaterialTheme.typography.headlineMedium
        )

        if (model.isLoading) {
            Text("Loading campsites...")
        } else if (model.error != null) {
            Text(
                "Error: ${model.error?.message}",
                color = MaterialTheme.colorScheme.error
            )
        } else if (model.campsites.isEmpty()) {
            Text("No campsites registered.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    model.campsites,
                    key = { it.id }
                ) { campsite ->
                    AdminCampsiteItem(
                        campsite = campsite,
                        model = model,
                        onClick = {
                            onCampsiteSelected(campsite)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("BACK")
        }
    }
}

@Composable
fun AdminCampsiteItem(
    campsite: Campsite,
    model: CampsiteModel,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CampsiteAdminImage(campsite.imagePath, model)

            Text(
                campsite.name.ifBlank { "Unnamed campsite" },
                style = MaterialTheme.typography.titleMedium
            )

            Text("Location: ${campsite.location}")
            Text("Owner: ${campsite.ownerID}")
            Text("Maximum occupancy: ${campsite.maxOccupancy}")

            val rating = campsite.averageRating
                ?: campsite.rating.takeIf { it.isNotEmpty() }
                    ?.average()

            Text(
                "Rating: ${rating?.let {
                    String.format("%.1f", it)
                } ?: "Not rated"}"
            )
        }
    }
}

@Composable
fun CampsiteAdminImage(
    path: String,
    model: CampsiteModel
) {
    var imageUrl by remember(path) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(path) {
        imageUrl = null

        if (path.isNotBlank()) {
            model.getCampsiteImage(
                path = path,
                onResult = { imageUrl = it },
                onError = { imageUrl = null }
            )
        }
    }

    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Campsite image",
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
        )
    } else {
        Text("No campsite image")
    }
}