package com.smartscanner.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.smartscanner.databinding.ActivityTextSummarizerBinding
import com.smartscanner.service.SummarizerResultStore
import com.smartscanner.service.SummarizerService

class TextSummarizerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextSummarizerBinding
    
    private val resultReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val result = intent?.getStringExtra("RESULT") ?: ""
            val isSuccess = intent?.getBooleanExtra("IS_SUCCESS", false) ?: false
            
            setLoading(false)
            if (isSuccess) {
                binding.tvSummaryResult.text = result
                showSummary()
            } else {
                showError(result)
            }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            startSummarization()
        } else {
            Toast.makeText(this, "Permission denied for notifications", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTextSummarizerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupListeners()
        hideSummary()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter("com.smartscanner.SUMMARIZATION_RESULT")
        LocalBroadcastManager.getInstance(this).registerReceiver(resultReceiver, filter)
    }

    override fun onResume() {
        super.onResume()
        // Kiểm tra xem Service đã hoàn thành trong khi App ở background chưa
        checkExistingResult()
    }

    override fun onStop() {
        super.onStop()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(resultReceiver)
    }

    private fun checkExistingResult() {
        if (SummarizerResultStore.isLoading) {
            setLoading(true)
        } else if (SummarizerResultStore.lastResult != null) {
            setLoading(false)
            if (SummarizerResultStore.isSuccess) {
                binding.tvSummaryResult.text = SummarizerResultStore.lastResult
                showSummary()
            } else {
                // Nếu có lỗi đã lưu, hiển thị nhưng không Toast liên tục
                binding.tvSummaryResult.text = "Lỗi trước đó: ${SummarizerResultStore.lastResult}"
                showSummary()
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupListeners() {
        binding.btnSummarize.setOnClickListener {
            checkPermissionAndStart()
        }

        binding.btnCopy.setOnClickListener {
            val summary = binding.tvSummaryResult.text.toString()
            if (summary.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = android.content.ClipData.newPlainText("Summary", summary)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Summary copied to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermissionAndStart() {
        val text = binding.etInputText.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) {
                startSummarization()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            startSummarization()
        }
    }

    private fun startSummarization() {
        val text = binding.etInputText.text.toString().trim()
        
        // Reset store trước khi bắt đầu lượt mới
        SummarizerResultStore.clear()
        SummarizerResultStore.isLoading = true

        setLoading(true)
        
        val intent = Intent(this, SummarizerService::class.java).apply {
            putExtra("INPUT_TEXT", text)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.btnSummarize.isEnabled = !isLoading
        if (isLoading) {
            hideSummary()
        }
    }

    private fun showSummary() {
        binding.tvLabelResult.visibility = View.VISIBLE
        binding.cardResult.visibility = View.VISIBLE
        binding.btnCopy.visibility = View.VISIBLE
    }

    private fun hideSummary() {
        binding.tvLabelResult.visibility = View.GONE
        binding.cardResult.visibility = View.GONE
        binding.btnCopy.visibility = View.GONE
        binding.tvSummaryResult.text = ""
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
