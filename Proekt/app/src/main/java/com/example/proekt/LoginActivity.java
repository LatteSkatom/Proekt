package com.example.proekt;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {
    private EditText Username, Password;
    private Button buttonlogin, buttonRegister;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // Используем стандартные переходы без дополнительных утилит

        auth = FirebaseAuth.getInstance();

        Username = findViewById(R.id.Username);
        Password = findViewById(R.id.Password);
        buttonlogin = findViewById(R.id.buttonlogin);
        buttonRegister = findViewById(R.id.buttonregister);

        buttonlogin.setOnClickListener(v -> signInAnonymously());
        buttonRegister.setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void signInAnonymously() {
        FirebaseUser current = auth.getCurrentUser();
        if (current != null) {
            openMain();
            return;
        }

        auth.signInAnonymously()
                .addOnSuccessListener(result -> openMain())
                .addOnFailureListener(e -> Toast.makeText(this, "Не удалось войти: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void openMain() {
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}
