package com.example.proekt;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

public class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Делаем навигационный бар прозрачным
        setupTransparentNavigationBar();
    }

    private void setupTransparentNavigationBar() {
        // Включаем edge-to-edge режим
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Для Android 11+ (API 30+)
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                    0, // Светлые иконки (0 = темные иконки)
                    WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                );
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Для Android 5.0+ (API 21+)
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            );
        }
    }
}

