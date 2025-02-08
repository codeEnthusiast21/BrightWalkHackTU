package com.edu.tiethackathon

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.camera.core.Camera
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.edu.tiethackathon.databinding.ActivityMainBinding
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tts: TextToSpeech
    private lateinit var binding: ActivityMainBinding
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private val API_URL = "https://2079-117-203-246-41.ngrok-free.app/completion"
    private var isCameraFrozen = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Initialize Text-to-Speech
        tts = TextToSpeech(this, this)

        // Set up the click listener for capturing photos
        binding.viewFinder.setOnClickListener {
            freezeCamera()
            takePhoto()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }
    private fun freezeCamera() {
        isCameraFrozen = true
        camera?.let { camera ->
            try {
                // Disable camera preview updates
                camera.cameraControl.enableTorch(false)
                val previewView = binding.viewFinder

                // Store the current preview frame
                val bitmap = previewView.bitmap

                // Create a new ImageView to hold the frozen frame
                val frozenFrameView = ImageView(this).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    setImageBitmap(bitmap)
                }

                // Add frozen frame on top of the preview
                (previewView.parent as ViewGroup).addView(frozenFrameView)
                frozenFrameView.tag = "frozen_frame"

            } catch (e: Exception) {
                Log.e(TAG, "Error freezing camera: ${e.message}")
                resumeCamera() // Reset on error
            }
        }
    }    private fun resumeCamera() {
        isCameraFrozen = false
        try {
            // Remove the frozen frame if it exists
            val viewGroup = binding.viewFinder.parent as ViewGroup
            val frozenFrame = viewGroup.findViewWithTag<ImageView>("frozen_frame")
            frozenFrame?.let {
                viewGroup.removeView(it)
            }

            // Re-enable camera preview
            camera?.cameraControl?.enableTorch(false)

        } catch (e: Exception) {
            Log.e(TAG, "Error resuming camera: ${e.message}")
        }
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.capacity())
                    buffer.get(bytes)

                    // Convert to Base64
                    val base64Image = Base64.encodeToString(bytes, Base64.DEFAULT)

                    // Send to API
                    sendImageToApi(base64Image)

                    image.close()
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    Toast.makeText(baseContext, "Photo capture failed", Toast.LENGTH_SHORT).show()
                    resumeCamera()

                }
            }
        )
    }

    private fun sendImageToApi(base64Image: String) {
        val queue = Volley.newRequestQueue(this)
        val jsonObject = JSONObject()
        jsonObject.put("image", base64Image)

        val request = JsonObjectRequest(
            Request.Method.POST, API_URL, jsonObject,
            { response ->
                val result = response.getString("result")
                binding.resultText.text = result
                speakText(result)

                resumeCamera()
            },
            { error ->
                Toast.makeText(this, "API Error: ${error.message}", Toast.LENGTH_LONG).show()

                resumeCamera()

            }
        )

        queue.add(request)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder().build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera failed to start", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun speakText(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            // Set language here
            val result = tts.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Toast.makeText(this, "Language not supported", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { permission: String ->
        ContextCompat.checkSelfPermission(baseContext, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        if (tts.isSpeaking) {
            tts.stop()
        }
        tts.shutdown()
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS: Array<String> = arrayOf("android.permission.CAMERA")
    }
}
