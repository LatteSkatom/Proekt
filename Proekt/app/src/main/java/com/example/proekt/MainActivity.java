package com.example.proekt;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proekt.utils.ActivityTransitionUtils;
import com.example.proekt.utils.WindowUtils;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Collections;
import java.util.Locale;

/**
 * MainActivity с загрузкой подписок из Firestore и обновлением в реальном времени.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private RecyclerView recyclerView;
    private SubscriptionAdapter adapter;
    private final List<FirebaseSubscription> subscriptionList = new ArrayList<>();
    private final List<String> subscriptionIds = new ArrayList<>();

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private ListenerRegistration subscriptionRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WindowUtils.setupTransparentNavigationBar(this);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        firestore.setFirestoreSettings(settings);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubscriptionAdapter(subscriptionList, this::onSubscriptionLongClick);
        recyclerView.setAdapter(adapter);

        Button addButton = findViewById(R.id.add_button);
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddActivity.class);
            ActivityTransitionUtils.startActivityForResultWithSlide(this, intent, 1001);
        });

        ShapeableImageView settingsbutton = findViewById(R.id.settingsbutt);
        settingsbutton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Seting_activity.class);
            ActivityTransitionUtils.startActivityWithSlide(this, intent);
        });

        Button analitikbutton = findViewById(R.id.Analit_button);
        analitikbutton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AnalitikActivity.class);
            ActivityTransitionUtils.startActivityWithSlide(this, intent);
        });

        signInIfNeeded();
    }

    @Override
    protected void onDestroy() {
        if (subscriptionRegistration != null) {
            subscriptionRegistration.remove();
        }
        super.onDestroy();
    }

    private void signInIfNeeded() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            auth.signInAnonymously()
                    .addOnSuccessListener(result -> {
                        ensureUserDocument(result.getUser());
                        listenSubscriptions();
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Auth error", e));
        } else {
            ensureUserDocument(currentUser);
            listenSubscriptions();
        }
    }

    private void listenSubscriptions() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        List<FirebaseSubscription> previousSubscriptions = new ArrayList<>(subscriptionList);
        cancelNotifications(previousSubscriptions);

        subscriptionRegistration = firestore.collection("users")
                .document(currentUser.getUid())
                .collection("subscriptions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Ошибка загрузки подписок", e);
                        return;
                    }

                    if (snapshot == null) return;

                    subscriptionList.clear();
                    subscriptionIds.clear();

                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        FirebaseSubscription subscription = doc.toObject(FirebaseSubscription.class);
                        if (subscription != null) {
                            subscription.id = doc.getId();
                            subscriptionList.add(subscription);
                            subscriptionIds.add(doc.getId());
                        }
                    }

                    adapter.notifyDataSetChanged();
                    scheduleNotifications(subscriptionList);
                });
    }

    private void onSubscriptionLongClick(FirebaseSubscription subscription, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить подписку")
                .setMessage("Вы действительно хотите удалить \"" + subscription.serviceName + "\"?")
                .setPositiveButton("Удалить", (dialog, which) -> deleteSubscription(position))
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deleteSubscription(int position) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null || position < 0 || position >= subscriptionIds.size()) {
            Toast.makeText(this, "Не удалось удалить подписку", Toast.LENGTH_SHORT).show();
            return;
        }

        String docId = subscriptionIds.get(position);
        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("subscriptions")
                .document(docId)
                .delete()
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ошибка удаления подписки", e);
                    Toast.makeText(MainActivity.this, "Не удалось удалить подписку", Toast.LENGTH_SHORT).show();
                });
    }

    private void scheduleNotifications(List<FirebaseSubscription> subscriptions) {
        for (FirebaseSubscription sub : subscriptions) {
            try {
                if (!sub.isActive) continue;

                String nextPayment = sub.nextPaymentDate;
                if (nextPayment == null || nextPayment.isEmpty()) continue;

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = sdf.parse(nextPayment);
                if (date == null) continue;

                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                calendar.add(Calendar.DAY_OF_MONTH, -1);

                long triggerTime = calendar.getTimeInMillis();
                long now = System.currentTimeMillis();
                if (triggerTime <= now) continue;

                cancelNotifications(java.util.Collections.singletonList(sub));

                Intent intent = buildNotificationIntent(sub);
                int requestCode = getRequestCode(sub);

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        this,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                        }
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка при планировании уведомления", e);
            }
        }
    }

    private void cancelNotifications(List<FirebaseSubscription> subscriptions) {
        for (FirebaseSubscription sub : subscriptions) {
            try {
                int requestCode = getRequestCode(sub);
                Intent intent = buildNotificationIntent(sub);
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        this,
                        requestCode,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_NO_CREATE
                );

                if (pendingIntent != null) {
                    AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    if (alarmManager != null) {
                        alarmManager.cancel(pendingIntent);
                    }
                    pendingIntent.cancel();
                }
            } catch (Exception ex) {
                Log.e(TAG, "Ошибка отмены уведомления", ex);
            }
        }
    }

    private Intent buildNotificationIntent(FirebaseSubscription sub) {
        Intent intent = new Intent(this, NotificationReceiver.class);
        intent.setAction("SUBSCRIPTION_NOTIFICATION_" + (sub.id != null ? sub.id : sub.serviceName));
        intent.putExtra("service_name", sub.serviceName);
        intent.putExtra("cost", String.valueOf(sub.cost));
        return intent;
    }

    private int getRequestCode(FirebaseSubscription sub) {
        String key = sub.id != null ? sub.id : sub.serviceName + sub.nextPaymentDate;
        return Math.abs(key.hashCode());
    }

    private void ensureUserDocument(FirebaseUser user) {
        if (user == null) return;

        DocumentReference userRef = firestore.collection("users").document(user.getUid());
        userRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                userRef.set(newUserData())
                        .addOnSuccessListener(v -> Log.d(TAG, "Пользователь создан"))
                        .addOnFailureListener(e -> Log.e(TAG, "Ошибка создания пользователя", e));
            }
        }).addOnFailureListener(e -> Log.e(TAG, "Ошибка чтения пользователя", e));
    }

    private java.util.Map<String, Object> newUserData() {
        java.util.Map<String, Object> userData = new java.util.HashMap<>();
        userData.put("name", "Гость");
        userData.put("avatarUrl", null);
        userData.put("createdAt", FieldValue.serverTimestamp());
        return userData;
    }
}
