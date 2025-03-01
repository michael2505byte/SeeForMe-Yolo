package com.example.seeformev2
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private var module: Module? = null
    private val TAG = "SeeForMe"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize offline Text-to-Speech
        tts = TextToSpeech(this, this)

        // Load the YOLOv8 TorchScript model from assets
        try {
            module = Module.load(assetFilePath(this, "yolov8s.torchscript.pt"))
        } catch (e: Exception) {
            Log.e(TAG, "Error loading model", e)
        }

        // Request camera permission if not granted
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), 101
            )
        }

        setContent {
            // State for the result text
            val resultTextState = remember { mutableStateOf("") }
            // State to hold the ImageCapture instance from CameraPreview
            val imageCaptureState = remember { mutableStateOf<androidx.camera.core.ImageCapture?>(null) }

            MaterialTheme {
                Surface {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Our composable that shows the camera preview and a capture button.
                        SeeForMeApp(
                            onImageCaptured = { bitmap ->
                                // Process the captured image and update the result state.
                                val result = processImage(bitmap)
                                resultTextState.value = result
                            },
                            onImageCaptureReady = { imageCapture ->
                                imageCaptureState.value = imageCapture
                            },
                            onManualCapture = {
                                // When the button is pressed, manually trigger a capture if possible.
                                imageCaptureState.value?.let { capture ->
                                    captureImage(capture, this@MainActivity) { bitmap ->
                                        val result = processImage(bitmap)
                                        resultTextState.value = result
                                    }
                                }
                            }
                        )
                        // Display the result text on screen.
                        Text(
                            text = resultTextState.value,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    /**
     * Process the captured image:
     * - Preprocess the bitmap for the model
     * - Run inference and get a dummy result
     * - Speak the result via TTS and return the result string.
     */
    private fun processImage(bitmap: Bitmap): String {
        val inputTensor: Tensor = TensorImageUtils.bitmapToFloat32Tensor(
            bitmap,
            TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
            TensorImageUtils.TORCHVISION_NORM_STD_RGB
        )

        val outputTensor = module?.forward(IValue.from(inputTensor))?.toTensor()
        val outputData = outputTensor?.dataAsFloatArray

        // Dummy parsing logic; replace with your own if needed.
        val detectedObjects = if (outputData == null) emptyList() else listOf("object1", "object2")
        val resultText = if (detectedObjects.isEmpty()) {
            "No objects detected"
        } else {
            "Detected: " + detectedObjects.joinToString(", ")
        }

        tts.speak(resultText, TextToSpeech.QUEUE_FLUSH, null, null)
        return resultText
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.shutdown()
    }

    /**
     * Utility function: Copy an asset file to a file accessible by PyTorch Mobile.
     */
    private fun assetFilePath(context: Context, assetName: String): String {
        val file = context.getFileStreamPath(assetName)
        if (file.exists() && file.length() > 0) return file.absolutePath
        context.assets.open(assetName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return file.absolutePath
    }
    private fun captureImage(
        imageCapture: androidx.camera.core.ImageCapture,
        context: Context,
        onImageCaptured: (Bitmap) -> Unit
    ) {
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(context),
            object : androidx.camera.core.ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: androidx.camera.core.ImageProxy) {
                    val bitmap = imageProxy.toBitmap()
                    onImageCaptured(bitmap)
                    imageProxy.close()
                }
                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                    Log.e("ManualCapture", "Capture failed: ${exception.message}", exception)
                }
            }
        )
    }
}
