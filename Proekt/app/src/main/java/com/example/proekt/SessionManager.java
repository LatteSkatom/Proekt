package com.example.proekt;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class SessionManager {
    public enum Mode { GUEST, CLOUD }

    private static SessionManager instance;
    private final Context appContext;
    private final SharedPreferences prefs;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    // Acts as the offline cache of the latest known subscriptions (for guests and
    // authenticated users). Firestore is *not* the source of truth while offline;
    // this list is persisted to disk and reloaded on app restart when there is no
    // connectivity.
    private final List<FirebaseSubscription> localSubscriptions = new ArrayList<>();
    private final List<FirebaseSubscription> pendingCloudSubscriptions = new ArrayList<>();
    private final List<String> pendingCloudDeletions = new ArrayList<>();
    private Mode mode = Mode.GUEST;

    private SessionManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences("session_cache", Context.MODE_PRIVATE);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                // Firestore caching is disabled because offline writes must be handled locally
                // (SharedPreferences/Room) and synced only when the network is available.
                .setPersistenceEnabled(false)
                .build();
        firestore.setFirestoreSettings(settings);
        loadLocalSubscriptions();
        loadPendingCloudSubscriptions();
        loadPendingCloudDeletions();
        initializeMode();
        registerNetworkCallback();
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context.getApplicationContext());
        }
        return instance;
    }

    public Mode getMode() {
        return mode;
    }

    public FirebaseAuth getAuth() {
        return auth;
    }

    public FirebaseFirestore getFirestore() {
        return firestore;
    }

    public List<FirebaseSubscription> getPendingCloudSubscriptions() {
        return new ArrayList<>(pendingCloudSubscriptions);
    }

    public List<String> getPendingCloudDeletions() {
        return new ArrayList<>(pendingCloudDeletions);
    }

    public void markDeletionSynced(String subscriptionId) {
        if (subscriptionId == null) return;
        if (pendingCloudDeletions.remove(subscriptionId)) {
            persistPendingCloudDeletions();
        }
    }

    public void registerListener(ListenerRegistration registration) {
        if (registration != null) {
            listeners.add(registration);
        }
    }

    public void clearListeners() {
        for (ListenerRegistration registration : listeners) {
            try {
                registration.remove();
            } catch (Exception ignored) {
            }
        }
        listeners.clear();
    }

    public void enterGuestMode() {
        mode = Mode.GUEST;
        clearListeners();
    }

    public void enableCloudMode(FirebaseUser user) {
        if (user == null) return;
        mode = Mode.CLOUD;
        if (!hasNetworkConnection()) {
            // Offline cloud mode defers any Firestore work until connectivity returns.
            return;
        }
        ensureUserDocument(user);
        syncPendingCloudSubscriptions();
    }

    public void signOutToGuest() {
        clearListeners();
        auth.signOut();
        enterGuestMode();
    }

    public List<FirebaseSubscription> getLocalSubscriptions() {
        return new ArrayList<>(localSubscriptions);
    }

    /**
     * Replaces the locally cached subscriptions with the latest data that was
     * fetched from Firestore while online. This guarantees the cache survives
     * process death and is the only source used when the app restarts offline.
     */
    public void replaceLocalSubscriptions(List<FirebaseSubscription> subscriptions) {
        localSubscriptions.clear();
        if (subscriptions != null) {
            localSubscriptions.addAll(subscriptions);
        }
        persistLocalSubscriptions();
    }

    public void addLocalSubscription(FirebaseSubscription subscription) {
        if (subscription != null) {
            localSubscriptions.add(0, subscription);
            persistLocalSubscriptions();
        }
    }

    public interface SubscriptionSaveCallback {
        void onSuccess(boolean syncedImmediately);

        void onError(String message);
    }

    public void saveCloudSubscription(FirebaseSubscription subscription, SubscriptionSaveCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onError("Авторизуйтесь");
            return;
        }

        if (!hasNetworkConnection()) {
            // Firestore calls are skipped offline; we cache locally and sync once connectivity returns.
            addPendingCloudSubscription(subscription);
            callback.onSuccess(false);
            return;
        }

        Map<String, Object> data = buildFirestoreData(subscription);
        firestore.collection("users")
                .document(user.getUid())
                .collection("subscriptions")
                .add(data)
                .addOnSuccessListener(r -> {
                    // Persist to local cache immediately so the item survives
                    // app restarts even if the device later goes offline and
                    // Firestore cannot be queried again until connectivity
                    // returns.
                    cacheLocalSubscription(subscription);
                    callback.onSuccess(true);
                })
                .addOnFailureListener(e -> {
                    addPendingCloudSubscription(subscription);
                    callback.onSuccess(false);
                });
    }

    public void removeLocalSubscription(int position) {
        if (position >= 0 && position < localSubscriptions.size()) {
            localSubscriptions.remove(position);
            persistLocalSubscriptions();
        }
    }

    /**
     * Removes a subscription from the local cache using its Firestore id when
     * available. This keeps local state authoritative and ensures UI renders
     * exclusively from the cached source even while offline.
     */
    public void removeLocalSubscriptionById(String subscriptionId, FirebaseSubscription fallback) {
        if (subscriptionId != null) {
            Iterator<FirebaseSubscription> iterator = localSubscriptions.iterator();
            while (iterator.hasNext()) {
                FirebaseSubscription next = iterator.next();
                if (subscriptionId.equals(next.id)) {
                    iterator.remove();
                    persistLocalSubscriptions();
                    return;
                }
            }
        }
        // If there is no stable id (e.g., pending additions) fall back to
        // content-based comparison to keep cache aligned with the UI.
        if (fallback != null) {
            Iterator<FirebaseSubscription> iterator = localSubscriptions.iterator();
            while (iterator.hasNext()) {
                if (sameSubscription(iterator.next(), fallback)) {
                    iterator.remove();
                    persistLocalSubscriptions();
                    return;
                }
            }
        }
    }

    public boolean hasNetworkConnection() {
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    public void syncPendingCloudSubscriptions() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || !hasNetworkConnection()) return;

        if (!pendingCloudDeletions.isEmpty()) {
            List<String> deletions = new ArrayList<>(pendingCloudDeletions);
            for (String id : deletions) {
                firestore.collection("users")
                        .document(user.getUid())
                        .collection("subscriptions")
                        .document(id)
                        .delete()
                        .addOnSuccessListener(r -> {
                            pendingCloudDeletions.remove(id);
                            persistPendingCloudDeletions();
                        })
                        .addOnFailureListener(e -> Log.w("SessionManager", "syncDeletion", e));
            }
        }

        if (pendingCloudSubscriptions.isEmpty()) return;

        List<FirebaseSubscription> toSync = new ArrayList<>(pendingCloudSubscriptions);
        for (FirebaseSubscription sub : toSync) {
            Map<String, Object> data = buildFirestoreData(sub);
            firestore.collection("users")
                    .document(user.getUid())
                    .collection("subscriptions")
                    .add(data)
                    .addOnSuccessListener(r -> {
                        pendingCloudSubscriptions.remove(sub);
                        persistPendingCloudSubscriptions();
                    })
                    .addOnFailureListener(e -> Log.w("SessionManager", "syncPending", e));
        }
    }

    /**
     * Queues a remove operation so it can be replayed against Firestore once
     * connectivity returns. The deletion is persisted locally immediately to
     * honor the offline-first contract; Firestore is only updated later when
     * online because it does not reliably serve data after process death while
     * offline.
     */
    public void queuePendingDeletion(String subscriptionId, FirebaseSubscription subscription) {
        if (subscriptionId == null) {
            removeMatchingPendingAdd(subscription);
            return;
        }
        pendingCloudDeletions.add(subscriptionId);
        persistPendingCloudDeletions();
    }

    private void ensureUserDocument(FirebaseUser user) {
        if (!hasNetworkConnection()) return;
        DocumentReference ref = firestore.collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) return;
            Map<String, Object> data = new HashMap<>();
            data.put("email", user.getEmail());
            data.put("login", null);
            data.put("name", user.getDisplayName() != null ? user.getDisplayName() : "Гость");
            data.put("avatarUrl", null);
            data.put("createdAt", FieldValue.serverTimestamp());
            ref.set(data);
        });
    }

    private void initializeMode() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            enableCloudMode(currentUser);
        } else {
            enterGuestMode();
        }
    }

    private void persistLocalSubscriptions() {
        try {
            JSONArray array = new JSONArray();
            for (FirebaseSubscription sub : localSubscriptions) {
                array.put(toJson(sub));
            }
            prefs.edit().putString("local_subscriptions", array.toString()).apply();
        } catch (Exception e) {
            Log.e("SessionManager", "persistLocalSubscriptions", e);
        }
    }

    private void persistPendingCloudSubscriptions() {
        try {
            JSONArray array = new JSONArray();
            for (FirebaseSubscription sub : pendingCloudSubscriptions) {
                array.put(toJson(sub));
            }
            prefs.edit().putString("pending_additions", array.toString()).apply();
        } catch (Exception e) {
            Log.e("SessionManager", "persistPendingCloudSubscriptions", e);
        }
    }

    private void persistPendingCloudDeletions() {
        try {
            JSONArray array = new JSONArray();
            for (String id : pendingCloudDeletions) {
                array.put(id);
            }
            prefs.edit().putString("pending_deletions", array.toString()).apply();
        } catch (Exception e) {
            Log.e("SessionManager", "persistPendingCloudDeletions", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadLocalSubscriptions() {
        localSubscriptions.clear();
        String raw = prefs.getString("local_subscriptions", null);
        if (raw == null) return;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                FirebaseSubscription sub = fromJson(array.getJSONObject(i));
                if (sub != null) localSubscriptions.add(sub);
            }
        } catch (Exception e) {
            Log.e("SessionManager", "loadLocalSubscriptions", e);
            prefs.edit().remove("local_subscriptions").apply();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadPendingCloudSubscriptions() {
        pendingCloudSubscriptions.clear();
        String raw = prefs.getString("pending_additions", null);
        if (raw == null) return;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                FirebaseSubscription sub = fromJson(array.getJSONObject(i));
                if (sub != null) pendingCloudSubscriptions.add(sub);
            }
        } catch (Exception e) {
            Log.e("SessionManager", "loadPending", e);
            prefs.edit().remove("pending_additions").apply();
        }
    }

    @SuppressWarnings("unchecked")
    private void loadPendingCloudDeletions() {
        pendingCloudDeletions.clear();
        String raw = prefs.getString("pending_deletions", null);
        if (raw == null) return;
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                pendingCloudDeletions.add(array.getString(i));
            }
        } catch (Exception e) {
            Log.e("SessionManager", "loadPendingDeletions", e);
            prefs.edit().remove("pending_deletions").apply();
        }
    }

    private void addPendingCloudSubscription(FirebaseSubscription subscription) {
        if (subscription != null) {
            pendingCloudSubscriptions.add(0, subscription);
            persistPendingCloudSubscriptions();
        }
    }

    /**
     * Stores a subscription in the offline cache, removing any duplicate entry
     * by content. This is used after confirmed online writes so the cache is
     * immediately durable across restarts.
     */
    private void cacheLocalSubscription(FirebaseSubscription subscription) {
        if (subscription == null) return;
        Iterator<FirebaseSubscription> iterator = localSubscriptions.iterator();
        while (iterator.hasNext()) {
            if (sameSubscription(iterator.next(), subscription)) {
                iterator.remove();
                break;
            }
        }
        localSubscriptions.add(0, subscription);
        persistLocalSubscriptions();
    }

    private void removeMatchingPendingAdd(FirebaseSubscription subscription) {
        if (subscription == null) return;
        Iterator<FirebaseSubscription> iterator = pendingCloudSubscriptions.iterator();
        while (iterator.hasNext()) {
            FirebaseSubscription next = iterator.next();
            if (sameSubscription(next, subscription)) {
                iterator.remove();
                persistPendingCloudSubscriptions();
                break;
            }
        }
    }

    private Map<String, Object> buildFirestoreData(FirebaseSubscription subscription) {
        Map<String, Object> data = new HashMap<>();
        data.put("serviceName", subscription.serviceName);
        data.put("cost", subscription.cost);
        data.put("frequency", subscription.frequency);
        data.put("nextPaymentDate", subscription.nextPaymentDate);
        data.put("isActive", subscription.isActive);
        data.put("createdAt", FieldValue.serverTimestamp());
        return data;
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

    private JSONObject toJson(FirebaseSubscription sub) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("id", sub.id);
            obj.put("serviceName", sub.serviceName);
            obj.put("cost", sub.cost);
            obj.put("frequency", sub.frequency);
            obj.put("nextPaymentDate", sub.nextPaymentDate);
            obj.put("isActive", sub.isActive);
            obj.put("createdAt", sub.createdAt != null ? sub.createdAt.toDate().getTime() : JSONObject.NULL);
            return obj;
        } catch (Exception e) {
            Log.e("SessionManager", "toJson", e);
            return null;
        }
    }

    private FirebaseSubscription fromJson(JSONObject obj) {
        try {
            String id = obj.optString("id", null);
            String serviceName = obj.optString("serviceName", null);
            double cost = obj.optDouble("cost", 0);
            String frequency = obj.optString("frequency", null);
            String nextDate = obj.optString("nextPaymentDate", null);
            boolean isActive = obj.optBoolean("isActive", true);
            long createdAt = obj.optLong("createdAt", -1);
            Timestamp ts = createdAt > 0 ? new Timestamp(new java.util.Date(createdAt)) : null;
            FirebaseSubscription subscription = new FirebaseSubscription(serviceName, cost, frequency, nextDate, isActive, ts);
            subscription.id = id;
            return subscription;
        } catch (Exception e) {
            Log.e("SessionManager", "fromJson", e);
            return null;
        }
    }

    private void registerNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        try {
            cm.registerNetworkCallback(new NetworkRequest.Builder().build(), new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(Network network) {
                    // Firestore writes are executed only after connectivity is restored.
                    syncPendingCloudSubscriptions();
                }
            });
        } catch (Exception e) {
            Log.w("SessionManager", "registerNetworkCallback", e);
        }
    }
}
