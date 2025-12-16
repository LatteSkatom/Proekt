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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SessionManager {
    public enum Mode { GUEST, CLOUD }

    private static SessionManager instance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private final List<ListenerRegistration> listeners = new ArrayList<>();
    private Mode mode = Mode.GUEST;

    private SessionManager(Context context) {
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        firestore.setFirestoreSettings(settings);
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
}
