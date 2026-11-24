package com.example.zephyrcampsites

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

class MyCampsitesView {

    /**
     * Displays a scrollable list of campsites registered by the owner.
     * Handles loading, error and empty states.
     *
     * @param ownerId The unique ID of the campsite owner.
     * @param controller Controller used to retrieve campsite data.
     * @param onBack Callback for returning to the previous screen.
     * @param openDetail Callback for opening campsite details.
     */
    @Composable
    fun MyCampsitesScreen(
        ownerId: String,
        controller: CampsiteController,
        onBack: () -> Unit,
        openDetail: (Int) -> Unit,
    ) {
        val model = controller.model
        val context = LocalContext.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back"
                    )
                }

                Text(
                    text = "My Campsites",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    model.error != null -> {
                        Text(
                            text = "Unable to load campsites.",
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    model.isLoading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    else -> {
                        val campsites =
                            controller.getOwnerCampsites(ownerId)

                        if (campsites.isEmpty()) {
                            EmptyCampsitesState(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(bottom = 16.dp)
                            ) {
                                items(
                                    items = campsites,
                                    key = { it.id }
                                ) { campsite ->
                                    OwnerCampsiteCard(
                                        campsite = campsite,
                                        onClick = {
                                            val id = campsite.id.toIntOrNull()

                                            if (id != null && id.toString() == campsite.id) {
                                                openDetail(id)
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "Unable to open campsite: Invalid campsite ID.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Displays a horizontal campsite card containing its promotional
     * image, name, location, price, availability, rating and guest capacity.
     *
     * @param campsite The campsite to display.
     * @param onClick Callback for opening the campsite details.
     */
    @Composable
    private fun OwnerCampsiteCard(
        campsite: Campsite,
        onClick: () -> Unit
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 2.dp
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CampsiteThumbnail(campsite)

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = campsite.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = campsite.location,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = campsite.pricePerNight?.let {
                            String.format(
                                Locale.CANADA,
                                "$%.2f / night",
                                it
                            )
                        } ?: "Price not set",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )

                    AvailabilityStatus(campsite)

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFB300),
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = campsite.averageRating?.let {
                                String.format(
                                    Locale.CANADA,
                                    "%.1f (%d)",
                                    it,
                                    campsite.rating.size
                                )
                            } ?: "No ratings",
                            fontSize = 12.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Guest capacity",
                            modifier = Modifier.size(16.dp)
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Text(
                            text = "${campsite.maxOccupancy} guests",
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(onClick = onClick) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View campsite details"
                    )
                }
            }
        }
    }

    /**
     * Decodes and displays the first non-empty promotional image
     * associated with a campsite.
     * Displays a placeholder if no valid image is available.
     *
     * @param campsite The campsite containing the promotional images.
     */
    @Composable
    private fun CampsiteThumbnail(campsite: Campsite) {
        val firstImage = campsite.compressedImages
            .firstOrNull { it.isNotBlank() }

        val bitmap by produceState<Bitmap?>(
            initialValue = null,
            key1 = firstImage
        ) {
            value = null

            value = withContext(Dispatchers.Default) {
                try {
                    if (firstImage.isNullOrBlank()) {
                        null
                    } else {
                        val bytes = Base64.decode(
                            firstImage,
                            Base64.DEFAULT
                        )

                        BitmapFactory.decodeByteArray(
                            bytes,
                            0,
                            bytes.size
                        )
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    MaterialTheme.colorScheme.surfaceVariant
                ),
            contentAlignment = Alignment.Center
        ) {
            val image = bitmap

            if (image != null) {
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = campsite.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "No image",
                        fontSize = 10.sp
                    )
                }
            }
        }
    }

    /**
     * Displays a colour-coded availability indicator based on
     * the campsite's booking period and the current date.
     *
     * Booking period: The range of dates set by the campsite owner during which the campsite is available for guests to stay.
     * @param campsite The campsite whose booking period is evaluated.
     */
    @Composable
    private fun AvailabilityStatus(campsite: Campsite) {
        val currentDate = Date()

        val isAvailable =
            !currentDate.before(campsite.bookingPeriodStart) &&
                    !currentDate.after(campsite.bookingPeriodEnd)

        val statusColor = if (isAvailable) {
            Color(0xFF08BE50)
        } else {
            Color(0xFFF87171)
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(statusColor.copy(alpha = 0.15f))
                .padding(
                    horizontal = 12.dp,
                    vertical = 4.dp
                )
        ) {
            Text(
                text = if (isAvailable) {
                    "Available"
                } else {
                    "Unavailable"
                },
                color = statusColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }

    /**
     * Displays an informational message when the owner has
     * no registered campsites.
     *
     * @param modifier Modifier applied to the empty state layout.
     */
    @Composable
    private fun EmptyCampsitesState(
        modifier: Modifier = Modifier
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Landscape,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "No campsites yet",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "You haven't registered any campsites yet. Register your first campsite to get started.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}