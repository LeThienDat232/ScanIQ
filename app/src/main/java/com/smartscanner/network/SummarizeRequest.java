package com.smartscanner.network;

import com.google.gson.annotations.SerializedName;

public class SummarizeRequest {
    @SerializedName("original_text")
    public final String originalText;

    public SummarizeRequest(String originalText) {
        this.originalText = originalText;
    }
}
