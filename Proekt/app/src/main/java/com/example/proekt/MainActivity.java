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
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
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
import java.util.Collections;
import java.util.Comparator;
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
    private final List<FirebaseSubscription> allSubscriptions = new ArrayList<>();
    private final List<String> subscriptionIds = new ArrayList<>();
    private ListenerRegistration subscriptionRegistration;
    private SortMode sortMode = SortMode.CREATED_DESC;
    private FilterMode filterMode = FilterMode.ALL;

    private enum SortMode {
        CREATED_DESC,
        NAME_ASC,
        NAME_DESC,
        COST_ASC,
        COST_DESC,
        DATE_ASC,
        DATE_DESC
    }

    private enum FilterMode {
        ALL,
        ACTIVE,
        INACTIVE
    }

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

        ImageButton sortButton = findViewById(R.id.sort_button);
        sortButton.setOnClickListener(this::showSortMenu);


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
                    applyFiltersAndSorting();
                    scheduleNotifications(allSubscriptions);
                });
        sessionManager.registerListener(subscriptionRegistration);
    }

    private void detachListener() {
        cancelNotifications(new ArrayList<>(allSubscriptions));
        subscriptionList.clear();
        allSubscriptions.clear();
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
        ShapeableImageView cancelButton = dialog.findViewById(R.id.cancel_delete_button);
        ShapeableImageView confirmButton = dialog.findViewById(R.id.confirm_delete_button);

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
        applyFiltersAndSorting();
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
        allSubscriptions.clear();
        subscriptionIds.clear();
        allSubscriptions.addAll(sessionManager.getLocalSubscriptions());
        syncSubscriptionIds(allSubscriptions);
        applyFiltersAndSorting();
        scheduleNotifications(allSubscriptions);
    }

    private void loadCachedCloudSubscriptions() {
        allSubscriptions.clear();
        subscriptionIds.clear();
        allSubscriptions.addAll(sessionManager.getLocalSubscriptions());
        syncSubscriptionIds(allSubscriptions);
        applyFiltersAndSorting();
        scheduleNotifications(allSubscriptions);
    }

    private void refreshLocalCloudList() {
        allSubscriptions.clear();
        subscriptionIds.clear();
        allSubscriptions.addAll(sessionManager.getLocalSubscriptions());
        syncSubscriptionIds(allSubscriptions);
    }

    private void mergePendingSubscriptions() {
        List<FirebaseSubscription> pending = sessionManager.getPendingCloudSubscriptions();
        if (pending.isEmpty()) return;

        for (FirebaseSubscription pendingSub : pending) {
            boolean alreadyPresent = false;
            for (FirebaseSubscription existing : allSubscriptions) {
                if (sameSubscription(existing, pendingSub)) {
                    alreadyPresent = true;
                    break;
                }
            }
            if (!alreadyPresent) {
                allSubscriptions.add(0, pendingSub);
            }
        }
        applyFiltersAndSorting();
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

    private void syncSubscriptionIds(List<FirebaseSubscription> source) {
        for (FirebaseSubscription sub : source) {
            if (sub.id != null && !subscriptionIds.contains(sub.id)) {
                subscriptionIds.add(sub.id);
            }
        }
    }

    private void applyFiltersAndSorting() {
        subscriptionList.clear();
        for (FirebaseSubscription sub : allSubscriptions) {
            if (filterMode == FilterMode.ACTIVE && !sub.isActive) {
                continue;
            }
            if (filterMode == FilterMode.INACTIVE && sub.isActive) {
                continue;
            }
            subscriptionList.add(sub);
        }

        Comparator<FirebaseSubscription> comparator = getComparatorForSortMode();
        if (comparator != null) {
            Collections.sort(subscriptionList, comparator);
        }
        adapter.notifyDataSetChanged();
    }

    private Comparator<FirebaseSubscription> getComparatorForSortMode() {
        switch (sortMode) {
            case NAME_ASC:
                return Comparator.comparing(sub -> safeString(sub.serviceName), String.CASE_INSENSITIVE_ORDER);
            case NAME_DESC:
                return Comparator.comparing((FirebaseSubscription sub) -> safeString(sub.serviceName),
                        String.CASE_INSENSITIVE_ORDER).reversed();
            case COST_ASC:
                return Comparator.comparingDouble(sub -> sub.cost);
            case COST_DESC:
                return (a, b) -> Double.compare(b.cost, a.cost);
            case DATE_ASC:
                return Comparator.comparing(sub -> safeString(sub.nextPaymentDate));
            case DATE_DESC:
                return Comparator.comparing((FirebaseSubscription sub) -> safeString(sub.nextPaymentDate)).reversed();
            case CREATED_DESC:
                return (a, b) -> compareTimestamps(b.createdAt, a.createdAt);
            default:
                return null;
        }
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private int compareTimestamps(com.google.firebase.Timestamp a, com.google.firebase.Timestamp b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        return a.compareTo(b);
    }

    private void showSortMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.menu_sort_subscriptions, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this::onSortMenuItemSelected);
        popupMenu.show();
    }

    private boolean onSortMenuItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.sort_created_desc) {
            sortMode = SortMode.CREATED_DESC;
        } else if (itemId == R.id.sort_name_asc) {
            sortMode = SortMode.NAME_ASC;
        } else if (itemId == R.id.sort_name_desc) {
            sortMode = SortMode.NAME_DESC;
        } else if (itemId == R.id.sort_cost_asc) {
            sortMode = SortMode.COST_ASC;
        } else if (itemId == R.id.sort_cost_desc) {
            sortMode = SortMode.COST_DESC;
        } else if (itemId == R.id.sort_date_asc) {
            sortMode = SortMode.DATE_ASC;
        } else if (itemId == R.id.sort_date_desc) {
            sortMode = SortMode.DATE_DESC;
        } else {
            return false;
        }
        applyFiltersAndSorting();
        return true;
    }

    private void showFilterMenu(View anchor) {
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.menu_filter_subscriptions, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this::onFilterMenuItemSelected);
        popupMenu.show();
    }

    private boolean onFilterMenuItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.filter_all) {
            filterMode = FilterMode.ALL;
        } else if (itemId == R.id.filter_active) {
            filterMode = FilterMode.ACTIVE;
        } else if (itemId == R.id.filter_inactive) {
            filterMode = FilterMode.INACTIVE;
        } else {
            return false;
        }
        applyFiltersAndSorting();
        return true;
    }
}
