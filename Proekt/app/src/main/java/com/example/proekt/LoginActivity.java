package com.example.proekt;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.proekt.utils.ActivityTransitionUtils;
import com.example.proekt.utils.WindowUtils;
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
        WindowUtils.setupTransparentNavigationBar(this);

        auth = FirebaseAuth.getInstance();

        Username = findViewById(R.id.Username);
        Password = findViewById(R.id.Password);
        buttonlogin = findViewById(R.id.buttonlogin);
        buttonRegister = findViewById(R.id.buttonregister);

        buttonlogin.setOnClickListener(v -> signInAnonymously());
        buttonRegister.setOnClickListener(v -> ActivityTransitionUtils.startActivityWithFade(this, new Intent(this, RegisterActivity.class)));
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
        ActivityTransitionUtils.startActivityClearStack(this, intent);
    }
}
