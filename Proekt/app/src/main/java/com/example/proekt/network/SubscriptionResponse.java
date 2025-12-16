package com.example.proekt.network;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public class SubscriptionResponse {
    @SerializedName("success")
    private boolean success;

    @SerializedName("message")
    private String message;

    @SerializedName("subscriptions")
    private List<Subscription> subscriptions;

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }

    public List<Subscription> getSubscriptions() {
        return subscriptions;
    }
}
