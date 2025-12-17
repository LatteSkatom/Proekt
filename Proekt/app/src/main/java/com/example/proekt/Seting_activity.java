package com.example.proekt;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class Seting_activity extends AppCompatActivity {

    private SessionManager sessionManager;
    private FirebaseFirestore firestore;
    private ShapeableImageView avatarView;
    private Uri selectedImageUri;

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
        EditText nameField = findViewById(R.id.name_edit_text);
        Button saveButton = findViewById(R.id.save_button);
        Button pickImageButton = findViewById(R.id.pick_avatar_button);
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
            if (sessionManager.getMode() == SessionManager.Mode.GUEST) {
                loginLauncher.launch(new Intent(this, LoginActivity.class));
            } else {
                sessionManager.signOutToGuest();
                Toast.makeText(this, "Вы вышли", Toast.LENGTH_SHORT).show();
                updateUi();
            }
        });

        addButton.setOnClickListener(v -> startActivity(new Intent(this, AddActivity.class)));
        subButton.setOnClickListener(v -> startActivity(new Intent(this, MainActivity.class)));
        analyticsButton.setOnClickListener(v -> startActivity(new Intent(this, AnalitikActivity.class)));

        updateUi();
    }

    private void updateUi() {
        if (sessionManager.getMode() == SessionManager.Mode.GUEST) {
            avatarView.setImageResource(R.drawable.ic_login);
        } else {
            FirebaseUser user = sessionManager.getAuth().getCurrentUser();
            if (user != null && user.getPhotoUrl() != null) {
                Glide.with(this).load(user.getPhotoUrl()).into(avatarView);
            } else {
                avatarView.setImageResource(R.drawable.avatar_placeholder);
            }
        }
    }

    private void uploadAvatar(String uid, Uri uri) {
        StorageReference ref = FirebaseStorage.getInstance().getReference().child("avatars/" + uid + ".jpg");
        ref.putFile(uri).continueWithTask(task -> ref.getDownloadUrl())
                .addOnSuccessListener(downloadUri -> firestore.collection("users").document(uid)
                        .update("avatarUrl", downloadUri.toString()))
                .addOnFailureListener(e -> Toast.makeText(this, "Ошибка загрузки", Toast.LENGTH_SHORT).show());
    }
}
