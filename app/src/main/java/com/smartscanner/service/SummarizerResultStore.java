package com.smartscanner.service;

import androidx.annotation.Nullable;

public final class SummarizerResultStore {
    @Nullable
    public static volatile String lastResult = null;
    public static volatile boolean isSuccess = false;
    public static volatile boolean isLoading = false;

    private SummarizerResultStore() {
    }

    public static void clear() {
        lastResult = null;
        isSuccess = false;
        isLoading = false;
    }
}
