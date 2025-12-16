package com.example.proekt;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnalitikActivity extends AppCompatActivity {

    private static final String TAG = "AnalitikActivity";

    private TextView subCountTv;
    private TextView totalSumTv;
    private Button periodBtn;

    private final String[] PERIOD_LABELS = {"Месяц", "Неделя", "Год"};
    private int periodIndex = 0;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private ListenerRegistration subscriptionsListener;
    private final List<FirebaseSubscription> subscriptions = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.analitika);

        subCountTv = findViewById(R.id.subCount);
        totalSumTv = findViewById(R.id.totalSum);
        periodBtn = findViewById(R.id.periodBtn);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        Button addButton = findViewById(R.id.add_button);
        addButton.setOnClickListener(v -> startActivity(new Intent(AnalitikActivity.this, AddActivity.class)));

        Button subButton = findViewById(R.id.sub_button);
        subButton.setOnClickListener(v -> startActivity(new Intent(AnalitikActivity.this, MainActivity.class)));

        ShapeableImageView settingsbutton = findViewById(R.id.settingsbutt);
        settingsbutton.setOnClickListener(v -> startActivity(new Intent(AnalitikActivity.this, Seting_activity.class)));

        periodBtn.setText(PERIOD_LABELS[periodIndex]);
        periodBtn.setOnClickListener(v -> {
            periodIndex = (periodIndex + 1) % PERIOD_LABELS.length;
            periodBtn.setText(PERIOD_LABELS[periodIndex]);
            updateAnalytics();
        });

        signInIfNeeded();
    }

    @Override
    protected void onDestroy() {
        if (subscriptionsListener != null) {
            subscriptionsListener.remove();
        }
        super.onDestroy();
    }

    private void signInIfNeeded() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            auth.signInAnonymously()
                    .addOnSuccessListener(result -> listenSubscriptions())
                    .addOnFailureListener(e -> Log.e(TAG, "Auth failed", e));
        } else {
            listenSubscriptions();
        }
    }

    private void listenSubscriptions() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        subscriptionsListener = firestore.collection("users")
                .document(currentUser.getUid())
                .collection("subscriptions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "load error", e);
                        return;
                    }
                    if (snapshot == null) return;

                    subscriptions.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        FirebaseSubscription sub = doc.toObject(FirebaseSubscription.class);
                        if (sub != null) {
                            sub.id = doc.getId();
                            subscriptions.add(sub);
                        }
                    }
                    updateAnalytics();
                });
    }

    private void updateAnalytics() {
        int count = subscriptions.size();
        double monthSum = 0.0;

        for (FirebaseSubscription sub : subscriptions) {
            monthSum += sub.cost;
        }

        subCountTv.setText("Подписок: " + count);

        double result;
        if (PERIOD_LABELS[periodIndex].equals("Неделя")) {
            result = monthSum / 4.345;
        } else if (PERIOD_LABELS[periodIndex].equals("Год")) {
            result = monthSum * 12.0;
        } else {
            result = monthSum;
        }

        totalSumTv.setText(String.format(Locale.getDefault(), "Сумма: %.2f ₽", result));
    }
}
