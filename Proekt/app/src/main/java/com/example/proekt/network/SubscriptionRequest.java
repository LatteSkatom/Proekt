package com.example.proekt.network;

import com.google.gson.annotations.SerializedName;

public class SubscriptionRequest {
    @SerializedName("service_name")
    private String serviceName;

    @SerializedName("cost")
    private double cost;

    @SerializedName("next_payment_date")
    private String nextPaymentDate;

    @SerializedName("user_id")
    private int userId;

    public SubscriptionRequest(String serviceName, double cost, String nextPaymentDate, int userId) {
        this.serviceName = serviceName;
        this.cost = cost;
        this.nextPaymentDate = nextPaymentDate;
        this.userId = userId;
    }
}
