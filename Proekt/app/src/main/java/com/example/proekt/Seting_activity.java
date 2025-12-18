package com.example.proekt;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class Seting_activity extends AppCompatActivity {

    private SessionManager sessionManager;
    private FirebaseFirestore firestore;
    private ShapeableImageView avatarView;
    private ShapeableImageView actionButton;
    private Uri selectedImageUri;
    private TextView loginValue;
    private EditText nameField;
    private String currentLogin;

    private final ActivityResultLauncher<Intent> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        Glide.with(this).load(selectedImageUri).into(avatarView);
                    }
                }
            }
    );

    private final ActivityResultLauncher<Intent> loginLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    updateUi();
                }
            }
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setings);

        sessionManager = SessionManager.getInstance(this);
        firestore = sessionManager.getFirestore();

        avatarView = findViewById(R.id.profile_image);
        actionButton = findViewById(R.id.action_button);
        nameField = findViewById(R.id.name_edit_text);
        loginValue = findViewById(R.id.login_value);
        Button saveButton = findViewById(R.id.save_button);
        Button pickImageButton = findViewById(R.id.pick_avatar_button);
        Button changeLoginButton = findViewById(R.id.change_login_button);
        Button addButton = findViewById(R.id.add_button);
        Button subButton = findViewById(R.id.sub_button);
        Button analyticsButton = findViewById(R.id.Analit_button);

        pickImageButton.setOnClickListener(v -> {
            if (sessionManager.getMode() == SessionManager.Mode.GUEST) {
                Toast.makeText(this, "Доступно после входа", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            pickImageLauncher.launch(intent);
        });

        saveButton.setOnClickListener(v -> {
            if (sessionManager.getMode() == SessionManager.Mode.GUEST) {
                Toast.makeText(this, "Войдите для сохранения", Toast.LENGTH_SHORT).show();
                return;
            }
            FirebaseUser user = sessionManager.getAuth().getCurrentUser();
            if (user == null) return;
            String name = nameField.getText().toString().trim();
            Map<String, Object> data = new HashMap<>();
            data.put("name", name);
            data.put("updatedAt", FieldValue.serverTimestamp());
            firestore.collection("users").document(user.getUid()).update(data);
            if (selectedImageUri != null) {
                uploadAvatar(user.getUid(), selectedImageUri);
            }
        });

        avatarView.setOnClickListener(v -> {
            // Avatar tap intentionally does nothing to avoid mixing profile edits
            // with auth flows. This spot can be used later for changing the avatar.
        });
        actionButton.setOnClickListener(v -> handleAuthAction());

        changeLoginButton.setOnClickListener(v -> {
            if (sessionManager.getMode() == SessionManager.Mode.GUEST) {
                Toast.makeText(this, "Войдите, чтобы менять логин", Toast.LENGTH_SHORT).show();
                return;
            }
            showChangeLoginDialog();
        });

        addButton.setOnClickListener(v -> startActivity(new Intent(this, AddActivity.class)));
        subButton.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        analyticsButton.setOnClickListener(v -> startActivity(new Intent(this, AnalitikActivity.class)));

        updateUi();
    }

    private void handleAuthAction() {
        if (sessionManager.getMode() == SessionManager.Mode.GUEST) {
            loginLauncher.launch(new Intent(this, LoginActivity.class));
        } else {
            sessionManager.signOutToGuest();
            Toast.makeText(this, "Вы вышли", Toast.LENGTH_SHORT).show();
            updateUi();
        }
    }

    private void updateUi() {
        if (sessionManager.getMode() == SessionManager.Mode.GUEST) {
            avatarView.setImageResource(R.drawable.ic_login);
            actionButton.setImageResource(R.drawable.enter_but);
            loginValue.setText("Гость");
            nameField.setText("");
        } else {
            FirebaseUser user = sessionManager.getAuth().getCurrentUser();
            if (user != null) {
                if (user.getPhotoUrl() != null) {
                    Glide.with(this).load(user.getPhotoUrl()).into(avatarView);
                } else {
                    avatarView.setImageResource(R.drawable.avatar_placeholder);
                }
                loadProfile(user.getUid());
            }
            actionButton.setImageResource(R.drawable.exitbutt);
        }
    }

    private void loadProfile(String uid) {
        firestore.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    String name = snapshot.getString("name");
                    currentLogin = snapshot.getString("login");
                    nameField.setText(name != null ? name : "");
                    loginValue.setText(currentLogin != null ? currentLogin : "");
                })
                .addOnFailureListener(e -> loginValue.setText(currentLogin != null ? currentLogin : ""));
    }

    private void showChangeLoginDialog() {
        FirebaseUser user = sessionManager.getAuth().getCurrentUser();
        if (user == null) return;

        BottomSheetDialog dialog = new BottomSheetDialog(this);
        dialog.setContentView(R.layout.dialog_change_login);

        TextInputEditText loginInput = dialog.findViewById(R.id.new_login_input);
        Button confirmButton = dialog.findViewById(R.id.confirm_login_button);
        Button cancelButton = dialog.findViewById(R.id.cancel_login_button);

        if (cancelButton != null) {
            cancelButton.setOnClickListener(v -> dialog.dismiss());
        }

        if (confirmButton != null) {
            confirmButton.setOnClickListener(v -> {
                if (loginInput == null) return;
                String newLogin = loginInput.getText() != null ? loginInput.getText().toString().trim() : "";
                if (!isLoginValid(newLogin)) {
                    loginInput.setError("Используйте 3-20 символов: буквы, цифры, .-_");
                    return;
                }
                if (newLogin.equals(currentLogin)) {
                    dialog.dismiss();
                    return;
                }
                if (!sessionManager.hasNetworkConnection()) {
                    Toast.makeText(this, "Нужен интернет для смены логина", Toast.LENGTH_SHORT).show();
                    return;
                }
                checkAndApplyLogin(user, newLogin, loginInput, dialog);
            });
        }

        dialog.show();
    }

    private boolean isLoginValid(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{3,20}");
    }

    private void checkAndApplyLogin(FirebaseUser user, String newLogin, TextInputEditText loginInput, BottomSheetDialog dialog) {
        firestore.collection("logins")
                .document(newLogin)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        loginInput.setError("Логин занят");
                        return;
                    }
                    updateLoginMapping(user, newLogin, dialog);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Ошибка проверки логина", Toast.LENGTH_SHORT).show());
    }

    private void updateLoginMapping(FirebaseUser user, String newLogin, BottomSheetDialog dialog) {
        // Login is editable (and separate from immutable email) so users can keep their email private
        // while changing how they are displayed. We atomically swap logins to preserve uniqueness.
        firestore.runTransaction(transaction -> {
                    DocumentReference newLoginRef = firestore.collection("logins").document(newLogin);
                    DocumentReference userRef = firestore.collection("users").document(user.getUid());
                    DocumentReference oldLoginRef = currentLogin != null ? firestore.collection("logins").document(currentLogin) : null;

                    DocumentSnapshot snapshot = transaction.get(newLoginRef);
                    if (snapshot.exists()) {
                        throw new FirebaseFirestoreException("Login exists", FirebaseFirestoreException.Code.ABORTED);
                    }

                    Map<String, Object> loginData = new HashMap<>();
                    loginData.put("uid", user.getUid());

                    transaction.set(newLoginRef, loginData);
                    transaction.update(userRef, "login", newLogin);
                    if (oldLoginRef != null) {
                        transaction.delete(oldLoginRef);
                    }
                    return null;
                })
                .addOnSuccessListener(aVoid -> {
                    currentLogin = newLogin;
                    loginValue.setText(newLogin);
                    Toast.makeText(this, "Логин обновлен", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Не удалось обновить логин", Toast.LENGTH_SHORT).show();
                });
    }

    private void uploadAvatar(String uid, Uri uri) {
        StorageReference ref = FirebaseStorage.getInstance().getReference().child("avatars/" + uid + ".jpg");
        ref.putFile(uri).continueWithTask(task -> ref.getDownloadUrl())
                .addOnSuccessListener(downloadUri -> firestore.collection("users").document(uid)
                        .update("avatarUrl", downloadUri.toString()))
                .addOnFailureListener(e -> Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show());
    }
}
