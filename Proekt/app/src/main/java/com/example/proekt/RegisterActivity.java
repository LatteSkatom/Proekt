package com.example.proekt;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.proekt.utils.ActivityTransitionUtils;

public class RegisterActivity extends AppCompatActivity {

    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        authManager = new AuthManager(this);

        EditText emailField = findViewById(R.id.email_edit_text);
        EditText loginField = findViewById(R.id.login_edit_text);
        EditText passwordField = findViewById(R.id.password_edit_text);
        EditText confirmField = findViewById(R.id.confirm_password_edit_text);
        Button registerButton = findViewById(R.id.register_button);

        registerButton.setOnClickListener(v -> authManager.register(
                emailField.getText().toString().trim(),
                loginField.getText().toString().trim(),
                passwordField.getText().toString().trim(),
                confirmField.getText().toString().trim(),
                new ToastCallback()
        ));
    }

    private class ToastCallback implements AuthManager.AuthCallback {
        @Override
        public void onSuccess() {
            Toast.makeText(RegisterActivity.this, "Аккаунт создан", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        }

        @Override
        public void onError(String message) {
            Toast.makeText(RegisterActivity.this, message, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        ActivityTransitionUtils.finishWithFadeBack(this);
    }
}
