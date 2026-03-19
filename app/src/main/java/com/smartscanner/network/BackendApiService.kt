package com.smartscanner.network

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface BackendApiService {
    @POST("api/documents/summarize")
    fun summarizeText(@Body request: SummarizeRequest): Call<SummarizeResponse>
}
