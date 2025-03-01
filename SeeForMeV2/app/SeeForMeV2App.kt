package com.example.seeforeme

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.core.content.ContextCompat

@Composable
fun SeeForMeApp(
    onImageCaptured: (Bitmap) -> Unit,
    onManualCapture: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Camera preview takes the majority of the screen
        Box(modifier = Modifier.weight(1f)) {
            CameraPreview(
                onImageCaptured = onImageCaptured,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Manual capture button with large touch target for accessibility
        Button(
            onClick = onManualCapture,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(text = "Capture Image")
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun CameraPreview(
    onImageCaptured: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx ->
            androidx.camera.view.PreviewView(ctx).apply {
                // This view will be used to show the camera preview.
            }
        },
        modifier = modifier,
        update = { previewView ->
            // Bind the camera when the PreviewView is available.
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                // Build preview and image capture use cases.
                val preview = Preview.Builder().build()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        context as LifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    // Automatically capture an image after binding.
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                val bitmap = imageProxy.toBitmap()
                                onImageCaptured(bitmap)
                                imageProxy.close()
                            }
                            override fun onError(exception: ImageCaptureException) {
                                Log.e("CameraPreview", "Capture failed: ${exception.message}", exception)
                            }
                        }
                    )
                } catch (exc: Exception) {
                    Log.e("CameraPreview", "Use case binding failed", exc)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    )
}

/**
 * Extension function to convert an ImageProxy to a Bitmap.
 *
 * NOTE: This implementation assumes the image is in JPEG format.
 * In a real scenario, you might need to convert from YUV format.
 */
fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
