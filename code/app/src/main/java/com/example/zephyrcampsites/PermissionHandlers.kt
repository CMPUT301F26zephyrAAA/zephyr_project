package com.example.zephyrcampsites

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.location.Location
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.location.FusedLocationProviderClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Class with any handlers for permissions.
 */
class PermissionHandlers {
    /**
     * Handler/launcher to handle asking for location permission.
     *
     * @param locationClient Location Provider Client to utilize to ask for location
     * @param locationSet Lambda to call when a location is determined (Done this way as Kotlin is pass by value)
     * @return Type ManagedActivityResultLauncher, not meant to be used directly but instead to call .launch on, which then would check for location permission and call locationSet.
     */
    @SuppressLint("MissingPermission")
    @Composable
    fun locationHandler (
        locationClient: FusedLocationProviderClient,
        locationSet: (Location?) -> Unit
    ) : ManagedActivityResultLauncher<Array<String>, Map<String, @JvmSuppressWildcards Boolean>> {
        return rememberLauncherForActivityResult( // Launcher to request location permissions
            ActivityResultContracts.RequestMultiplePermissions()
        ) { isGranted ->
            val fineLocationCheck = isGranted[Manifest.permission.ACCESS_FINE_LOCATION]
            val coarseLocationCheck = isGranted[Manifest.permission.ACCESS_COARSE_LOCATION]
            if (fineLocationCheck == true || coarseLocationCheck == true) {
                locationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        locationSet(location)
                    } else {
                        locationClient.lastLocation.addOnSuccessListener { location -> locationSet(location) }
                    }
                }
            } // If no permission, location stays null
        }
    }

    /**
     * Handler/launcher to handle saving image files.
     * The launcher is able to open dialog to save files.
     *
     * @param saveTarget Image to save to client device.
     * @return Type ManagedActivityResultLauncher, not meant to be used directly, instead to call .launch on and open the file location picker.
     */
    @Composable
    fun imageSaveHandler (
        saveTarget : ImageBitmap
    ) : ManagedActivityResultLauncher<Intent, ActivityResult> {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        return rememberLauncherForActivityResult( // Launcher to save QR code (Opens destination picker)
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val image = saveTarget.asAndroidBitmap()
            val data = result.data
            val uri = data?.data
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.let { outputStream ->
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            image.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            outputStream.flush()
                            outputStream.close()
                        }
                    }
                }
            }
        }
    }
}