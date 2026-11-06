package com.example.zephyrcampsites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.storage.storage

// One entry in the admin image browser.
data class AdminImage(
    val ownerId: String,
    val ownerName: String,
    val path: String,
    val type: String
)

/**
 * Displays all images. (For Admin purposes)
 *
 * @param accountsModel Model for accounts.
 * @param campsiteModel Model for campsites.
 * @param onBack Returns to the previous screen
 */
@Composable
fun AdminBrowseImageView(
    accountsModel: AccountsModel,
    campsiteModel: CampsiteModel,
    onBack: () -> Unit,
) {
    val db = remember { Firebase.firestore }

    var images by remember { mutableStateOf<List<AdminImage>>(emptyList()) }
    var selectedImage by remember { mutableStateOf<AdminImage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    // Observe image references from both collections.
    DisposableEffect(db) {
        var accountImages = emptyList<AdminImage>()
        var campsiteImages = emptyList<AdminImage>()
        var accountsLoaded = false
        var campsitesLoaded = false

        fun refresh() {
            images = accountImages + campsiteImages
            loading = !(accountsLoaded && campsitesLoaded)
        }

        val accountListener = db.collection("accounts")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    error = exception.message
                    accountsLoaded = true
                    refresh()
                    return@addSnapshotListener
                }

                accountImages = snapshot?.documents.orEmpty()
                    .mapNotNull { doc ->
                        val path = doc.getString("imagePath")
                        if (path.isNullOrBlank()) null
                        else AdminImage(
                            ownerId = doc.id,
                            ownerName = doc.getString("name")
                                ?: "Unnamed Profile",
                            path = path,
                            type = "profile"
                        )
                    }

                accountsLoaded = true
                refresh()
            }

        val campsiteListener = db.collection("campsite")
            .addSnapshotListener { snapshot, exception ->
                if (exception != null) {
                    error = exception.message
                    campsitesLoaded = true
                    refresh()
                    return@addSnapshotListener
                }

                campsiteImages = snapshot?.documents.orEmpty()
                    .flatMap { doc ->
                        val result = mutableListOf<AdminImage>()
                        val name = doc.getString("name")
                            ?: "Unnamed Campsite"

                        val primary = doc.getString("imagePath")
                        if (!primary.isNullOrBlank()) {
                            result.add(
                                AdminImage(doc.id, name, primary, "campsite")
                            )
                        }

                        val promotional =
                            doc.get("promoImagePaths") as? List<*>

                        promotional.orEmpty()
                            .filterIsInstance<String>()
                            .filter { it.isNotBlank() }
                            .forEach { path ->
                                result.add(
                                    AdminImage(
                                        doc.id, name, path, "promotional"
                                    )
                                )
                            }

                        result
                    }

                campsitesLoaded = true
                refresh()
            }

        onDispose {
            accountListener.remove()
            campsiteListener.remove()
        }
    }

    val selected = selectedImage

    if (selected == null) {
        // IMAGE BROWSE PAGE
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "BROWSE IMAGES (ADMIN)",
                style = MaterialTheme.typography.headlineMedium
            )

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            when {
                loading -> CircularProgressIndicator()

                images.isEmpty() -> Text("No images found.")

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(images, key = {
                        "${it.type}:${it.ownerId}:${it.path}"
                    }) { image ->
                        OutlinedButton(
                            onClick = { selectedImage = image },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                StorageImage(
                                    path = image.path,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp)
                                )

                                Text(image.ownerName)
                                Text("Type: ${image.type}")
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BACK")
            }
        }
    } else {
        // SELECTED IMAGE PAGE
        var confirmDelete by remember(selected) {
            mutableStateOf(false)
        }
        var deleting by remember(selected) {
            mutableStateOf(false)
        }
        var deleteError by remember(selected) {
            mutableStateOf<String?>(null)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "IMAGE DETAILS",
                style = MaterialTheme.typography.headlineMedium
            )

            StorageImage(
                path = selected.path,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )

            Text("Owner: ${selected.ownerName}")
            Text("Type: ${selected.type}")

            deleteError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = { confirmDelete = true },
                enabled = !deleting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (deleting) "Deleting..." else "DELETE IMAGE")
            }

            OutlinedButton(
                onClick = { selectedImage = null },
                enabled = !deleting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("BACK TO BROWSE")
            }
        }

        if (confirmDelete) {
            AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete Image?") },
                text = {
                    Text("This image will be permanently removed.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDelete = false
                            deleting = true

                            val success = {
                                deleting = false
                                selectedImage = null
                            }

                            val failure: (Exception) -> Unit = {
                                deleting = false
                                deleteError = it.message
                            }

                            when (selected.type) {
                                "profile" -> {
                                    val account = Account(selected.ownerId)
                                    account.imagePath = selected.path

                                    accountsModel.removeProfileImage(
                                        account, success, failure
                                    )
                                }

                                "campsite", "promotional" -> {
                                    val campsite = campsiteModel.campsites
                                        .find { it.id == selected.ownerId }

                                    if (campsite == null) {
                                        failure(
                                            IllegalStateException(
                                                "Campsite not found."
                                            )
                                        )
                                    } else if (selected.type == "campsite") {
                                        campsiteModel.removeCampsiteImage(
                                            campsite, success, failure
                                        )
                                    } else {
                                        campsiteModel.removePromotionalImage(
                                            campsite,
                                            selected.path,
                                            success,
                                            failure
                                        )
                                    }
                                }
                            }
                        }
                    ) {
                        Text("DELETE")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { confirmDelete = false }
                    ) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}

/**
 * Displays a Firebase Storage image.
 * A missing image displays a text fallback.
 */
@Composable
fun StorageImage(
    path: String,
    modifier: Modifier = Modifier
) {
    var url by remember(path) { mutableStateOf<String?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    DisposableEffect(path) {
        var active = true

        val reference = try {
            if (path.startsWith("gs://") ||
                path.startsWith("https://")) {
                Firebase.storage.getReferenceFromUrl(path)
            } else {
                Firebase.storage.reference.child(path)
            }
        } catch (e: IllegalArgumentException) {
            failed = true
            null
        }

        reference?.downloadUrl
            ?.addOnSuccessListener {
                if (active) url = it.toString()
            }
            ?.addOnFailureListener {
                if (active) failed = true
            }

        onDispose { active = false }
    }

    when {
        url != null -> AsyncImage(
            model = url,
            contentDescription = "Stored image",
            modifier = modifier,
            contentScale = ContentScale.Fit,
            onError = { failed = true }
        )

        failed -> Text("Image unavailable")

        else -> CircularProgressIndicator()
    }
}