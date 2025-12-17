package com.example.proekt;

import android.content.Context;
import android.util.Log;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionManager {
    public enum Mode { GUEST, CLOUD }

    private static SessionManager instance;
    private final Context appContext;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private final List<FirebaseSubscription> localSubscriptions = new ArrayList<>();
    private Mode mode = Mode.GUEST;

    private SessionManager(Context context) {
        appContext = context.getApplicationContext();
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        firestore.setFirestoreSettings(settings);
        loadLocalSubscriptions();
        enterGuestMode();
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
        firestore.disableNetwork().addOnFailureListener(e -> Log.w("SessionManager", "disableNetwork", e));
    }

    public void enableCloudMode(FirebaseUser user) {
        if (user == null) return;
        mode = Mode.CLOUD;
        firestore.enableNetwork().addOnFailureListener(e -> Log.w("SessionManager", "enableNetwork", e));
        ensureUserDocument(user);
    }

    public void signOutToGuest() {
        clearListeners();
        auth.signOut();
        enterGuestMode();
    }

    public List<FirebaseSubscription> getLocalSubscriptions() {
        return new ArrayList<>(localSubscriptions);
    }

    public void addLocalSubscription(FirebaseSubscription subscription) {
        if (subscription != null) {
            localSubscriptions.add(0, subscription);
            persistLocalSubscriptions();
        }
    }

    public void removeLocalSubscription(int position) {
        if (position >= 0 && position < localSubscriptions.size()) {
            localSubscriptions.remove(position);
            persistLocalSubscriptions();
        }
    }

    private void ensureUserDocument(FirebaseUser user) {
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

    private File getLocalCacheFile() {
        return new File(appContext.getFilesDir(), "local_subscriptions.dat");
    }

    private void persistLocalSubscriptions() {
        File file = getLocalCacheFile();
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            List<CachedSubscription> cache = new ArrayList<>();
            for (FirebaseSubscription sub : localSubscriptions) {
                cache.add(new CachedSubscription(sub));
            }
            oos.writeObject(cache);
            oos.flush();
        } catch (Exception e) {
            Log.e("SessionManager", "persistLocalSubscriptions", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void loadLocalSubscriptions() {
        File file = getLocalCacheFile();
        if (!file.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            if (obj instanceof List<?>) {
                localSubscriptions.clear();
                for (Object item : (List<?>) obj) {
                    if (item instanceof CachedSubscription) {
                        localSubscriptions.add(((CachedSubscription) item).toSubscription());
                    }
                }
            }
        } catch (Exception e) {
            Log.e("SessionManager", "loadLocalSubscriptions", e);
            // reset corrupted cache to allow fresh saves to persist
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static class CachedSubscription implements Serializable {
        private static final long serialVersionUID = 1L;
        String serviceName;
        double cost;
        String frequency;
        String nextPaymentDate;
        boolean isActive;
        Long createdAtMillis;

        CachedSubscription(FirebaseSubscription sub) {
            this.serviceName = sub.serviceName;
            this.cost = sub.cost;
            this.frequency = sub.frequency;
            this.nextPaymentDate = sub.nextPaymentDate;
            this.isActive = sub.isActive;
            this.createdAtMillis = sub.createdAt != null ? sub.createdAt.toDate().getTime() : null;
        }

        FirebaseSubscription toSubscription() {
            Timestamp ts = createdAtMillis != null ? new Timestamp(new java.util.Date(createdAtMillis)) : null;
            return new FirebaseSubscription(serviceName, cost, frequency, nextPaymentDate, isActive, ts);
        }
    }
}
