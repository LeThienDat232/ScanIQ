package com.smartscanner.network

import com.google.gson.annotations.SerializedName

data class SummarizeRequest(
    @SerializedName("original_text") val originalText: String
)

data class SummarizeResponse(
    @SerializedName("status") val status: String,
    @SerializedName("data") val data: SummarizeData
)

data class SummarizeData(
    @SerializedName("summary_text") val summaryText: String
)
