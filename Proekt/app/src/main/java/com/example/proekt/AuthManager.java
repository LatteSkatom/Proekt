package com.example.proekt;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

import androidx.activity.result.ActivityResultLauncher;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class AuthManager {
    private final SessionManager sessionManager;
    private final FirebaseAuth auth;
    private final FirebaseFirestore firestore;
    private GoogleSignInClient googleSignInClient;

    public AuthManager(Context context) {
        sessionManager = SessionManager.getInstance(context);
        auth = sessionManager.getAuth();
        firestore = sessionManager.getFirestore();
        setupGoogleSignIn(context);
    }

    private void setupGoogleSignIn(Context context) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(context, gso);
    }

    public void startGoogle(Activity activity, ActivityResultLauncher<Intent> launcher) {
        Intent signInIntent = googleSignInClient.getSignInIntent();
        launcher.launch(signInIntent);
    }

    public void handleGoogleResult(Intent data, AuthCallback callback) {
        Task<GoogleSignInAccount> accountTask = GoogleSignIn.getSignedInAccountFromIntent(data);
        try {
            GoogleSignInAccount account = accountTask.getResult(ApiException.class);
            if (account == null) {
                callback.onError("Google sign-in failed");
                return;
            }
            String idToken = account.getIdToken();
            AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
            auth.signInWithCredential(credential).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    FirebaseUser user = auth.getCurrentUser();
                    sessionManager.enableCloudMode(user);
                    createUserIfMissing(user, null);
                    callback.onSuccess();
                } else {
                    callback.onError("Google sign-in failed");
                }
            });
        } catch (ApiException e) {
            callback.onError("Google sign-in failed");
        }
    }

    public void loginWithEmailOrLogin(String emailOrLogin, String password, AuthCallback callback) {
        if (TextUtils.isEmpty(emailOrLogin) || TextUtils.isEmpty(password) || password.length() < 6) {
            callback.onError("Неверные данные");
            return;
        }
        if (isEmail(emailOrLogin)) {
            signInWithEmail(emailOrLogin, password, callback);
        } else {
            firestore.collection("users")
                    .whereEqualTo("login", emailOrLogin)
                    .limit(1)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (!snapshot.isEmpty()) {
                            String email = snapshot.getDocuments().get(0).getString("email");
                            signInWithEmail(email, password, callback);
                        } else {
                            callback.onError("Логин не найден");
                        }
                    })
                    .addOnFailureListener(e -> callback.onError("Ошибка входа"));
        }
    }

    private void signInWithEmail(String email, String password, AuthCallback callback) {
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            callback.onError("Неверные данные");
            return;
        }
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    sessionManager.enableCloudMode(user);
                    callback.onSuccess();
                })
                .addOnFailureListener(e -> callback.onError("Ошибка входа"));
    }

    public void register(String email, String login, String password, String confirm, AuthCallback callback) {
        if (!isEmail(email) || TextUtils.isEmpty(login) || TextUtils.isEmpty(password) || !password.equals(confirm)) {
            callback.onError("Неверные данные");
            return;
        }

        firestore.collection("users").whereEqualTo("login", login).limit(1).get()
                .addOnSuccessListener(loginSnapshot -> {
                    if (!loginSnapshot.isEmpty()) {
                        callback.onError("Логин занят");
                        return;
                    }
                    firestore.collection("users").whereEqualTo("email", email).limit(1).get()
                            .addOnSuccessListener(emailSnapshot -> {
                                if (!emailSnapshot.isEmpty()) {
                                    callback.onError("Почта занята");
                                    return;
                                }
                                auth.createUserWithEmailAndPassword(email, password)
                                        .addOnSuccessListener(result -> {
                                            FirebaseUser user = result.getUser();
                                            createUserIfMissing(user, login);
                                            sessionManager.enableCloudMode(user);
                                            callback.onSuccess();
                                        })
                                        .addOnFailureListener(e -> {
                                            if (e instanceof FirebaseAuthUserCollisionException) {
                                                callback.onError("Почта занята");
                                            } else {
                                                callback.onError("Ошибка регистрации");
                                            }
                                        });
                            })
                            .addOnFailureListener(e -> callback.onError("Ошибка регистрации"));
                })
                .addOnFailureListener(e -> callback.onError("Ошибка регистрации"));
    }

    public void signOut() {
        sessionManager.signOutToGuest();
        if (googleSignInClient != null) {
            googleSignInClient.signOut();
        }
    }

    private void createUserIfMissing(FirebaseUser user, String login) {
        if (user == null) return;
        DocumentReference ref = firestore.collection("users").document(user.getUid());
        ref.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) return;
            Map<String, Object> data = new HashMap<>();
            data.put("email", user.getEmail());
            data.put("login", login);
            data.put("name", user.getDisplayName() != null ? user.getDisplayName() : login);
            data.put("avatarUrl", null);
            data.put("createdAt", FieldValue.serverTimestamp());
            ref.set(data);
        });
    }

    private boolean isEmail(String value) {
        return !TextUtils.isEmpty(value) && Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$").matcher(value).matches();
    }

    public interface AuthCallback {
        void onSuccess();
        void onError(String message);
    }
}
