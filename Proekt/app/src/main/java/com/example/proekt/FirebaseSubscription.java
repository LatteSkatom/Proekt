package com.example.proekt;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

public class FirebaseSubscription {

    @Exclude
    public String id;

    public String serviceName;
    public double cost;
    public String frequency;
    public String nextPaymentDate;
    public boolean isActive;
    public Timestamp createdAt;

    public FirebaseSubscription() {
        // Обязательный пустой конструктор для Firestore
    }

    public FirebaseSubscription(String serviceName, double cost, String frequency,
                                 String nextPaymentDate, boolean isActive, Timestamp createdAt) {
        this.serviceName = serviceName;
        this.cost = cost;
        this.frequency = frequency;
        this.nextPaymentDate = nextPaymentDate;
        this.isActive = isActive;
        this.createdAt = createdAt;
    }
}
