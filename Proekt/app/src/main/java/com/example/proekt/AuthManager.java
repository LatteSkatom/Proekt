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
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.WriteBatch;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Locale;
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

// Replace ONLY the loginWithEmailOrLogin method in AuthManager
// Firestore read access is assumed to be OPEN (test mode)

    public void loginWithEmailOrLogin(String emailOrLogin, String password, AuthCallback callback) {
        if (TextUtils.isEmpty(emailOrLogin) || TextUtils.isEmpty(password) || password.length() < 6) {
            callback.onError("Неверные данные");
            return;
        }

        String input = emailOrLogin.trim();

        // If input is an email → login directly
        if (isEmail(input)) {
            signInWithEmail(input, password, callback, null);
            return;
        }

        // Otherwise treat input as username → resolve via Firestore
        String username = input;

        firestore.collection("logins")
                .document(username)
                .get()
                .addOnSuccessListener(loginSnap -> {
                    if (!loginSnap.exists()) {
                        callback.onError("Неверный логин или пароль");
                        return;
                    }

                    String uid = loginSnap.getString("uid");
                    if (TextUtils.isEmpty(uid)) {
                        callback.onError("Ошибка входа");
                        return;
                    }

                    firestore.collection("users")
                            .document(uid)
                            .get()
                            .addOnSuccessListener(userSnap -> {
                                if (!userSnap.exists()) {
                                    callback.onError("Ошибка входа");
                                    return;
                                }

                                String email = userSnap.getString("email");
                                if (TextUtils.isEmpty(email)) {
                                    callback.onError("Ошибка входа");
                                    return;
                                }

                                // Login using resolved email
                                signInWithEmail(email, password, callback, username);
                            })
                            .addOnFailureListener(e ->
                                    callback.onError("Ошибка входа")
                            );
                })
                .addOnFailureListener(e ->
                        callback.onError("Ошибка входа")
                );
    }

    // signInWithEmail remains unchanged
    private void signInWithEmail(String email, String password, AuthCallback callback, String loginUsed) {
        if (TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            callback.onError("Неверные данные");
            return;
        }

        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    sessionManager.enableCloudMode(user);

                    // Optional cache for future offline use
                    if (!TextUtils.isEmpty(loginUsed)) {
                        sessionManager.cacheLoginEmail(loginUsed, email);
                    }

                    callback.onSuccess();
                })
                .addOnFailureListener(e ->
                        callback.onError("Неверный логин или пароль")
                );
    }


    public void register(String email, String login, String password, String confirm, AuthCallback callback) {
        if (!isEmail(email) || TextUtils.isEmpty(login) || TextUtils.isEmpty(password) || !password.equals(confirm)) {
            callback.onError("Неверные данные");
            return;
        }

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        callback.onError("Ошибка регистрации");
                        return;
                    }

                    String uid = user.getUid();
                    WriteBatch batch = firestore.batch();

                    DocumentReference userRef = firestore.collection("users").document(uid);
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("email", email);
                    userData.put("login", login);
                    userData.put("name", user.getDisplayName() != null ? user.getDisplayName() : login);
                    userData.put("avatarUrl", null);
                    userData.put("createdAt", FieldValue.serverTimestamp());
                    batch.set(userRef, userData);

                    Map<String, Object> loginData = new HashMap<>();
                    loginData.put("uid", uid);
                    batch.set(firestore.collection("logins").document(login), loginData);

                    String emailHash = hashEmail(email);
                    Map<String, Object> emailData = new HashMap<>();
                    emailData.put("uid", uid);
                    batch.set(firestore.collection("emails").document(emailHash), emailData);

                    batch.commit()
                            .addOnSuccessListener(unused -> {
                                sessionManager.enableCloudMode(user);
                                sessionManager.cacheLoginEmail(login, email);
                                callback.onSuccess();
                            })
                            .addOnFailureListener(e -> {
                                user.delete();
                                if (e instanceof FirebaseFirestoreException
                                        && ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                                    callback.onError("Логин или почта заняты");
                                } else {
                                    callback.onError("Ошибка регистрации");
                                }
                            });
                })
                .addOnFailureListener(e -> {
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        callback.onError("Почта занята");
                    } else {
                        callback.onError("Ошибка регистрации");
                    }
                });
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

    private String hashEmail(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(normalized.hashCode());
        }
    }

    public interface AuthCallback {
        void onSuccess();
        void onError(String message);
    }
}
