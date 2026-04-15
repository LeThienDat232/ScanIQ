package com.smartscanner.service

object SummarizerResultStore {
    @Volatile var lastResult: String? = null
    @Volatile var isSuccess: Boolean = false
    @Volatile var isLoading: Boolean = false

    fun clear() {
        lastResult = null
        isSuccess = false
        isLoading = false
    }
}
