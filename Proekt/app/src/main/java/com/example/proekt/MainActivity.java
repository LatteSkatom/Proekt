package com.example.proekt;

import android.app.AlarmManager;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int ADD_REQUEST = 1001;
    private static final int LOGIN_REQUEST = 2001;
    private static final String TAG = "MainActivity";

    private SessionManager sessionManager;
    private RecyclerView recyclerView;
    private SubscriptionAdapter adapter;
    private final List<FirebaseSubscription> subscriptionList = new ArrayList<>();
    private final List<String> subscriptionIds = new ArrayList<>();
    private ListenerRegistration subscriptionRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionManager = SessionManager.getInstance(this);

        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SubscriptionAdapter(subscriptionList, this::onSubscriptionLongClick);
        recyclerView.setAdapter(adapter);

        Button addButton = findViewById(R.id.add_button);
        addButton.setOnClickListener(v -> startActivityForResult(new Intent(this, AddActivity.class), ADD_REQUEST));

        ShapeableImageView settingsButton = findViewById(R.id.settingsbutt);
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, Seting_activity.class)));

        Button analitikButton = findViewById(R.id.Analit_button);
        analitikButton.setOnClickListener(v -> {
            startActivity(new Intent(this, AnalitikActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMode();
    }

    @Override
    protected void onDestroy() {
        detachListener();
        super.onDestroy();
    }

    private void refreshMode() {
        if (sessionManager.getMode() == SessionManager.Mode.CLOUD) {
            if (sessionManager.hasNetworkConnection()) {
                attachListener();
                mergePendingSubscriptions();
                sessionManager.syncPendingCloudSubscriptions();
            } else {
                // Firestore listeners are not used while offline to avoid relying on its caching.
                // We rely solely on the locally persisted cache populated during the last
                // online session; Firestore is not available after process death when offline.
                detachListener();
                loadCachedCloudSubscriptions();
                mergePendingSubscriptions();
                adapter.notifyDataSetChanged();
            }
        } else {
            detachListener();
            loadGuestSubscriptions();
        }
    }

    private void attachListener() {
        FirebaseUser user = sessionManager.getAuth().getCurrentUser();
        if (user == null || !sessionManager.hasNetworkConnection()) return;
        detachListener();
        subscriptionRegistration = sessionManager.getFirestore()
                .collection("users")
                .document(user.getUid())
                .collection("subscriptions")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null) return;
                    List<FirebaseSubscription> cloudSubscriptions = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        FirebaseSubscription sub = doc.toObject(FirebaseSubscription.class);
                        if (sub != null) {
                            sub.id = doc.getId();
                            cloudSubscriptions.add(sub);
                        }
                    }
                    // Persist the last known online state locally. UI will be refreshed from
                    // the cache to avoid rendering Firestore data directly, honoring the
                    // offline-first deletion contract.
                    sessionManager.replaceLocalSubscriptions(cloudSubscriptions);
                    loadCachedCloudSubscriptions();
                    mergePendingSubscriptions();
                    adapter.notifyDataSetChanged();
                    scheduleNotifications(subscriptionList);
                });
        sessionManager.registerListener(subscriptionRegistration);
    }

    private void detachListener() {
        cancelNotifications(new ArrayList<>(subscriptionList));
        subscriptionList.clear();
        subscriptionIds.clear();
        adapter.notifyDataSetChanged();
        if (subscriptionRegistration != null) {
            subscriptionRegistration.remove();
            subscriptionRegistration = null;
        }
    }

    private void onSubscriptionLongClick(FirebaseSubscription subscription, int position) {
        showDeleteSubscriptionDialog(subscription, position);
    }

    private void showDeleteSubscriptionDialog(FirebaseSubscription subscription, int position) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_confirm_subscription_delete);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.CENTER;
            window.setDimAmount(0.6f);
            window.setAttributes(params);
        }

        TextView confirmationMessage = dialog.findViewById(R.id.delete_confirmation_message);
        TextView feedbackText = dialog.findViewById(R.id.delete_feedback_text);
        Button cancelButton = dialog.findViewById(R.id.cancel_delete_button);
        Button confirmButton = dialog.findViewById(R.id.confirm_delete_button);

        confirmationMessage.setText("Удалить \"" + subscription.serviceName + "\"?");

        cancelButton.setOnClickListener(v -> dialog.dismiss());
        confirmButton.setOnClickListener(v -> deleteSubscription(position, dialog, feedbackText));

        dialog.setCancelable(true);
        dialog.show();
    }

    private void deleteSubscription(int position, Dialog dialog, TextView feedbackText) {
        boolean isGuest = sessionManager.getMode() == SessionManager.Mode.GUEST;
        if (position < 0 || position >= subscriptionList.size()) return;

        FirebaseSubscription sub = subscriptionList.get(position);

        if (isGuest) {
            sessionManager.removeLocalSubscription(position);
            loadGuestSubscriptions();
            cancelNotifications(java.util.Collections.singletonList(sub));
            dismissWithFeedback(feedbackText, dialog, "Подписка удалена");
            return;
        }

        String subscriptionId = sub.id;
        boolean online = sessionManager.hasNetworkConnection();

        // Remove from local cache immediately so the UI is always driven by local state
        // and remains accurate after offline restarts. Firestore will be updated later.
        sessionManager.removeLocalSubscriptionById(subscriptionId, sub);
        sessionManager.queuePendingDeletion(subscriptionId, sub);
        refreshLocalCloudList();
        adapter.notifyDataSetChanged();
        cancelNotifications(java.util.Collections.singletonList(sub));

        if (!online) {
            dismissWithFeedback(feedbackText, dialog, "Удаление сохранено офлайн");
            return;
        }

        FirebaseUser user = sessionManager.getAuth().getCurrentUser();
        if (user == null) return;

        if (subscriptionId == null) {
            // No Firestore id yet (likely a pending addition). Sync queue will skip it.
            dismissWithFeedback(feedbackText, dialog, "Подписка удалена локально");
            return;
        }

        sessionManager.getFirestore()
                .collection("users")
                .document(user.getUid())
                .collection("subscriptions")
                .document(subscriptionId)
                .delete()
                .addOnSuccessListener(v -> {
                    // Remove from queue once the cloud deletion succeeds.
                    sessionManager.markDeletionSynced(subscriptionId);
                    sessionManager.syncPendingCloudSubscriptions();
                    dismissWithFeedback(feedbackText, dialog, "Подписка удалена");
                })
                .addOnFailureListener(e -> {
                    dismissWithFeedback(feedbackText, dialog, "Удаление будет выполнено после подключения");
                });
    }

    private void dismissWithFeedback(TextView feedbackText, Dialog dialog, String message) {
        if (feedbackText != null) {
            feedbackText.setText(message);
            feedbackText.postDelayed(dialog::dismiss, 600);
            return;
        }
        dialog.dismiss();
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
                if (triggerTime <= System.currentTimeMillis()) continue;
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
            } catch (Exception ex) {
                Log.e(TAG, "schedule error", ex);
            }
        }
    }

    private void cancelNotifications(List<FirebaseSubscription> subscriptions) {
        for (FirebaseSubscription sub : subscriptions) {
            try {
                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        this,
                        getRequestCode(sub),
                        buildNotificationIntent(sub),
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
                Log.e(TAG, "cancel error", ex);
            }
        }
    }

    private Intent buildNotificationIntent(FirebaseSubscription sub) {
        Intent intent = new Intent(this, NotificationReceiver.class);
        intent.putExtra("service_name", sub.serviceName);
        intent.putExtra("cost", String.valueOf(sub.cost));
        return intent;
    }

    private int getRequestCode(FirebaseSubscription sub) {
        String key = sub.id != null ? sub.id : sub.serviceName + sub.nextPaymentDate;
        return Math.abs(key.hashCode());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADD_REQUEST && resultCode == RESULT_OK && sessionManager.getMode() == SessionManager.Mode.GUEST) {
            loadGuestSubscriptions();
        } else if (requestCode == LOGIN_REQUEST && resultCode == RESULT_OK) {
            refreshMode();
        } else if (requestCode == ADD_REQUEST && resultCode == RESULT_OK) {
            refreshMode();
        }
    }

    private void loadGuestSubscriptions() {
        subscriptionList.clear();
        subscriptionIds.clear();
        subscriptionList.addAll(sessionManager.getLocalSubscriptions());
        for (FirebaseSubscription sub : subscriptionList) {
            if (sub.id != null && !subscriptionIds.contains(sub.id)) {
                subscriptionIds.add(sub.id);
            }
        }
        adapter.notifyDataSetChanged();
        scheduleNotifications(subscriptionList);
    }

    private void loadCachedCloudSubscriptions() {
        subscriptionList.clear();
        subscriptionIds.clear();
        subscriptionList.addAll(sessionManager.getLocalSubscriptions());
        for (FirebaseSubscription sub : subscriptionList) {
            if (sub.id != null && !subscriptionIds.contains(sub.id)) {
                subscriptionIds.add(sub.id);
            }
        }
        adapter.notifyDataSetChanged();
        scheduleNotifications(subscriptionList);
    }

    private void refreshLocalCloudList() {
        subscriptionList.clear();
        subscriptionIds.clear();
        subscriptionList.addAll(sessionManager.getLocalSubscriptions());
        for (FirebaseSubscription sub : subscriptionList) {
            if (sub.id != null && !subscriptionIds.contains(sub.id)) {
                subscriptionIds.add(sub.id);
            }
        }
    }

    private void mergePendingSubscriptions() {
        List<FirebaseSubscription> pending = sessionManager.getPendingCloudSubscriptions();
        if (pending.isEmpty()) return;

        for (FirebaseSubscription pendingSub : pending) {
            boolean alreadyPresent = false;
            for (FirebaseSubscription existing : subscriptionList) {
                if (sameSubscription(existing, pendingSub)) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                subscriptionList.add(0, pendingSub);
            }
        }
    }

    private boolean sameSubscription(FirebaseSubscription a, FirebaseSubscription b) {
        if (a == null || b == null) return false;
        if (Double.compare(a.cost, b.cost) != 0) return false;
        if (a.isActive != b.isActive) return false;
        if (!safeEquals(a.serviceName, b.serviceName)) return false;
        if (!safeEquals(a.frequency, b.frequency)) return false;
        return safeEquals(a.nextPaymentDate, b.nextPaymentDate);
    }

    private boolean safeEquals(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
}
