package com.example.proekt;

import android.content.Intent;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseUser;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class AddActivity extends AppCompatActivity {

    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_sub);

        sessionManager = SessionManager.getInstance(this);

        EditText serviceField = findViewById(R.id.editServiceName);
        EditText costField = findViewById(R.id.editCost);
        EditText nextPaymentField = findViewById(R.id.editDate);
        View saveButton = findViewById(R.id.save_button);

        nextPaymentField.setFocusable(false);
        nextPaymentField.setClickable(true);
        nextPaymentField.setOnClickListener(v -> showDatePicker(nextPaymentField));

        findViewById(R.id.sub_button).setOnClickListener(v -> {
            startActivity(new Intent(AddActivity.this, MainActivity.class));
            finish();
        });

        View analyticsButton = findViewById(R.id.Analit_button);
        if (analyticsButton != null) {
            analyticsButton.setOnClickListener(v -> {
                startActivity(new Intent(AddActivity.this, AnalitikActivity.class));
                finish();
            });
        }

        View settingsButton = findViewById(R.id.settingsbutt);
        if (settingsButton != null) {
            settingsButton.setOnClickListener(v -> startActivity(new Intent(AddActivity.this, Seting_activity.class)));
        }

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

            double cost;
            try {
                cost = Double.parseDouble(costText.replace(",", "."));
            } catch (NumberFormatException ex) {
                Toast.makeText(this, "Некорректная цена", Toast.LENGTH_SHORT).show();
                return;
            }

            FirebaseSubscription subscription = new FirebaseSubscription(serviceName, cost, frequency, nextDate, isActive, Timestamp.now());

            if (sessionManager.getMode() == SessionManager.Mode.GUEST) {
                sessionManager.addLocalSubscription(subscription);
                completeAndReturn();
            } else {
                FirebaseUser user = sessionManager.getAuth().getCurrentUser();
                if (user == null) {
                    Toast.makeText(this, "Авторизуйтесь", Toast.LENGTH_SHORT).show();
                    return;
                }
                sessionManager.saveCloudSubscription(subscription, new SessionManager.SubscriptionSaveCallback() {
                    @Override
                    public void onSuccess(boolean syncedImmediately) {
                        if (!syncedImmediately) {
                            Toast.makeText(AddActivity.this, "Сохранено офлайн, синхронизируем при подключении", Toast.LENGTH_SHORT).show();
                        }
                        completeAndReturn();
                    }

                    @Override
                    public void onError(String message) {
                        Toast.makeText(AddActivity.this, message, Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    private void completeAndReturn() {
        setResult(RESULT_OK, new Intent());
        startActivity(new Intent(AddActivity.this, MainActivity.class));
        finish();
    }

    private void showDatePicker(EditText target) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(year, month, dayOfMonth);
                    String formatted = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(selected.getTime());
                    target.setText(formatted);
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }
}
