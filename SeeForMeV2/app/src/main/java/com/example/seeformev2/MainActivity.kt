package com.example.seeformev2

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.pytorch.IValue
import org.pytorch.Module
import org.pytorch.Tensor
import org.pytorch.torchvision.TensorImageUtils
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*
import kotlin.math.exp

class MainActivity : AppCompatActivity() {

    private lateinit var module: Module
    private lateinit var tts: TextToSpeech
    private var isProcessing = false
    private var ttsReady = false

    private lateinit var previewView: PreviewView
    private var imageCapture: ImageCapture? = null
    private var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private val classLabels = listOf(
        "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
        "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
        "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe",
        "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
        "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup", "fork", "knife",
        "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog",
        "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed", "dining table", "toilet",
        "tv", "laptop", "mouse", "remote", "keyboard", "cell phone", "microwave", "oven", "toaster",
        "sink", "refrigerator", "book", "clock", "vase", "scissors", "teddy bear", "hair drier", "toothbrush"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        findViewById<Button>(R.id.captureButton).setOnClickListener { captureImage() }
        findViewById<Button>(R.id.settingsButton).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts.language = Locale.getDefault()
                ttsReady = true
                checkPermissions()
                loadModel()
                startCamera()
            } else {
                Log.e("TTS", "Initialization failed")
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && !isProcessing) {
            captureImage()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }
    }

    private fun loadModel() {
        try {
            module = Module.load(assetFilePath("yolov8n.pt"))
            Log.d("Model", "Loaded successfully")
        } catch (e: Exception) {
            Log.e("Model", "Load failed", e)
            speak("Failed to load model", true)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("Camera", "Start failed", e)
                speak("Camera failed to start", true)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        if (isProcessing) return
        isProcessing = true

        imageCapture?.takePicture(ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    try {
                        processImage(image)
                    } finally {
                        image.close()
                        isProcessing = false
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("Capture", "Failed", exception)
                    speak("Failed to capture image", true)
                    isProcessing = false
                }
            })
    }

    private fun processImage(image: ImageProxy) {
        try {
            val bitmap = image.toBitmap()?.let { original ->
                // Calculate scale while maintaining aspect ratio
                val targetSize = 640
                val scale = Math.min(
                    targetSize.toFloat() / original.width,
                    targetSize.toFloat() / original.height
                )
                val scaledWidth = (original.width * scale).toInt()
                val scaledHeight = (original.height * scale).toInt()

                // Create padded bitmap
                val paddedBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(paddedBitmap)
                val left = (targetSize - scaledWidth) / 2
                val top = (targetSize - scaledHeight) / 2

                val scaledBitmap = Bitmap.createScaledBitmap(
                    original,
                    scaledWidth,
                    scaledHeight,
                    true
                )

                canvas.drawBitmap(scaledBitmap, left.toFloat(), top.toFloat(), null)
                scaledBitmap.recycle()
                paddedBitmap
            } ?: run {
                speak("Invalid image", true)
                return
            }

            // Convert to tensor with normalization
            val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(
                bitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB,
                TensorImageUtils.TORCHVISION_NORM_STD_RGB
            )
            bitmap.recycle()

            // Run inference
            val outputTensor = module.forward(IValue.from(inputTensor)).toTensor()
            val outputData = outputTensor.dataAsFloatArray

            // Process detections
            val detections = outputData.toList().chunked(6).mapNotNull { data ->
                val confidence = sigmoid(data[4]).coerceIn(0.1f, 1.0f)
                if (confidence < 0.5f) return@mapNotNull null

                val classIndex = data[5].toInt().coerceIn(0, classLabels.size - 1)
                Detection(
                    x = data[0],
                    y = data[1],
                    w = data[2],
                    h = data[3],
                    confidence = confidence,
                    classIndex = classIndex
                )
            }
                .sortedByDescending { it.confidence }
                .take(3)

            // Report results
            if (detections.isNotEmpty()) {
                val results = detections.joinToString(", ") {
                    "${classLabels[it.classIndex]} (${(it.confidence * 100).toInt()}%)"
                }
                speak("Detected: $results", false)
            } else {
                speak("No objects detected", false)
            }
        } catch (e: Exception) {
            Log.e("Processing", "Error", e)
            speak("Error processing image", true)
        }
    }

    private fun sigmoid(x: Float): Float {
        return if (x >= 0) {
            1f / (1f + exp(-x.toDouble())).toFloat()
        } else {
            val ex = exp(x.toDouble()).toFloat()
            ex / (1f + ex)
        }
    }

    private fun speak(text: String, isError: Boolean) {
        if (ttsReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    @Throws(IOException::class)
    private fun assetFilePath(assetName: String): String {
        val file = File(filesDir, assetName)
        if (!file.exists()) {
            assets.open(assetName).use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
        }
        return file.absolutePath
    }

    override fun onDestroy() {
        tts.shutdown()
        super.onDestroy()
    }
}

data class Detection(
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float,
    val confidence: Float,
    val classIndex: Int
)