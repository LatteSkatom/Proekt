package com.example.proekt;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class Seting_activity extends AppCompatActivity {

    private ShapeableImageView avatarImage;
    private EditText editDisplayName;
    private Button buttonSaveProfile;
    private ShapeableImageView actionButton;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;

    private Uri selectedAvatarUri;

    private final ActivityResultLauncher<String> pickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::onAvatarSelected
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setings);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        storage = FirebaseStorage.getInstance();

        avatarImage = findViewById(R.id.avatarImage);
        editDisplayName = findViewById(R.id.editDisplayName);
        buttonSaveProfile = findViewById(R.id.buttonSaveProfile);
        actionButton = findViewById(R.id.action_button);

        Button changeAvatarButton = findViewById(R.id.buttonChangeAvatar);
        changeAvatarButton.setOnClickListener(v -> pickImageLauncher.launch("image/*"));

        buttonSaveProfile.setOnClickListener(v -> saveProfile());
        actionButton.setOnClickListener(v -> finish());

        Button addButton = findViewById(R.id.add_button);
        addButton.setOnClickListener(v -> startActivity(new Intent(Seting_activity.this, AddActivity.class)));

        Button subButton = findViewById(R.id.sub_button);
        subButton.setOnClickListener(v -> startActivity(new Intent(Seting_activity.this, MainActivity.class)));

        Button analitButton = findViewById(R.id.Analit_button);
        analitButton.setOnClickListener(v -> startActivity(new Intent(Seting_activity.this, AnalitikActivity.class)));

        loadProfile();
    }

    private void loadProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            auth.signInAnonymously().addOnSuccessListener(result -> fetchUserDocument(result.getUser()));
            return;
        }
        fetchUserDocument(user);
    }

    private void fetchUserDocument(FirebaseUser user) {
        DocumentReference userRef = firestore.collection("users").document(user.getUid());
        userRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                String name = snapshot.getString("name");
                String avatarUrl = snapshot.getString("avatarUrl");
                editDisplayName.setText(!TextUtils.isEmpty(name) ? name : "Гость");
                if (!TextUtils.isEmpty(avatarUrl)) {
                    Glide.with(this)
                            .load(avatarUrl)
                            .placeholder(R.drawable.avatar_placeholder)
                            .centerCrop()
                            .into(avatarImage);
                } else {
                    avatarImage.setImageResource(R.drawable.avatar_placeholder);
                }
            } else {
                createDefaultUser(userRef);
            }
        }).addOnFailureListener(e -> Toast.makeText(this, "Не удалось загрузить профиль", Toast.LENGTH_SHORT).show());
    }

    private void createDefaultUser(DocumentReference userRef) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "Гость");
        data.put("avatarUrl", null);
        data.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());
        userRef.set(data, SetOptions.merge());
        editDisplayName.setText("Гость");
        avatarImage.setImageResource(R.drawable.avatar_placeholder);
    }

    private void onAvatarSelected(@Nullable Uri uri) {
        if (uri != null) {
            selectedAvatarUri = uri;
            avatarImage.setImageURI(uri);
        }
    }

    private void saveProfile() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Пользователь не найден", Toast.LENGTH_SHORT).show();
            return;
        }

        String displayName = editDisplayName.getText().toString().trim();
        if (TextUtils.isEmpty(displayName)) {
            displayName = "Гость";
        }

        DocumentReference userRef = firestore.collection("users").document(user.getUid());
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", displayName);

        if (selectedAvatarUri != null) {
            uploadAvatarAndSave(user, selectedAvatarUri, updates, userRef);
        } else {
            userRef.set(updates, SetOptions.merge())
                    .addOnSuccessListener(v -> Toast.makeText(this, "Профиль сохранён", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(this, "Ошибка сохранения профиля", Toast.LENGTH_SHORT).show());
        }
    }

    private void uploadAvatarAndSave(FirebaseUser user, Uri avatarUri, Map<String, Object> updates, DocumentReference userRef) {
        StorageReference avatarRef = storage.getReference()
                .child("avatars/")
                .child(user.getUid() + ".jpg");

        avatarRef.putFile(avatarUri)
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return avatarRef.getDownloadUrl();
                })
                .addOnSuccessListener(downloadUri -> {
                    updates.put("avatarUrl", downloadUri.toString());
                    userRef.set(updates, SetOptions.merge())
                            .addOnSuccessListener(v -> Toast.makeText(this, "Профиль сохранён", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(this, "Ошибка сохранения профиля", Toast.LENGTH_SHORT).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Не удалось загрузить аватар", Toast.LENGTH_SHORT).show());
    }
}
