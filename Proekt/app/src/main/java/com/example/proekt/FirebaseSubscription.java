package com.example.proekt;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Exclude;

import java.io.Serializable;

public class FirebaseSubscription implements Serializable {

    private static final long serialVersionUID = 1L;

    @Exclude
    public String id;

    public String serviceName;
    public double cost;
    public String frequency;
    public String nextPaymentDate;
    public boolean isActive;
    public Timestamp createdAt;

    public FirebaseSubscription() {
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
