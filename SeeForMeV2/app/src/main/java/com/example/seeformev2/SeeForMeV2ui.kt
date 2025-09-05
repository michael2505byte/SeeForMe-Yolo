package com.example.seeformev2

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
@Composable
fun SeeForMeApp(
    onImageCaptured: (Bitmap) -> Unit,
    onImageCaptureReady: (ImageCapture) -> Unit,
    onManualCapture: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Camera preview takes the majority of the screen.
        Box(modifier = Modifier.weight(1f)) {
            CameraPreview(
                onImageCaptured = onImageCaptured,
                onImageCaptureReady = onImageCaptureReady,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Manual capture button.
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
    onImageCaptureReady: (ImageCapture) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    AndroidView(
        factory = { ctx -> PreviewView(ctx) },
        modifier = modifier,
        update = { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build()
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        context as LifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    // Provide the ImageCapture instance for manual capture.
                    onImageCaptureReady(imageCapture)

                    // Optionally: automatically capture one image when the preview starts.
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                val bitmap = imageProxy.toBitmap()
                                if (bitmap != null) { // ✅ Added null check here
                                    onImageCaptured(bitmap)
                                } else {
                                    Log.e("CameraPreview", "Failed to convert image to bitmap")
                                }
                                imageProxy.close()
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("CameraPreview", "Auto-capture failed: ${exception.message}", exception)
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
