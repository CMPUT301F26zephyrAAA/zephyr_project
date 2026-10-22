package com.example.zephyrcampsites

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.publicvalue.multiplatform.qrcode.CameraPosition
import org.publicvalue.multiplatform.qrcode.CodeType
import org.publicvalue.multiplatform.qrcode.ScannerWithPermissions
import qrcode.QRCode
import qrcode.render.QRCodeGraphics

class QRCodeHandler {
    fun qRGenerate(type: String, id: Int) : ImageBitmap {
        val targetQRCode = QRCode.ofRoundedSquares().build("$type://$id")
        val pngBytes = targetQRCode.render().nativeImage() as Bitmap
        val image = pngBytes.asImageBitmap()
        return image
    }

    @Composable
    fun QRScan() {
        ScannerWithPermissions(
            onScanned = { scannedText -> qRParser(scannedText) },
            types = listOf(CodeType.QR),
            cameraPosition = CameraPosition.BACK,
            enableTorch = false
        )
    }

    fun qRParser(result: String): Boolean {
        val resList = result.split("://")
        val category = resList[0]
        val page = resList[1]
        if (category in listOf("campsite", "lottery", "account")) {
            return true
        } else {
            return false
        }
    }
}