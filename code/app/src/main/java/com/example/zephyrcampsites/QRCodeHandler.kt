package com.example.zephyrcampsites

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.publicvalue.multiplatform.qrcode.CameraPosition
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions
import qrcode.QRCode
import qrcode.color.Colors

/**
 * Class to handle anything related to an actual QR code, such as generating or scanning.
 */
class QRCodeHandler {
    /**
     * Generates a QR code that, when scanned, opens the intended page.
     *
     * @param type The type of detail page to lead to (in string). Needs to be either "account", "lottery", or "campsite" (STC) exactly.
     * @param id The id of the entry to open of set type.
     */
    fun qRGenerate(type: String, id: Int) : ImageBitmap {
        val targetQRCode = QRCode.ofRoundedSquares().withBackgroundColor(Colors.WHITE).build("$type://$id")
        val pngBytes = targetQRCode.render().nativeImage() as Bitmap
        val image = pngBytes.asImageBitmap()
        return image
    }

    /**
     * Composable function that opens a camera to scan QR codes.
     * Currently written to handle campsites, accounts, and lotteries.
     *
     * @param campsiteNav Function to trigger when a campsite QR code is scanned
     * @param accountNav Function to trigger when an account QR code is scanned
     * @param lotteryNav Function to trigger when a lottery QR code is scanned
     */
    @Composable
    fun QRScan(
        campsiteNav: (Int) -> Unit,
        accountNav: (Int) -> Unit,
        lotteryNav: (Int) -> Unit
    ) {
        var parsed by remember {mutableStateOf<String>("")}
        ScannerWithPermissions(
            onScanned = { scannedText ->
                parsed = scannedText; true
            },
            types = listOf(CodeType.QR),
            cameraPosition = CameraPosition.BACK,
            enableTorch = false
        )
        val address = parsed.split("://")
        when (address[0]) {
            "campsite" -> campsiteNav(address[1].toInt())
            "account" -> accountNav(address[1].toInt())
            "lottery" -> lotteryNav(address[1].toInt())
        }
    }
}