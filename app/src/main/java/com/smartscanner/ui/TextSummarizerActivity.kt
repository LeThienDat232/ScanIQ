package com.smartscanner.ui

import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.smartscanner.databinding.ActivityTextSummarizerBinding
import com.smartscanner.network.BackendApiService
import com.smartscanner.network.SummarizeRequest
import com.smartscanner.network.SummarizeResponse
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TextSummarizerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTextSummarizerBinding
    private lateinit var apiService: BackendApiService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge
        enableEdgeToEdge()
        
        binding = ActivityTextSummarizerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Adjust for system bars (status bar and navigation bar)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupToolbar()
        setupRetrofit()
        setupListeners()
        
        // Initial state: Hide summary components
        hideSummary()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun setupRetrofit() {
        // Use 10.0.2.2 for Android Emulator to access localhost of the host machine
        val baseUrl = "http://10.0.2.2:3000/" 
        
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(BackendApiService::class.java)
    }

    private fun setupListeners() {
        binding.btnSummarize.setOnClickListener {
            val text = binding.etInputText.text.toString().trim()
            if (text.isNotEmpty()) {
                summarizeText(text)
            } else {
                Toast.makeText(this, "Please enter some text", Toast.LENGTH_SHORT).show()
            }
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

    private fun summarizeText(text: String) {
        setLoading(true)
        val request = SummarizeRequest(text)
        
        apiService.summarizeText(request).enqueue(object : Callback<SummarizeResponse> {
            override fun onResponse(call: Call<SummarizeResponse>, response: Response<SummarizeResponse>) {
                setLoading(false)
                if (response.isSuccessful && response.body()?.status == "success") {
                    val summaryText = response.body()?.data?.summaryText ?: ""
                    binding.tvSummaryResult.text = summaryText
                    showSummary()
                } else {
                    showError("Summarization failed. Please check your connection.")
                }
            }

            override fun onFailure(call: Call<SummarizeResponse>, t: Throwable) {
                setLoading(false)
                showError("Connection failed. Please try again.")
            }
        })
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
