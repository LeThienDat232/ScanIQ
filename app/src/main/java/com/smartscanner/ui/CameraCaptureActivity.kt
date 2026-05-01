package com.smartscanner.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smartscanner.data.AppDatabase
import com.smartscanner.data.Document
import com.smartscanner.data.DocumentRepository
import com.smartscanner.data.FileStorageManager
import com.smartscanner.databinding.ActivityCameraCaptureBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraCaptureBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    
    private var isScanModeOn = false
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var flashMode = ImageCapture.FLASH_MODE_OFF

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCameraCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        setupUI()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnToggleScan.setOnClickListener {
            isScanModeOn = !isScanModeOn
            if (isScanModeOn) {
                binding.btnToggleScan.text = "Scan Mode: ON"
                binding.scanOverlay.visibility = View.VISIBLE
            } else {
                binding.btnToggleScan.text = "Scan Mode: OFF"
                binding.scanOverlay.visibility = View.GONE
            }
        }

        binding.btnCapture.setOnClickListener { takePhoto() }

        binding.btnFlash.setOnClickListener {
            flashMode = if (flashMode == ImageCapture.FLASH_MODE_OFF) {
                ImageCapture.FLASH_MODE_ON
            } else {
                ImageCapture.FLASH_MODE_OFF
            }
            imageCapture?.flashMode = flashMode
            val icon = if (flashMode == ImageCapture.FLASH_MODE_ON) 
                com.google.android.material.R.drawable.design_ic_visibility
            else 
                com.google.android.material.R.drawable.design_ic_visibility_off
            // Note: Ideally use your own flash icons here
            Toast.makeText(this, "Flash ${if (flashMode == ImageCapture.FLASH_MODE_ON) "ON" else "OFF"}", Toast.LENGTH_SHORT).show()
        }

        binding.btnSwitchCamera.setOnClickListener {
            lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                CameraSelector.LENS_FACING_BACK
            } else {
                CameraSelector.LENS_FACING_FRONT
            }
            startCamera()
        }
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

            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .build()

            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return

        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    saveDocument(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    Toast.makeText(baseContext, "Capture failed", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        
        // Handle rotation
        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
        return Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
    }

    private fun saveDocument(bitmap: Bitmap) {
        lifecycleScope.launch {
            try {
                val timestamp = System.currentTimeMillis()
                val fileName = "SCAN_$timestamp.jpg"

                // 1. Save physical file (FileStorageManager handles unique naming)
                val filePath = withContext(Dispatchers.IO) {
                    FileStorageManager.saveImageToInternalStorage(this@CameraCaptureActivity, bitmap, fileName)
                }

                if (filePath != null) {
                    val actualFileName = File(filePath).name
                    // 2. Save to Room Database
                    withContext(Dispatchers.IO) {
                        val database = AppDatabase.getDatabase(this@CameraCaptureActivity)
                        val repository = DocumentRepository(database.documentDao(), database.folderDao())
                        
                        val newDoc = Document(
                            folderId = null,
                            title = actualFileName,
                            filePath = filePath,
                            fileType = "image/jpeg",
                            createdAt = timestamp
                        )
                        repository.insertDocument(newDoc)
                    }

                    Toast.makeText(this@CameraCaptureActivity, "Document saved: $actualFileName", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    throw Exception("Failed to save physical file")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving document", e)
                Toast.makeText(this@CameraCaptureActivity, "Error saving document: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "CameraCaptureActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
