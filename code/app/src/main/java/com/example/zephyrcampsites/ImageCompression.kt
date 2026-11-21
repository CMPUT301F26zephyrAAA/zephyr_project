package com.example.zephyrcampsites


import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageCompressionUtil {

    suspend fun compressImage(
        context: Context,
        imageUri: Uri
    ): String = withContext(Dispatchers.IO) {

        val resolver = context.contentResolver

        // Read image dimensions without loading pixels
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }


        val inputStream = resolver.openInputStream(imageUri)
            ?: error("Cannot open image URI")

        inputStream.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                options
            )
        }

        require(options.outWidth > 0 && options.outHeight > 0) {
            "Unable to read image dimensions"
        }


        require(options.outWidth > 0 && options.outHeight > 0) {
            "Invalid image"
        }

        // Downsample large images
        var sampleSize = 1
        while (
            options.outWidth / sampleSize > 1024 ||
            options.outHeight / sampleSize > 1024
        ) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
        }

        val bitmap = resolver.openInputStream(imageUri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOptions)
        } ?: error("Could not decode image")

        val maxDimension = 1024
        val scale = minOf(
            1f,
            maxDimension.toFloat() /
                    maxOf(bitmap.width, bitmap.height)
        )

        val resized = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )

        val output = ByteArrayOutputStream()

        val compressed = resized.compress(
            Bitmap.CompressFormat.JPEG,
            65,
            output
        )

        require(compressed) { "Compression failed" }

        val bytes = output.toByteArray()

        // Reject images that remain too large
        require(bytes.size <= 150 * 1024) {
            "Compressed image exceeds 150 KB"
        }

        Base64.encodeToString(
            bytes,
            Base64.NO_WRAP
        )
    }
}
