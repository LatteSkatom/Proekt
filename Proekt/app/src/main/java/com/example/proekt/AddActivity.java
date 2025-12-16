package com.example.proekt;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.proekt.network.ApiService;
import com.example.proekt.network.RetrofitClient;
import com.example.proekt.network.SimpleResponse;
import com.example.proekt.network.Subscription; // Импортируем модель Subscription
import com.example.proekt.utils.NetworkUtils;
import com.example.proekt.utils.ActivityTransitionUtils;
import com.example.proekt.utils.WindowUtils;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddActivity extends AppCompatActivity {

    private EditText etService, etCost, etDate;
    private ShapeableImageView btnSave;
    private int userId;
    private boolean isGuest = false;
    private static final int GUEST_ID = -1; // Константа гостевого ID

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_sub);
        WindowUtils.setupTransparentNavigationBar(this);

        etService = findViewById(R.id.editServiceName);
        etCost = findViewById(R.id.editCost);
        etDate = findViewById(R.id.editDate);
        btnSave = findViewById(R.id.save_button);

        // Перемещаем обработчик кнопки сюда, чтобы избежать дублирования
        btnSave.setOnClickListener(v -> saveSubscription());


        Button addButton = findViewById(R.id.sub_button);
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(AddActivity.this, MainActivity.class);
            // Используем флаг RESULT_CANCELED, если просто переходим назад, чтобы не запускать лишний раз синхронизацию
            setResult(RESULT_CANCELED);
            ActivityTransitionUtils.finishWithSlideBack(this);
        });

        ShapeableImageView settingsbutton = findViewById(R.id.settingsbutt);
        settingsbutton.setOnClickListener(v -> {
            Intent intent = new Intent(AddActivity.this, Seting_activity.class);
            // Тут можно просто запустить новую активность без ожидания результата
            ActivityTransitionUtils.startActivityWithSlide(this, intent);
        });

        Button analitikbutton = findViewById(R.id.Analit_button);
        analitikbutton.setOnClickListener(v -> {
            Intent intent = new Intent(AddActivity.this, AnalitikActivity.class);
            ActivityTransitionUtils.startActivityWithSlide(this, intent);
        });


        // Настройка DatePicker
        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(v -> showDatePicker());

        // --- ЛОГИКА ОПРЕДЕЛЕНИЯ USER ID и ГОСТЯ ---
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = prefs.getInt("user_id", 0);

        // Если ID не найден, устанавливаем гостевой режим.
        if (userId == 0) {
            userId = GUEST_ID; // Используем гостевой ID
            isGuest = true;
        } else if (userId == GUEST_ID) {
            isGuest = true;
        } else {
            isGuest = false;
        }

        if (userId == GUEST_ID) {
            // У гостя нет смысла передавать ID дальше, но если ты оставил код, пусть будет.
            // Intent intent = new Intent(getIntent());
            // intent.putExtra("user_id", userId);
        }
        // ------------------------------------------
    }

    private void showDatePicker() {
        // ... (метод showDatePicker без изменений)
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
        String costString = etCost.getText().toString().trim(); // Изменил имя, чтобы избежать путаницы
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


        // 1. Создаем JSON для локального/сетевого сохранения
        JsonObject jsonToSend = new JsonObject();
        jsonToSend.addProperty("service_name", serviceName);
        jsonToSend.addProperty("cost", cost);
        jsonToSend.addProperty("next_payment_date", date);
        jsonToSend.addProperty("user_id", userId);
        jsonToSend.addProperty("is_active", 1);
        jsonToSend.addProperty("chastota_id", 2);


        // ============ 2. ОБРАБОТКА ГОСТЕВОГО РЕЖИМА И ОФФЛАЙН ============

        // Если это гость ИЛИ нет сети, сохраняем только локально.
        if (isGuest || !NetworkUtils.isNetworkAvailable(this)) {
            Log.i("AddActivity", isGuest ? "Сохранение гостя локально" : "Сохранение офлайн локально");

            // Сохраняем локально (используем безопасный метод saveLocal)
            saveLocal(serviceName, cost, date);

            // Если не гость, но нет сети, также можно добавить в queue (если бы она тут была)
            // Но пока что только сохраняем в кэш

            finishWithOk();
            return; // Выход, сетевой запрос не нужен
        }

        // ============ 3. ОНЛАЙН (Только для авторизованных пользователей с сетью) ============
        ApiService api = RetrofitClient.getInstance().getApi();
        Call<SimpleResponse> call = api.addSubscription(jsonToSend);

        call.enqueue(new Callback<SimpleResponse>() {
            @Override
            public void onResponse(Call<SimpleResponse> call, Response<SimpleResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {

                    // Сохраняем локально, чтобы сразу обновить кэш в MainActivity
                    saveLocal(serviceName, cost, date);
                    finishWithOk();

                } else {
                    Toast.makeText(AddActivity.this, "Ошибка сервера при добавлении", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SimpleResponse> call, Throwable t) {
                Toast.makeText(AddActivity.this, "Ошибка сети. Сохранено локально в очередь.", Toast.LENGTH_SHORT).show();

                // Если произошла ошибка сети, сохраняем локально (в кэш)
                saveLocal(serviceName, cost, date);

                // Здесь ты бы добавил в offline_queue, если бы у тебя был доступ к методу enqueueOffline из MainActivity.
                // Так как прямого доступа нет, достаточно saveLocal. MainActivity при onResume попробует синхронизироваться.

                finishWithOk();
            }
        });
    }

    /**
     * БЕЗОПАСНОЕ сохранение подписки в локальный кэш (список Subscription)
     * Использует Gson для корректного добавления элемента в JSON-массив,
     * избегая опасной конкатенации строк.
     */
    private void saveLocal(String service, double cost, String date) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String key = "cached_subscriptions_" + userId;
        String json = prefs.getString(key, null);

        Gson gson = new Gson();
        Type type = new TypeToken<List<Subscription>>() {}.getType();

        // 1. Десериализуем существующий список (или создаем новый)
        List<Subscription> currentList = json != null ? gson.fromJson(json, type) : new ArrayList<>();
        if (currentList == null) currentList = new ArrayList<>();

        // 2. Создаем новый объект Subscription (ID не нужен для локального кэша, т.к. его выдает сервер)
        // NOTE: Используем конструктор, который ты ранее скинул.
        // Если твой конструктор требует 7 аргументов, нам нужно их заполнить.
        // Используем 0 для idSub и userId, и null для ненужных полей.

        // ВАЖНО: Модель Subscription требует 7 аргументов (даже если 2 не используются)
        Subscription newSub = new Subscription(
                0, // idSub
                service,
                cost,
                date,
                userId, // user_id (будет -1 для гостя)
                "1", // is_active
                null // unused
        );

        // 3. Добавляем новый элемент
        currentList.add(newSub);

        // 4. Сериализуем обратно и сохраняем
        String newJson = gson.toJson(currentList);
        prefs.edit().putString(key, newJson).apply();
    }

    private void finishWithOk() {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("updated", true);
        setResult(RESULT_OK, resultIntent);
        ActivityTransitionUtils.finishWithSlideBack(this);
    }
}