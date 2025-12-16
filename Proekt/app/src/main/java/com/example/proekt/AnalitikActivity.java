package com.example.proekt;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.imageview.ShapeableImageView;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AnalitikActivity extends AppCompatActivity {

    private TextView subCountTv;
    private TextView totalSumTv;
    private Button periodBtn;

    private int userId;
    private final Gson gson = new Gson();

    // 0 = Месяц, 1 = Неделя, 2 = Год
    private int periodIndex = 0;

    private final String[] PERIOD_LABELS = {"Месяц", "Неделя", "Год"};
    private static final int GUEST_ID = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.analitika);

        subCountTv = findViewById(R.id.subCount);
        totalSumTv = findViewById(R.id.totalSum);
        periodBtn = findViewById(R.id.periodBtn);

        // user_id берем как везде
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = prefs.getInt("user_id", GUEST_ID);

        // ====================================================================
        // ✅ ШАГ 1: Назначаем слушателей для ВСЕХ КНОПОК
        // (Это всегда должно выполняться)
        // ====================================================================

        Button addButton = findViewById(R.id.add_button);
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(AnalitikActivity.this, AddActivity.class);
            intent.putExtra("user_id", userId);
            startActivity(intent);
        });

        // Кнопка "Подписки"
        Button subButton = findViewById(R.id.sub_button);
        subButton.setOnClickListener(v -> {
            Intent intent = new Intent(AnalitikActivity.this, MainActivity.class);
            startActivity(intent);
        });

        ShapeableImageView settingsbutton = findViewById(R.id.settingsbutt);
        settingsbutton.setOnClickListener(v -> {
            Intent intent = new Intent(AnalitikActivity.this, Seting_activity.class);
            startActivity(intent);
        });

        // Настройка кнопки переключения периодов (теперь она всегда активна)
        periodBtn.setText(PERIOD_LABELS[periodIndex]);
        periodBtn.setOnClickListener(v -> {
            periodIndex = (periodIndex + 1) % PERIOD_LABELS.length;
            periodBtn.setText(PERIOD_LABELS[periodIndex]);
            updateAnalytics();
        });


        // ====================================================================
        // ✅ ШАГ 2: Запускаем Аналитику
        // (Запускаем для всех. Если данных нет, она покажет 0.)
        // ====================================================================
        updateAnalytics();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // В onResume также просто обновляем аналитику для всех.
        updateAnalytics();
    }

    private void updateAnalytics() {
        // Мы больше не делаем ранний выход для Гостя,
        // так как loadSubscriptions() вернет либо данные Гостя, либо пустой список.

        List<JsonObject> list = loadSubscriptions();

        int count = list.size();
        double monthSum = 0.0;

        for (JsonObject obj : list) {
            double cost = 0.0;
            try {
                if (obj.has("cost") && !obj.get("cost").isJsonNull()) {
                    cost = obj.get("cost").getAsDouble();
                }
            } catch (Exception ignored) {}

            monthSum += cost;
        }

        subCountTv.setText("Подписок: " + count);

        double result = 0.0;
        String suffix = "";

        if (PERIOD_LABELS[periodIndex].equals("Месяц")) {
            result = monthSum;
            suffix = "мес";
        } else if (PERIOD_LABELS[periodIndex].equals("Неделя")) {
            result = monthSum / 4.345;
            suffix = "нед";
        } else if (PERIOD_LABELS[periodIndex].equals("Год")) {
            result = monthSum * 12.0;
            suffix = "год";
        }

        // Оставляю форматирование, как вы просили, хотя 'suffix' тут игнорируется.
        totalSumTv.setText(String.format(Locale.getDefault(), "Сумма: %.2f ₽", result, suffix));

        // 🔥 Добавление: Если вы хотите, чтобы суффикс менялся, строка должна быть такой:
        // totalSumTv.setText(String.format(Locale.getDefault(), "Сумма: %.2f ₽/%s", result, suffix));
    }

    private List<JsonObject> loadSubscriptions() {
        List<JsonObject> out = new ArrayList<>();

        if (userId == 0) return out;

        try {
            SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
            String key = "cached_subscriptions_" + userId; // key будет "cached_subscriptions_-1" для гостя
            String raw = prefs.getString(key, "[]");

            JsonArray arr = gson.fromJson(raw, JsonArray.class);
            if (arr == null) return out;

            for (JsonElement el : arr) {
                if (el.isJsonObject()) {
                    out.add(el.getAsJsonObject());
                }
            }

        } catch (Exception ignored) {}

        return out;
    }
}