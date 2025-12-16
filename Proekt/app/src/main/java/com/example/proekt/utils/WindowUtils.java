package com.example.proekt.utils;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.view.View;
import android.view.WindowInsetsController;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class WindowUtils {

    /**
     * Настраивает прозрачный навигационный бар и строку состояния для активности (Edge-to-Edge).
     * Должен вызываться ПОСЛЕ setContentView().
     * @param activity Activity, которую нужно настроить.
     */
    public static void setupTransparentNavigationBar(AppCompatActivity activity) {

        // 1. Включаем Edge-to-Edge режим (контент рисуется под системными барами)
        WindowCompat.setDecorFitsSystemWindows(activity.getWindow(), false);

        // 2. Устанавливаем прозрачный цвет для Navigation Bar и Status Bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.getWindow().setStatusBarColor(Color.TRANSPARENT);
            activity.getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }

        // 3. Устанавливаем светлый/темный режим для иконок системных баров
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Используем WindowInsetsControllerCompat для кросс-версионной совместимости
            WindowInsetsControllerCompat controller =
                    WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView());

            if (controller != null) {
                // Устанавливаем темные иконки на строке состояния (если фон светлый)
                controller.setAppearanceLightStatusBars(true);

                // Устанавливаем темные иконки на навигационном баре (если фон светлый, API 26+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    controller.setAppearanceLightNavigationBars(true);
                }
            }
        }
    }
}