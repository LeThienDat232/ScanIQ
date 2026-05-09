package com.smartscanner.ui;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.smartscanner.databinding.ActivityTextSummarizerBinding;
import com.smartscanner.service.SummarizerResultStore;
import com.smartscanner.service.SummarizerService;

public class TextSummarizerActivity extends AppCompatActivity {
    private ActivityTextSummarizerBinding binding;
    private ActivityResultLauncher<String> requestPermissionLauncher;

    private final BroadcastReceiver resultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String result = intent != null ? intent.getStringExtra("RESULT") : "";
            boolean isSuccess = intent != null && intent.getBooleanExtra("IS_SUCCESS", false);

            setLoading(false);
            if (isSuccess) {
                binding.tvSummaryResult.setText(result);
                showSummary();
            } else {
                showError(result == null ? "Unknown error" : result);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityTextSummarizerBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startSummarization();
                    } else {
                        Toast.makeText(this, "Permission denied for notifications", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupToolbar();
        setupListeners();
        hideSummary();
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter("com.smartscanner.SUMMARIZATION_RESULT");
        LocalBroadcastManager.getInstance(this).registerReceiver(resultReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        checkExistingResult();
    }

    @Override
    protected void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(resultReceiver);
    }

    private void checkExistingResult() {
        if (SummarizerResultStore.isLoading) {
            setLoading(true);
        } else if (SummarizerResultStore.lastResult != null) {
            setLoading(false);
            binding.tvSummaryResult.setText(SummarizerResultStore.lastResult);
            showSummary();
        }
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());
    }

    private void setupListeners() {
        binding.btnSummarize.setOnClickListener(v -> checkPermissionAndStart());

        binding.btnCopy.setOnClickListener(v -> {
            String summary = binding.tvSummaryResult.getText().toString();
            if (!summary.isEmpty()) {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("Summary", summary);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(clip);
                }
                Toast.makeText(this, "Summary copied to clipboard", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkPermissionAndStart() {
        String text = binding.etInputText.getText().toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            startSummarization();
        }
    }

    private void startSummarization() {
        String text = binding.etInputText.getText().toString().trim();
        SummarizerResultStore.clear();
        SummarizerResultStore.isLoading = true;

        setLoading(true);

        Intent intent = new Intent(this, SummarizerService.class);
        intent.putExtra("INPUT_TEXT", text);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void setLoading(boolean isLoading) {
        binding.loadingIndicator.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSummarize.setEnabled(!isLoading);
        if (isLoading) {
            hideSummary();
        }
    }

    private void showSummary() {
        binding.tvLabelResult.setVisibility(View.VISIBLE);
        binding.cardResult.setVisibility(View.VISIBLE);
        binding.btnCopy.setVisibility(View.VISIBLE);
    }

    private void hideSummary() {
        binding.tvLabelResult.setVisibility(View.GONE);
        binding.cardResult.setVisibility(View.GONE);
        binding.btnCopy.setVisibility(View.GONE);
        binding.tvSummaryResult.setText("");
    }

    private void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
