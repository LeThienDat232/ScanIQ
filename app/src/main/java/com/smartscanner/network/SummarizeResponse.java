package com.smartscanner.network;

import com.google.gson.annotations.SerializedName;

public class SummarizeResponse {
    @SerializedName("status")
    public String status;

    @SerializedName("data")
    public SummarizeData data;
}
