package com.example.proekt;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.proekt.utils.ActivityTransitionUtils;
import com.example.proekt.utils.WindowUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {
    private Button buttonRegister;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        WindowUtils.setupTransparentNavigationBar(this);

        auth = FirebaseAuth.getInstance();

        buttonRegister = findViewById(R.id.buttonRegister);
        buttonRegister.setOnClickListener(v -> signInAnonymously());
    }

    private void signInAnonymously() {
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            ActivityTransitionUtils.finishWithSlideBack(this);
            return;
        }

        auth.signInAnonymously()
                .addOnSuccessListener(result -> ActivityTransitionUtils.finishWithSlideBack(this))
                .addOnFailureListener(e -> Toast.makeText(this, "Не удалось войти: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
