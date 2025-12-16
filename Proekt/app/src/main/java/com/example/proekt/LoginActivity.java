package com.example.proekt;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.content.Intent;
import android.util.Log;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatActivity;

import com.example.proekt.network.LoginResponse;
import com.example.proekt.network.RetrofitClient;
import com.example.proekt.network.SimpleResponse;
import com.example.proekt.network.Subscription;
import com.example.proekt.utils.ActivityTransitionUtils;
import com.example.proekt.utils.WindowUtils;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private EditText Username, Password;
    private Button buttonlogin, buttonRegister;
    private static final int GUEST_ID = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ✅ Проверяем, вошёл ли пользователь ранее
        boolean isLoggedIn = getSharedPreferences("user_prefs", MODE_PRIVATE)
                .getBoolean("is_logged_in", false);

        if (isLoggedIn) {
            // Если пользователь уже вошёл, сразу переходим на главный экран
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            ActivityTransitionUtils.startActivityClearStack(this, intent);
            return;
        }

        // Если пользователь не вошёл ранее — показываем экран логина
        setContentView(R.layout.activity_login);
        WindowUtils.setupTransparentNavigationBar(this);

        Username = findViewById(R.id.Username);
        Password = findViewById(R.id.Password);
        buttonlogin = findViewById(R.id.buttonlogin);
        buttonRegister = findViewById(R.id.buttonregister);

        buttonlogin.setOnClickListener(v -> {
            String username = Username.getText().toString().trim();
            String password = Password.getText().toString().trim();

            if (!username.isEmpty() && !password.isEmpty()) {
                loginUser(username, password);
            } else {
                Toast.makeText(LoginActivity.this, "Введите логин и пароль", Toast.LENGTH_SHORT).show();
            }
        });

        buttonRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            ActivityTransitionUtils.startActivityWithFade(this, intent);
        });

    }

    private void loginUser(String usernameOrEmail, String password) {
        Call<LoginResponse> call = RetrofitClient
                .getInstance()
                .getApi()
                .login(usernameOrEmail, password);

        call.enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    LoginResponse loginResponse = response.body();

                    if (loginResponse.isSuccess()) {
                        Toast.makeText(LoginActivity.this, "Успешный вход", Toast.LENGTH_SHORT).show();
                        int newUserId = loginResponse.getUserId();

                        // 1. --- МИГРАЦИЯ ДАННЫХ ГОСТЯ ---
                        migrateGuestData(newUserId);

                        // 2. ✅ Сохраняем НОВЫЙ user_id и логин
                        getSharedPreferences("user_prefs", MODE_PRIVATE)
                                .edit()
                                .putInt("user_id", newUserId)
                                .putString("username", usernameOrEmail)
                                .putBoolean("is_logged_in", true)
                                .apply();

                        // 3. ✅ Переход в MainActivity (теперь с новым, авторизованным ID)
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        ActivityTransitionUtils.startActivityClearStack(LoginActivity.this, intent);
                    } else {
                        Toast.makeText(LoginActivity.this, "Ошибка: " + loginResponse.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(LoginActivity.this, "Ошибка сервера", Toast.LENGTH_SHORT).show();
                    Log.e("LoginActivity", "Ответ сервера: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                Toast.makeText(LoginActivity.this, "Ошибка соединения: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("LoginActivity", "Ошибка соединения", t);
            }
        });
    }

    /**
     * Проверяет, есть ли локальные данные у гостя (-1), и отправляет их на сервер
     * для нового, авторизованного пользователя.
     * @param newUserId Новый ID пользователя, под которым нужно сохранить подписки.
     */
    private void migrateGuestData(int newUserId) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        Gson gson = new Gson();

        // Ключ, по которому хранятся подписки гостя в MainActivity
        String guestKey = "cached_subscriptions_" + GUEST_ID;
        String guestJson = prefs.getString(guestKey, null);

        if (guestJson != null && !guestJson.isEmpty()) {
            Type type = new TypeToken<List<Subscription>>() {}.getType();
            List<Subscription> guestList = gson.fromJson(guestJson, type);

            if (guestList != null && !guestList.isEmpty()) {
                Log.i("Migration", "Найдено " + guestList.size() + " подписок гостя. Запускаю перенос.");
                Toast.makeText(LoginActivity.this, "Переносим " + guestList.size() + " подписок с устройства на Ваш аккаунт...", Toast.LENGTH_LONG).show();

                // Отправляем каждую подписку на сервер под новым ID
                for (Subscription sub : guestList) {
                    // Создаем JSON, используя ключи, соответствующие @SerializedName в модели Subscription
                    JsonObject jsonSub = new JsonObject();
                    // Используем НОВЫЙ ID!
                    jsonSub.addProperty("user_id", newUserId);

                    // Ключи должны совпадать с тем, что ожидает твой PHP-скрипт (service_name, cost, next_payment_date)
                    jsonSub.addProperty("service_name", sub.getServis());
                    jsonSub.addProperty("cost", sub.getCost());
                    jsonSub.addProperty("next_payment_date", sub.getNextPaymentDate());
                    // isActive не отправляем, т.к. это внутренний статус, а не поле для создания

                    // Асинхронная отправка на сервер
                    RetrofitClient.getInstance().getApi().addSubscription(jsonSub)
                            .enqueue(new Callback<SimpleResponse>() {
                                @Override
                                public void onResponse(Call<SimpleResponse> call, Response<SimpleResponse> response) {
                                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                                        Log.d("Migration", "Успешно перенесена подписка: " + sub.getServis());
                                    } else {
                                        Log.w("Migration", "Ошибка сервера при переносе: " + sub.getServis() + " - " + response.code());
                                    }
                                }

                                @Override
                                public void onFailure(Call<SimpleResponse> call, Throwable t) {
                                    Log.e("Migration", "Ошибка сети при переносе: " + sub.getServis(), t);
                                }
                            });
                }

                // ОЧЕНЬ ВАЖНО: Удаляем старые локальные данные гостя
                prefs.edit().remove(guestKey).apply();
                Log.i("Migration", "Локальные данные гостя удалены.");

            } else {
                // Если JSON был, но список пуст, просто удаляем ключ
                prefs.edit().remove(guestKey).apply();
            }
        }
    }
}