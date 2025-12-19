package com.example.proekt;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.google.android.material.imageview.ShapeableImageView;


import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.proekt.utils.ActivityTransitionUtils;

public class LoginActivity extends AppCompatActivity {

    private AuthManager authManager;
    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getData() != null) {
                    authManager.handleGoogleResult(result.getData(), new ToastCallback());
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authManager = new AuthManager(this);

        EditText emailOrLogin = findViewById(R.id.email_edit_text);
        EditText password = findViewById(R.id.password_edit_text);
        Button loginButton = findViewById(R.id.login_button);
        ShapeableImageView googleButton = findViewById(R.id.google_button);
        Button registerButton = findViewById(R.id.register_button);

        loginButton.setOnClickListener(v -> authManager.loginWithEmailOrLogin(
                emailOrLogin.getText().toString().trim(),
                password.getText().toString().trim(),
                new ToastCallback()
        ));

        registerButton.setOnClickListener(v -> ActivityTransitionUtils.startActivityWithFade(
                this,
                new Intent(this, RegisterActivity.class)
        ));
        googleButton.setOnClickListener(v -> authManager.startGoogle(this, googleLauncher));
    }

    private class ToastCallback implements AuthManager.AuthCallback {
        @Override
        public void onSuccess() {
            Toast.makeText(LoginActivity.this, "Вход выполнен", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }

        @Override
        public void onError(String message) {
            Toast.makeText(LoginActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }
}
