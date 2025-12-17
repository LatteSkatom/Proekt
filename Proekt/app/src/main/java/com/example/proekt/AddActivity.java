package com.example.proekt;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class AddActivity extends AppCompatActivity {

    private SessionManager sessionManager;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_sub);

        sessionManager = SessionManager.getInstance(this);
        firestore = sessionManager.getFirestore();

        EditText serviceField = findViewById(R.id.editServiceName);
        EditText costField = findViewById(R.id.editCost);
        EditText nextPaymentField = findViewById(R.id.editDate);
        View saveButton = findViewById(R.id.save_button);

        saveButton.setOnClickListener(v -> {
            String serviceName = serviceField.getText().toString().trim();
            String costText = costField.getText().toString().trim();
            String nextDate = nextPaymentField.getText().toString().trim();
            String frequency = "MONTHLY";
            boolean isActive = true;

            if (TextUtils.isEmpty(serviceName) || TextUtils.isEmpty(costText) || TextUtils.isEmpty(nextDate)) {
                Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
                return;
            }

            double cost = Double.parseDouble(costText);
            FirebaseSubscription subscription = new FirebaseSubscription(serviceName, cost, frequency, nextDate, isActive, Timestamp.now());

            if (sessionManager.getMode() == SessionManager.Mode.GUEST) {
                Intent result = new Intent();
                result.putExtra("subscription", subscription);
                setResult(RESULT_OK, result);
                finish();
            } else {
                FirebaseUser user = sessionManager.getAuth().getCurrentUser();
                if (user == null) {
                    Toast.makeText(this, "Авторизуйтесь", Toast.LENGTH_SHORT).show();
                    return;
                }
                Map<String, Object> data = new HashMap<>();
                data.put("serviceName", serviceName);
                data.put("cost", cost);
                data.put("frequency", frequency);
                data.put("nextPaymentDate", nextDate);
                data.put("isActive", isActive);
                data.put("createdAt", FieldValue.serverTimestamp());

                firestore.collection("users")
                        .document(user.getUid())
                        .collection("subscriptions")
                        .add(data)
                        .addOnSuccessListener(r -> {
                            setResult(RESULT_OK);
                            finish();
                        })
                        .addOnFailureListener(e -> Toast.makeText(AddActivity.this, "Ошибка сохранения", Toast.LENGTH_SHORT).show());
            }
        });
    }
}
