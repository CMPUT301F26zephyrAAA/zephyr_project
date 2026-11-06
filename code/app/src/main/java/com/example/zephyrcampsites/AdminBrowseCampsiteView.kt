package com.example.zephyrcampsites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.ui.platform.testTag

/** Displays the authorized admin campsite list and forwards selection and Back actions. */
@Composable
fun AdminCampsiteListView(
    account: Account?,
    campsites: List<Campsite>,
    isLoading: Boolean,
    errorMessage: String?,
    loadImage: (String, (String?) -> Unit) -> Unit,
    onCampsiteSelected: (Campsite) -> Unit,
    onBack: () -> Unit
) {
    if (account?.userType != "admin" || account.id.isBlank()) {
        Column {
            Text("Administrator access required.")
            OutlinedButton(onClick = onBack) { Text("BACK") }
        }
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "BROWSE CAMPSITES",
            style = MaterialTheme.typography.headlineMedium,
        )

        if (isLoading) {
            Text("Loading campsites...")
        } else if (errorMessage != null) {
            Text(
                "Error: ${errorMessage}",
                color = MaterialTheme.colorScheme.error,
            )
        } else if (campsites.isEmpty()) {
            Text("No campsites registered.")
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    campsites,
                    key = { it.id },
                ) { campsite ->
                    AdminCampsiteItem(
                        campsite = campsite,
                        loadImage = loadImage,
                        onClick = {
                            onCampsiteSelected(campsite)
                        },
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f, fill = false))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("BACK")
        }
    }
}

@Composable
fun AdminCampsiteItem(
    campsite: Campsite,
    loadImage: (String, (String?) -> Unit) -> Unit,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("AdminCampsite_${campsite.id}"),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CampsiteAdminImage(campsite.imagePath, loadImage)

            Text(
                campsite.name.ifBlank { "Unnamed campsite" },
                style = MaterialTheme.typography.titleMedium,
            )

            Text("Location: ${campsite.location}")
            Text("Owner: ${campsite.ownerID}")
            Text("Maximum occupancy: ${campsite.maxOccupancy}")

            val rating =
                campsite.averageRating ?: campsite.rating.takeIf { it.isNotEmpty() }?.average()

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
    loadImage: (String, (String?) -> Unit) -> Unit,
) {
    var imageUrl by
        remember(path) {
            mutableStateOf<String?>(null)
        }

    LaunchedEffect(path) {
        imageUrl = null

        if (path.isNotBlank()) {
            loadImage(path) { imageUrl = it }
        }
    }

    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Campsite image",
            modifier = Modifier.fillMaxWidth().height(150.dp),
        )
    } else {
        Text("No campsite image")
    }
}