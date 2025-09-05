package com.example.seeformev2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

fun ImageProxy.toBitmap(): Bitmap? {
    if (format != ImageFormat.YUV_420_888) return null

    // Get YUV planes
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    // Get buffer sizes
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    // Create NV21 byte array
    val nv21 = ByteArray(ySize + uSize + vSize)

    // Copy Y channel
    yBuffer.get(nv21, 0, ySize)

    // Interleave V and U channels
    val position = ySize
    vBuffer.get(nv21, position, vSize)
    uBuffer.get(nv21, position + vSize, uSize)

    // Create YUV image
    val yuvImage = YuvImage(
        nv21,
        ImageFormat.NV21,
        width,
        height,
        null
    )

    // Compress to JPEG
    val outputStream = ByteArrayOutputStream()
    yuvImage.compressToJpeg(
        Rect(0, 0, width, height),
        95, // Quality
        outputStream
    )

    // Decode to Bitmap
    val options = BitmapFactory.Options().apply {
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    val jpegData = outputStream.toByteArray()
    val bitmap = BitmapFactory.decodeByteArray(jpegData, 0, jpegData.size, options) ?: return null

    // Apply rotation
    val rotationDegrees = imageInfo.rotationDegrees
    if (rotationDegrees != 0) {
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
        }
        return Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        ).also { bitmap.recycle() }
    }

    return bitmap
}