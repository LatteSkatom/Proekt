package com.example.proekt;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {
    private Button buttonRegister;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();

        buttonRegister = findViewById(R.id.buttonRegister);
        buttonRegister.setOnClickListener(v -> signInAnonymously());
    }

    private void signInAnonymously() {
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            finish();
            return;
        }

        auth.signInAnonymously()
                .addOnSuccessListener(result -> finish())
                .addOnFailureListener(e -> Toast.makeText(this, "Не удалось войти: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}
