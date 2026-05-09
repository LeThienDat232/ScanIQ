package com.smartscanner.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.smartscanner.network.BackendApiService;
import com.smartscanner.network.SummarizeRequest;
import com.smartscanner.network.SummarizeResponse;
import com.smartscanner.ui.TextSummarizerActivity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class SummarizerService extends Service {
    private static final String TAG = "SummarizerService";
    private static final String CHANNEL_ID = "SummarizerServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BackendApiService apiService;

    @Override
    public void onCreate() {
        super.onCreate();
        setupRetrofit();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        String inputText = intent != null ? intent.getStringExtra("INPUT_TEXT") : "";
        if (inputText == null) {
            inputText = "";
        }

        Notification notification = createNotification("Summarizing text...", null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if (!inputText.isEmpty()) {
            SummarizerResultStore.isLoading = true;
            summarizeText(inputText);
        } else {
            finishWork();
        }

        return START_NOT_STICKY;
    }

    private void setupRetrofit() {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("http://10.0.2.2:3000/")
                .client(okHttpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        apiService = retrofit.create(BackendApiService.class);
    }

    private void summarizeText(String text) {
        executor.execute(() -> {
            try {
                Log.d(TAG, "Sending summarize request");
                retrofit2.Response<SummarizeResponse> response =
                        apiService.summarizeText(new SummarizeRequest(text)).execute();

                if (response.isSuccessful()) {
                    SummarizeResponse body = response.body();
                    if (body != null && "success".equals(body.status) && body.data != null) {
                        String summary = body.data.summaryText;
                        SummarizerResultStore.lastResult = summary;
                        SummarizerResultStore.isSuccess = true;
                        SummarizerResultStore.isLoading = false;

                        updateNotification("Summarization complete", summary);
                        broadcastResult(summary, true);
                    } else {
                        String status = body != null ? body.status : "unknown";
                        String message = "Server error: " + status;
                        SummarizerResultStore.lastResult = message;
                        SummarizerResultStore.isSuccess = false;
                        SummarizerResultStore.isLoading = false;
                        broadcastResult(message, false);
                    }
                } else {
                    String message = "API error (" + response.code() + ")";
                    SummarizerResultStore.lastResult = message;
                    SummarizerResultStore.isSuccess = false;
                    SummarizerResultStore.isLoading = false;
                    Log.e(TAG, message);
                    broadcastResult(message, false);
                }
            } catch (Exception e) {
                Log.e(TAG, "Summarizer exception", e);
                String message = "Connection error: " + e.getMessage();
                SummarizerResultStore.lastResult = message;
                SummarizerResultStore.isSuccess = false;
                SummarizerResultStore.isLoading = false;
                broadcastResult("Connection error", false);
            } finally {
                finishWork();
            }
        });
    }

    private void broadcastResult(String result, boolean isSuccess) {
        new Handler(Looper.getMainLooper()).post(() -> {
            Intent intent = new Intent("com.smartscanner.SUMMARIZATION_RESULT");
            intent.putExtra("RESULT", result);
            intent.putExtra("IS_SUCCESS", isSuccess);
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Summarizer Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification(String content, @Nullable String subText) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Smart Scanner")
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(Notification.DEFAULT_ALL)
                .setAutoCancel(true);

        if (subText != null) {
            builder.setStyle(new NotificationCompat.BigTextStyle().bigText(subText));
        }

        Intent intent = new Intent(this, TextSummarizerActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
        );
        builder.setContentIntent(pendingIntent);

        return builder.build();
    }

    private void updateNotification(String content, String subText) {
        Notification notification = createNotification(content, subText);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void finishWork() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_DETACH);
        } else {
            stopForeground(false);
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        executor.shutdown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
