package com.example.proekt;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.WriteBatch;
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
        loginValue = findViewById(R.id.login_value);
        Button pickImageButton = findViewById(R.id.pick_avatar_button);
        Button profileMenuButton = findViewById(R.id.profile_menu_button);
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

        avatarView.setOnClickListener(v -> {
            // Avatar tap intentionally does nothing to avoid mixing profile edits
            // with auth flows. This spot can be used later for changing the avatar.
        });
        actionButton.setOnClickListener(v -> handleAuthAction());

        profileMenuButton.setOnClickListener(v -> {
            if (sessionManager.getMode() == SessionManager.Mode.GUEST) {
                Toast.makeText(this, "Войдите, чтобы открыть меню профиля", Toast.LENGTH_SHORT).show();
                return;
            }
            showProfileMenuDialog();
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
                    currentLogin = snapshot.getString("login");
                    loginValue.setText(currentLogin != null ? currentLogin : "");
                })
                .addOnFailureListener(e -> loginValue.setText(currentLogin != null ? currentLogin : ""));
    }

    private void showProfileMenuDialog() {
        FirebaseUser user = sessionManager.getAuth().getCurrentUser();
        if (user == null) return;

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_profile_menu);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = window.getAttributes();
            params.gravity = Gravity.CENTER;
            // Outside area stays semi-transparent to keep focus on the centered menu.
            window.setDimAmount(0.6f);
            window.setAttributes(params);
        }

        ShapeableImageView dialogAvatar = dialog.findViewById(R.id.dialog_avatar);
        TextView dialogLogin = dialog.findViewById(R.id.dialog_login_value);
        TextInputEditText dialogLoginInput = dialog.findViewById(R.id.dialog_login_input);
        TextView loginFeedbackText = dialog.findViewById(R.id.login_feedback_text);
        Button saveProfileButton = dialog.findViewById(R.id.dialog_save_profile_button);
        Button changePasswordButton = dialog.findViewById(R.id.dialog_change_password_button);
        View passwordFields = dialog.findViewById(R.id.password_fields_container);
        TextInputEditText oldPasswordInput = dialog.findViewById(R.id.old_password_input);
        TextInputEditText newPasswordInput = dialog.findViewById(R.id.new_password_input);
        TextInputEditText confirmPasswordInput = dialog.findViewById(R.id.confirm_password_input);
        Button submitPasswordButton = dialog.findViewById(R.id.submit_password_button);
        Button closeButton = dialog.findViewById(R.id.close_menu_button);

        if (user.getPhotoUrl() != null) {
            Glide.with(this).load(user.getPhotoUrl()).into(dialogAvatar);
        } else {
            dialogAvatar.setImageResource(R.drawable.avatar_placeholder);
        }

        String initialLogin = currentLogin != null ? currentLogin : (user.getEmail() != null ? user.getEmail() : "");
        dialogLogin.setText(initialLogin);
        if (dialogLoginInput != null) {
            dialogLoginInput.setText(initialLogin);
        }

        changePasswordButton.setOnClickListener(v -> {
            // The menu replaces previous scattered buttons so account actions live in one focused, centered surface.
            passwordFields.setVisibility(View.VISIBLE);
        });

        saveProfileButton.setOnClickListener(v -> {
            String newLogin = dialogLoginInput != null && dialogLoginInput.getText() != null
                    ? dialogLoginInput.getText().toString().trim()
                    : "";
            handleProfileSave(user, newLogin, dialogLogin, loginFeedbackText, dialogLoginInput);
        });

        submitPasswordButton.setOnClickListener(v -> handlePasswordChange(user, oldPasswordInput, newPasswordInput, confirmPasswordInput, dialog));
        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.setCancelable(true);
        dialog.show();
    }

    private void handlePasswordChange(FirebaseUser user, TextInputEditText oldPasswordInput, TextInputEditText newPasswordInput, TextInputEditText confirmPasswordInput, Dialog dialog) {
        String oldPassword = oldPasswordInput.getText() != null ? oldPasswordInput.getText().toString().trim() : "";
        String newPassword = newPasswordInput.getText() != null ? newPasswordInput.getText().toString().trim() : "";
        String confirmPassword = confirmPasswordInput.getText() != null ? confirmPasswordInput.getText().toString().trim() : "";

        boolean hasError = false;
        if (oldPassword.isEmpty()) {
            oldPasswordInput.setError("Введите текущий пароль");
            hasError = true;
        }
        if (newPassword.length() < 6) {
            newPasswordInput.setError("Минимум 6 символов");
            hasError = true;
        }
        if (!newPassword.equals(confirmPassword)) {
            confirmPasswordInput.setError("Пароли не совпадают");
            hasError = true;
        }
        if (hasError) {
            return;
        }

        String email = user.getEmail();
        if (email == null || email.isEmpty()) {
            Toast.makeText(this, "Нельзя сменить пароль без почты", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthCredential credential = EmailAuthProvider.getCredential(email, oldPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    // Firebase requires recent reauthentication before sensitive actions like password updates.
                    user.updatePassword(newPassword)
                            .addOnSuccessListener(unused -> {
                                Toast.makeText(this, "Пароль обновлен", Toast.LENGTH_SHORT).show();
                                dialog.dismiss();
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Не удалось обновить пароль", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> {
                    oldPasswordInput.setError("Неверный текущий пароль");
                    Toast.makeText(this, "Не удалось подтвердить личность", Toast.LENGTH_SHORT).show();
                });
    }

    private void handleProfileSave(FirebaseUser user, String newLogin, TextView dialogLogin, TextView loginFeedbackText, TextInputEditText dialogLoginInput) {
        if (newLogin.isEmpty()) {
            if (dialogLoginInput != null) {
                dialogLoginInput.setError("Логин не может быть пустым");
            }
            if (loginFeedbackText != null) {
                loginFeedbackText.setText("Введите логин");
                loginFeedbackText.setTextColor(ContextCompat.getColor(this, R.color.black));
            }
            return;
        }

        if (newLogin.length() < 3 || newLogin.length() > 30) {
            if (dialogLoginInput != null) {
                dialogLoginInput.setError("Логин должен быть от 3 до 30 символов");
            }
            if (loginFeedbackText != null) {
                loginFeedbackText.setText("Неверная длина логина");
                loginFeedbackText.setTextColor(ContextCompat.getColor(this, R.color.black));
            }
            return;
        }

        String uid = user.getUid();
        WriteBatch batch = firestore.batch();
        DocumentReference userRef = firestore.collection("users").document(uid);

        Map<String, Object> userUpdate = new HashMap<>();
        userUpdate.put("login", newLogin);
        userUpdate.put("updatedAt", FieldValue.serverTimestamp());
        batch.update(userRef, userUpdate);

        if (currentLogin != null && !currentLogin.isEmpty()) {
            batch.delete(firestore.collection("logins").document(currentLogin));
        }

        Map<String, Object> loginData = new HashMap<>();
        loginData.put("uid", uid);
        batch.set(firestore.collection("logins").document(newLogin), loginData);

        String previousLogin = currentLogin;

        batch.commit()
                .addOnSuccessListener(unused -> {
                    currentLogin = newLogin;
                    dialogLogin.setText(newLogin);
                    loginValue.setText(newLogin);
                    sessionManager.updateCachedLogin(previousLogin, newLogin, user.getEmail());
                    if (loginFeedbackText != null) {
                        loginFeedbackText.setText("Профиль сохранен");
                        loginFeedbackText.setTextColor(ContextCompat.getColor(this, R.color.black));
                    }
                    if (selectedImageUri != null) {
                        uploadAvatar(uid, selectedImageUri);
                    }
                })
                .addOnFailureListener(e -> {
                    if (loginFeedbackText != null) {
                        if (e instanceof FirebaseFirestoreException &&
                                ((FirebaseFirestoreException) e).getCode() == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            loginFeedbackText.setText("Логин занят");
                        } else {
                            loginFeedbackText.setText("Не удалось сохранить профиль");
                        }
                        loginFeedbackText.setTextColor(ContextCompat.getColor(this, R.color.black));
                    }
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
