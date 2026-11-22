package com.example.zephyrcampsites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Holds the selected parent record and image path used by administrative image removal. */
data class AdminImage(
    val ownerId: String,
    val ownerName: String,
    val path: String,
    val type: String,
    val isCompressed: Boolean = false,
    val imageIndex: Int = -1,
)

/** Observes profile and campsite image references for the authorized administrator. */
@Composable
fun AdminBrowseImageView(
    account: Account?,
    accountsModel: AccountsModel,
    campsiteModel: CampsiteModel,
    onRemove: (AdminImage, () -> Unit, (Exception) -> Unit) -> Unit,
    onBack: () -> Unit,
    loadImage: (String, (String?) -> Unit) -> Unit,
) {
    if (account?.userType != "admin" || account.id.isBlank()) {
        Text("Administrator access required.")
        return
    }
    var profiles by remember(accountsModel) { mutableStateOf<List<Account>>(emptyList()) }
    var profilesLoading by remember(accountsModel) { mutableStateOf(true) }
    var profileError by remember(accountsModel) { mutableStateOf<String?>(null) }
    DisposableEffect(accountsModel) {
        val listener =
            accountsModel.observeAccounts(
                {
                    profiles = it
                    profilesLoading = false
                    profileError = null
                },
                {
                    profilesLoading = false
                    profileError = it.message ?: "Unable to load profile images."
                },
            )
        onDispose { listener.remove() }
    }
    val profileImages =
        profiles
            .filter { it.imagePath.isNotBlank() }
            .map {
                AdminImage(it.id, it.name.ifBlank { "Unnamed Profile" }, it.imagePath, "profile")
            }

    val campsiteImages =
        campsiteModel.campsites.flatMap { campsite ->

            val legacyImages =
                (listOf(campsite.imagePath) + campsite.promoImagePaths)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .map { path ->
                        AdminImage(
                            ownerId = campsite.id,
                            ownerName = campsite.name.ifBlank { "Unnamed Campsite" },
                            path = path,
                            type = "campsite",
                            isCompressed = false
                        )
                    }

            val compressedImages =
                campsite.compressedImages
                    .mapIndexedNotNull { index, encodedImage ->
                        if (encodedImage.isBlank()) {
                            null
                        } else {
                            AdminImage(
                                ownerId = campsite.id,
                                ownerName = campsite.name.ifBlank { "Unnamed Campsite" },
                                path = encodedImage,
                                type = "campsite",
                                isCompressed = true,
                                imageIndex = index
                            )
                        }
                    }

            legacyImages + compressedImages
        }

    AdminImageBrowser(
        account,
        profileImages + campsiteImages,
        profilesLoading || campsiteModel.isLoading,
        profileError ?: campsiteModel.error?.message,
        onRemove,
        onBack,
        loadImage,
    )
}

/**
 * Displays the image list, selected image, confirmation, and removal feedback without accessing
 * Firebase.
 */
