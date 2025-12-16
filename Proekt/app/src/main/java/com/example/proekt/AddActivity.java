package com.example.proekt;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.proekt.utils.ActivityTransitionUtils;
import com.example.proekt.utils.WindowUtils;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class AddActivity extends AppCompatActivity {

    private static final String TAG = "AddActivity";

    private EditText etService, etCost, etDate;
    private ShapeableImageView btnSave;

    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_sub);
        WindowUtils.setupTransparentNavigationBar(this);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        etService = findViewById(R.id.editServiceName);
        etCost = findViewById(R.id.editCost);
        etDate = findViewById(R.id.editDate);
        btnSave = findViewById(R.id.save_button);

        btnSave.setOnClickListener(v -> saveSubscription());

        Button addButton = findViewById(R.id.sub_button);
        addButton.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            ActivityTransitionUtils.finishWithSlideBack(this);
        });

        ShapeableImageView settingsbutton = findViewById(R.id.settingsbutt);
        settingsbutton.setOnClickListener(v -> {
            Intent intent = new Intent(AddActivity.this, Seting_activity.class);
            ActivityTransitionUtils.startActivityWithSlide(this, intent);
        });

        Button analitikbutton = findViewById(R.id.Analit_button);
        analitikbutton.setOnClickListener(v -> {
            Intent intent = new Intent(AddActivity.this, AnalitikActivity.class);
            ActivityTransitionUtils.startActivityWithSlide(this, intent);
        });

        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(v -> showDatePicker());
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    String result = String.format("%04d-%02d-%02d", year, month + 1, day);
                    etDate.setText(result);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );

        dialog.show();
    }

    private void saveSubscription() {
        String serviceName = etService.getText().toString().trim();
        String costString = etCost.getText().toString().trim();
        String date = etDate.getText().toString().trim();
        double cost;

        if (serviceName.isEmpty() || costString.isEmpty() || date.isEmpty()) {
            Toast.makeText(this, "Заполните все поля", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            cost = Double.parseDouble(costString);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Неверный формат стоимости", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Не удалось определить пользователя", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> subscriptionData = new HashMap<>();
        subscriptionData.put("serviceName", serviceName);
        subscriptionData.put("cost", cost);
        subscriptionData.put("frequency", "monthly");
        subscriptionData.put("nextPaymentDate", date);
        subscriptionData.put("isActive", true);
        subscriptionData.put("createdAt", FieldValue.serverTimestamp());

        firestore.collection("users")
                .document(currentUser.getUid())
                .collection("subscriptions")
                .add(subscriptionData)
                .addOnSuccessListener(documentReference -> finishWithOk())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Ошибка сохранения подписки", e);
                    Toast.makeText(AddActivity.this, "Не удалось сохранить подписку", Toast.LENGTH_SHORT).show();
                });
    }

    private void finishWithOk() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("updated", true);
        setResult(RESULT_OK, resultIntent);
        ActivityTransitionUtils.finishWithSlideBack(this);
    }
}
