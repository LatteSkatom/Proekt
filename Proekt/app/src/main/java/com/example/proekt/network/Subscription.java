package com.example.proekt.network;

import com.google.gson.annotations.SerializedName;

public class Subscription {

    @SerializedName("service_name")
    private String servis;

    @SerializedName("cost")
    private double cost;

    @SerializedName("next_payment_date")
    private String nextPaymentDate;

    @SerializedName("is_active")
    private String isActive;

    @SerializedName("id_sub")
    private int idSub;

    @SerializedName("user_id")
    private int userId;

    // 🔥 Полный конструктор (исправляет твою ошибку)
    public Subscription(int idSub, String servis, double cost, String nextPaymentDate,
                        int userId, String isActive, String unused) {
        this.idSub = idSub;
        this.servis = servis;
        this.cost = cost;
        this.nextPaymentDate = nextPaymentDate;
        this.userId = userId;
        this.isActive = isActive;
    }

    public int getIdSub() { return idSub; }
    public String getServis() { return servis; }
    public double getCost() { return cost; }
    public String getNextPaymentDate() { return nextPaymentDate; }
    public String getIsActive() { return isActive; }
    public int getUserId() { return userId; }

    public void setServis(String servis) { this.servis = servis; }
    public void setCost(double cost) { this.cost = cost; }
    public void setNextPaymentDate(String date) { this.nextPaymentDate = date; }
    public void setUserId(int id) { this.userId = id; }
}
