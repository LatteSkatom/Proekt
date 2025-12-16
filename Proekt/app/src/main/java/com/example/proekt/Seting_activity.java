package com.example.proekt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.imageview.ShapeableImageView; // Важно!

public class Seting_activity extends AppCompatActivity {

    private int userId;
    private boolean isLoggedIn = false;
    private ShapeableImageView buttonAction; // Теперь это ShapeableImageView

    // 🔥 Идентификаторы твоих изображений
    // Убедись, что твои файлы называются enter_but.png и exitbutt.png
    private static final int DRAWABLE_LOGOUT = R.drawable.exitbutt;
    private static final int DRAWABLE_LOGIN = R.drawable.enter_but;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.setings);

        // Получаем user_id и статус
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = prefs.getInt("user_id", 0);

        isLoggedIn = prefs.getBoolean("is_logged_in", false) && userId > 0;

        // -------------------------
        //   НАСТРОЙКА КНОПОК НАВИГАЦИИ (без изменений)
        // -------------------------

        Button addButton = findViewById(R.id.add_button);
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(Seting_activity.this, AddActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });

        Button subButton = findViewById(R.id.sub_button);
        subButton.setOnClickListener(v -> {
            Intent intent = new Intent(Seting_activity.this, MainActivity.class);
            startActivity(intent);
        });

        Button analitButton = findViewById(R.id.Analit_button);
        analitButton.setOnClickListener(v -> {
            Intent intent = new Intent(Seting_activity.this, AnalitikActivity.class);
            startActivity(intent);
        });

        // ------------------------------------------
        //   УСЛОВНОЕ ОТОБРАЖЕНИЕ КНОПКИ ВХОДА/ВЫХОДА
        // ------------------------------------------

        // Используем ID, который ты установил в XML (или R.id.exitbutton, если не менял)
        buttonAction = findViewById(R.id.action_button);

        if (isLoggedIn) {
            // Если авторизован: кнопка становится "Выйти"
            buttonAction.setImageResource(DRAWABLE_LOGOUT); // Ставим изображение выхода
            buttonAction.setOnClickListener(v -> logoutUser());
        } else {
            // Если Гость: кнопка становится "Войти"
            buttonAction.setImageResource(DRAWABLE_LOGIN); // Ставим изображение входа
            buttonAction.setOnClickListener(v -> navigateToLogin());
        }
    }

    /** Переход к экрану входа/регистрации */
    private void navigateToLogin() {
        Intent intent = new Intent(Seting_activity.this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    /** Выход из аккаунта авторизованного пользователя */
    private void logoutUser() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);

        // 1. Сбрасываем все авторизационные данные
        prefs.edit()
                .remove("user_id")
                .remove("username")
                .putBoolean("is_logged_in", false)
                .apply();

        Toast.makeText(this, "Вы успешно вышли из аккаунта", Toast.LENGTH_SHORT).show();

        // 2. Перезапускаем MainActivity.
        Intent intent = new Intent(Seting_activity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}