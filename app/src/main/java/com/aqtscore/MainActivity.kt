package com.aqtscore

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.aqtscore.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentImageBitmap: Bitmap? = null
    private var currentPhotoUri: Uri? = null
    private val bulletHoleDetector = BulletHoleDetector()

    companion object {
        private const val TAG = "MainActivity"
    }

    // Camera permission launcher
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(
                this,
                R.string.camera_permission_required,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Storage permission launcher
    private val requestStoragePermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchGallery()
        } else {
            Toast.makeText(
                this,
                R.string.storage_permission_required,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Camera launcher
    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            currentPhotoUri?.let { uri ->
                loadImageFromUri(uri)
            }
        }
    }

    // Gallery launcher
    private val selectImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            loadImageFromUri(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize OpenCV
        if (!OpenCVHelper.initialize(this)) {
            Log.e(TAG, "Failed to initialize OpenCV")
            Toast.makeText(this, "Failed to initialize OpenCV", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.takePhotoButton.setOnClickListener {
            checkCameraPermissionAndTakePhoto()
        }

        binding.selectImageButton.setOnClickListener {
            checkStoragePermissionAndSelectImage()
        }

        binding.analyzeButton.setOnClickListener {
            analyzeTarget()
        }
    }

    private fun checkCameraPermissionAndTakePhoto() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                requestCameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    private fun checkStoragePermissionAndSelectImage() {
        // For Android 13+ (API 33+), we use READ_MEDIA_IMAGES
        // For older versions, we use READ_EXTERNAL_STORAGE
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                launchGallery()
            }
            else -> {
                requestStoragePermission.launch(permission)
            }
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = createImageFile()
            currentPhotoUri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            takePicture.launch(currentPhotoUri)
        } catch (e: IOException) {
            Log.e(TAG, "Error creating image file", e)
            Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchGallery() {
        selectImage.launch("image/*")
    }

    private fun createImageFile(): File {
        val storageDir = getExternalFilesDir(null)
        return File.createTempFile(
            "target_${System.currentTimeMillis()}",
            ".jpg",
            storageDir
        )
    }

    private fun loadImageFromUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                // Optionally resize large images for performance
                currentImageBitmap = resizeBitmapIfNeeded(bitmap)
                binding.imageView.setImageBitmap(currentImageBitmap)
                binding.analyzeButton.isEnabled = true

                // Reset score display
                resetScoreDisplay()
            } else {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image", e)
            Toast.makeText(this, "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resizeBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val maxDimension = 2048
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val scale = maxDimension.toFloat() / maxOf(width, height)
        val matrix = Matrix()
        matrix.postScale(scale, scale)

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    private fun analyzeTarget() {
        val bitmap = currentImageBitmap ?: return

        binding.analyzeButton.isEnabled = false
        binding.takePhotoButton.isEnabled = false
        binding.selectImageButton.isEnabled = false

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.Default) {
                    bulletHoleDetector.analyzeTarget(bitmap)
                }

                // Update UI with results
                binding.imageView.setImageBitmap(result.annotatedBitmap)
                binding.holesDetectedText.text = getString(
                    R.string.holes_detected,
                    result.bulletHoles.size
                )
                binding.totalScoreText.text = getString(
                    R.string.total_score,
                    result.totalScore
                )

                Toast.makeText(
                    this@MainActivity,
                    "Analysis complete: ${result.bulletHoles.size} holes detected",
                    Toast.LENGTH_SHORT
                ).show()

            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing target", e)
                Toast.makeText(
                    this@MainActivity,
                    "Error analyzing target: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.analyzeButton.isEnabled = true
                binding.takePhotoButton.isEnabled = true
                binding.selectImageButton.isEnabled = true
            }
        }
    }

    private fun resetScoreDisplay() {
        binding.holesDetectedText.text = getString(R.string.holes_detected, 0)
        binding.totalScoreText.text = getString(R.string.total_score, 0)
    }

    override fun onDestroy() {
        super.onDestroy()
        currentImageBitmap?.recycle()
        currentImageBitmap = null
    }
}
