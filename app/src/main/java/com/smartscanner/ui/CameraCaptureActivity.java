package com.smartscanner.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.smartscanner.data.AppDatabase;
import com.smartscanner.data.Document;
import com.smartscanner.data.DocumentRepository;
import com.smartscanner.data.FileStorageManager;
import com.smartscanner.data.ImageTextIndexer;
import com.smartscanner.databinding.ActivityCameraCaptureBinding;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraCaptureActivity extends AppCompatActivity {
    private static final String TAG = "CameraCaptureActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private static final String[] REQUIRED_PERMISSIONS = {Manifest.permission.CAMERA};

    private ActivityCameraCaptureBinding binding;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        binding = ActivityCameraCaptureBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        cameraExecutor = Executors.newSingleThreadExecutor();
        applySystemBarInsets();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        setupUi();
    }

    private void setupUi() {
        binding.btnBack.setOnClickListener(v -> finish());

        binding.btnCapture.setOnClickListener(v -> takePhoto());

        binding.btnFlash.setOnClickListener(v -> {
            flashMode = flashMode == ImageCapture.FLASH_MODE_OFF
                    ? ImageCapture.FLASH_MODE_ON
                    : ImageCapture.FLASH_MODE_OFF;
            if (imageCapture != null) {
                imageCapture.setFlashMode(flashMode);
            }
            Toast.makeText(
                    this,
                    "Flash " + (flashMode == ImageCapture.FLASH_MODE_ON ? "ON" : "OFF"),
                    Toast.LENGTH_SHORT
            ).show();
        });

        binding.btnSwitchCamera.setOnClickListener(v -> {
            lensFacing = lensFacing == CameraSelector.LENS_FACING_FRONT
                    ? CameraSelector.LENS_FACING_BACK
                    : CameraSelector.LENS_FACING_FRONT;
            startCamera();
        });
    }

    private void applySystemBarInsets() {
        int topPaddingLeft = binding.topPanel.getPaddingLeft();
        int topPaddingTop = binding.topPanel.getPaddingTop();
        int topPaddingRight = binding.topPanel.getPaddingRight();
        int topPaddingBottom = binding.topPanel.getPaddingBottom();

        int bottomPaddingLeft = binding.bottomPanel.getPaddingLeft();
        int bottomPaddingTop = binding.bottomPanel.getPaddingTop();
        int bottomPaddingRight = binding.bottomPanel.getPaddingRight();
        int bottomPaddingBottom = binding.bottomPanel.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, windowInsets) -> {
            Insets insets = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout()
            );

            binding.topPanel.setPadding(
                    topPaddingLeft,
                    topPaddingTop + insets.top,
                    topPaddingRight,
                    topPaddingBottom
            );
            binding.bottomPanel.setPadding(
                    bottomPaddingLeft,
                    bottomPaddingTop,
                    bottomPaddingRight,
                    bottomPaddingBottom + insets.bottom
            );

            return windowInsets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(binding.viewFinder.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(flashMode)
                        .build();

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (Exception exc) {
                Log.e(TAG, "Use case binding failed", exc);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void takePhoto() {
        ImageCapture capture = imageCapture;
        if (capture == null) {
            return;
        }

        capture.takePicture(
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        Bitmap bitmap = imageProxyToBitmap(image);
                        image.close();
                        if (bitmap != null) {
                            saveDocument(bitmap);
                        } else {
                            Toast.makeText(CameraCaptureActivity.this, "Capture failed", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Log.e(TAG, "Photo capture failed: " + exception.getMessage(), exception);
                        Toast.makeText(CameraCaptureActivity.this, "Capture failed", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                return null;
            }

            Matrix matrix = new Matrix();
            matrix.postRotate(image.getImageInfo().getRotationDegrees());
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        } catch (Exception e) {
            Log.e(TAG, "Bitmap conversion failed", e);
            return null;
        }
    }

    private void saveDocument(Bitmap bitmap) {
        DocumentRepository.DATABASE_EXECUTOR.execute(() -> {
            try {
                long timestamp = System.currentTimeMillis();
                String fileName = "SCAN_" + timestamp + ".jpg";
                String filePath = FileStorageManager.saveImageToInternalStorage(this, bitmap, fileName);

                if (filePath == null) {
                    throw new IllegalStateException("Failed to save physical file");
                }

                String actualFileName = new File(filePath).getName();
                AppDatabase database = AppDatabase.getDatabase(this);
                DocumentRepository repository = new DocumentRepository(database, database.documentDao(), database.folderDao());
                Document newDocument = new Document(null, actualFileName, filePath, "image/jpeg", timestamp);
                repository.insertDocument(newDocument, documentId -> {
                    Document savedDocument = new Document((int) documentId, null, actualFileName, filePath, "image/jpeg", null, timestamp);
                    ImageTextIndexer.indexIfNeeded(getApplicationContext(), repository, savedDocument);
                    Toast.makeText(
                            CameraCaptureActivity.this,
                            "Document saved: " + actualFileName,
                            Toast.LENGTH_SHORT
                    ).show();
                    finish();
                });
            } catch (Exception e) {
                Log.e(TAG, "Error saving document", e);
                runOnUiThread(() -> Toast.makeText(
                        CameraCaptureActivity.this,
                        "Error saving document: " + e.getMessage(),
                        Toast.LENGTH_LONG
                ).show());
            }
        });
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