@Composable
fun AdminImageBrowser(
    account: Account?,
    images: List<AdminImage>,
    loading: Boolean,
    error: String?,
    onRemove: (AdminImage, () -> Unit, (Exception) -> Unit) -> Unit,
    onBack: () -> Unit,
    loadImage: (String, (String?) -> Unit) -> Unit,
) {
    if (account?.userType != "admin" || account.id.isBlank()) {
        Text("Administrator access required.")
        return
    }
    var selected by remember { mutableStateOf<AdminImage?>(null) }
    var confirm by remember(selected) { mutableStateOf(false) }
    var deleting by remember(selected) { mutableStateOf(false) }
    var removalError by remember(selected) { mutableStateOf<String?>(null) }
    BackHandler(enabled = selected != null) {
        if (!deleting) {
            selected = null
        }
    }
    val image = selected
    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (image == null) {
                "BROWSE IMAGES (ADMIN)"
            } else {
                "IMAGE DETAILS"
            },
            style = MaterialTheme.typography.headlineMedium,
        )
        if (image == null) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            when {
                loading -> CircularProgressIndicator()
                error != null -> Unit
                images.isEmpty() -> Text("No images found.")
                else ->
                    LazyColumn(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(
                            images,
                            key = {
                                if (it.isCompressed) {
                                    "${it.type}:${it.ownerId}:compressed:${it.imageIndex}"
                                } else {
                                    "${it.type}:${it.ownerId}:${it.path}"
                                }
                            }
                        ) { item ->
                            OutlinedButton(
                                onClick = { selected = item },
                                modifier =
                                    Modifier.fillMaxWidth()
                                        .testTag("AdminImage_${item.ownerId}_${if (item.isCompressed) item.imageIndex else item.path}"),
                            ) {
                                Column {
                                    if (item.isCompressed) {
                                        AdminCompressedImage(
                                            encodedImage = item.path,
                                            modifier = Modifier.fillMaxWidth().height(150.dp)
                                        )
                                    } else {
                                        StorageImage(
                                            item.path,
                                            loadImage,
                                            Modifier.fillMaxWidth().height(150.dp)
                                        )
                                    }
                                    Text(item.ownerName)
                                    Text("Type: ${item.type}")
                                }
                            }
                        }
                    }
            }
            OutlinedButton(onClick = onBack) { Text("BACK") }
        } else {
            if (image.isCompressed) {
                AdminCompressedImage(
                    encodedImage = image.path,
                    modifier = Modifier.fillMaxWidth().height(250.dp)
                )
            } else {
                StorageImage(
                    image.path,
                    loadImage,
                    Modifier.fillMaxWidth().height(250.dp)
                )
            }
            Text("Owner: ${image.ownerName}")
            Text("Type: ${image.type}")
            removalError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { confirm = true },
                enabled = !deleting,
                modifier = Modifier.testTag("RemoveImage"),
            ) {
                Text(
                    if (deleting) {
                        "Removing..."
                    } else {
                        "DELETE IMAGE"
                    }
                )
            }
            OutlinedButton(onClick = { selected = null }, enabled = !deleting) {
                Text("BACK TO BROWSE")
            }
        }
    }
    if (confirm && image != null) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete image?") },
            text = {
                Text("This image will be permanently removed. The profile or campsite will remain.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirm = false
                        deleting = true
                        removalError = null
                        onRemove(
                            image,
                            {
                                deleting = false
                                selected = null
                            },
                            {
                                deleting = false
                                removalError = it.message ?: "Unable to remove image."
                            },
                        )
                    },
                    modifier = Modifier.testTag("ConfirmRemoveImage"),
                ) {
                    Text("DELETE")
                }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("CANCEL") } },
        )
    }
}

/** Displays a resolved image, loading indicator, or missing-image fallback. */
@Composable
fun StorageImage(
    path: String,
    loadImage: (String, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by remember(path) { mutableStateOf<String?>(null) }
    var loading by remember(path) { mutableStateOf(path.isNotBlank()) }
    DisposableEffect(path) {
        var active = true
        if (path.isNotBlank()) {
            loadImage(path) {
                if (active) {
                    url = it
                    loading = false
                }
            }
        }
        onDispose { active = false }
    }
    when {
        url != null ->
            AsyncImage(
                model = url,
                contentDescription = "Stored image",
                modifier = modifier,
                contentScale = ContentScale.Fit,
                onError = {
                    url = null
                    loading = false
                },
            )
        loading -> CircularProgressIndicator()
        else -> Text("Image unavailable")
    }
}

@Composable
fun AdminCompressedImage(
    encodedImage: String,
    modifier: Modifier = Modifier
) {
    val result by produceState<Pair<Boolean, android.graphics.Bitmap?>>(
        initialValue = false to null,
        key1 = encodedImage
    ) {
        val decoded = withContext(Dispatchers.Default) {
            try {
                val bytes = Base64.decode(encodedImage, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                null
            }
        }
        value = true to decoded
    }

    val finished = result.first
    val bitmap = result.second

    when {
        bitmap != null -> Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = "Compressed campsite image",
            modifier = modifier,
            contentScale = ContentScale.Fit
        )
        !finished -> CircularProgressIndicator()
        else -> Text("Image unavailable")
    }
}
