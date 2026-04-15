package com.smartscanner.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.smartscanner.network.BackendApiService
import com.smartscanner.network.SummarizeRequest
import com.smartscanner.network.SummarizeResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class SummarizerService : Service() {

    private val TAG = "SummarizerService"
    private val CHANNEL_ID = "SummarizerServiceChannel"
    private val NOTIFICATION_ID = 1
    private lateinit var apiService: BackendApiService

    override fun onCreate() {
        super.onCreate()
        setupRetrofit()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val inputText = intent?.getStringExtra("INPUT_TEXT") ?: ""
        
        val notification = createNotification("Đang tóm tắt văn bản...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (inputText.isNotEmpty()) {
            SummarizerResultStore.isLoading = true
            summarizeText(inputText)
        } else {
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun setupRetrofit() {
        // Tăng timeout cho OkHttpClient để xử lý văn bản dài
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val baseUrl = "http://10.0.2.2:3000/"
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(BackendApiService::class.java)
    }

    private fun summarizeText(text: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "Gửi request tới API (Văn bản dài)...")
                val response = apiService.summarizeText(SummarizeRequest(text)).execute()
                
                if (response.isSuccessful) {
                    val body = response.body()
                    if (body != null && body.status == "success") {
                        val summary = body.data.summaryText
                        
                        // Cập nhật Store ngay lập tức
                        SummarizerResultStore.lastResult = summary
                        SummarizerResultStore.isSuccess = true
                        SummarizerResultStore.isLoading = false
                        
                        Log.d(TAG, "Tóm tắt thành công, đã lưu vào Store")
                        updateNotification("Tóm tắt hoàn tất", summary)
                        broadcastResult(summary, true)
                    } else {
                        val status = body?.status ?: "unknown"
                        SummarizerResultStore.lastResult = "Server trả về lỗi: $status"
                        SummarizerResultStore.isSuccess = false
                        SummarizerResultStore.isLoading = false
                        broadcastResult("Lỗi Server: $status", false)
                    }
                } else {
                    val errorCode = response.code()
                    SummarizerResultStore.lastResult = "Lỗi API: $errorCode"
                    SummarizerResultStore.isSuccess = false
                    SummarizerResultStore.isLoading = false
                    Log.e(TAG, "Lỗi API: $errorCode")
                    broadcastResult("Lỗi API ($errorCode)", false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ngoại lệ: ${e.message}")
                SummarizerResultStore.lastResult = "Lỗi kết nối: ${e.message}"
                SummarizerResultStore.isSuccess = false
                SummarizerResultStore.isLoading = false
                broadcastResult("Lỗi kết nối", false)
            }
        }
    }

    private fun broadcastResult(result: String, isSuccess: Boolean) {
        Handler(Looper.getMainLooper()).post {
            val intent = Intent("com.smartscanner.SUMMARIZATION_RESULT")
            intent.putExtra("RESULT", result)
            intent.putExtra("IS_SUCCESS", isSuccess)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Summarizer Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String, subText: String? = null): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Smart Scanner")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(Notification.DEFAULT_ALL)
            .setAutoCancel(true)
        
        if (subText != null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(subText))
        }

        // Click vào notification để mở app (tùy chọn)
        val intent = Intent(this, com.smartscanner.ui.TextSummarizerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        builder.setContentIntent(pendingIntent)

        return builder.build()
    }

    private fun updateNotification(content: String, subText: String) {
        val notification = createNotification(content, subText)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
