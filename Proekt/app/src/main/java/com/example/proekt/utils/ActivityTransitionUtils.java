package com.example.proekt.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.example.proekt.R;

/**
 * Утилиты для управления переходами между Activity.
 * Все методы настроены на ПОЛНОЕ ОТКЛЮЧЕНИЕ анимации.
 */
public class ActivityTransitionUtils {

    // --- Методы, которые теперь используют НУЛЕВУЮ анимацию (0, 0) ---

    /**
     * Запускает новую Activity БЕЗ анимации.
     * Используется для всех стандартных переходов (например, Main -> Add).
     * @param context Текущий контекст/Activity.
     * @param intent Намерение для запуска новой Activity.
     */
    public static void startActivityWithSlide(Context context, Intent intent) {
        context.startActivity(intent);
        if (context instanceof Activity) {
            // Отключаем анимацию
            ((Activity) context).overridePendingTransition(0, 0);
        }
    }

    /**
     * Запускает новую Activity для получения результата БЕЗ анимации.
     * @param activity Текущая Activity.
     * @param intent Намерение для запуска новой Activity.
     * @param requestCode Код запроса.
     */
    public static void startActivityForResultWithSlide(Activity activity, Intent intent, int requestCode) {
        activity.startActivityForResult(intent, requestCode);
        // Отключаем анимацию
        activity.overridePendingTransition(0, 0);
    }

    /**
     * Завершает текущую Activity БЕЗ анимации.
     * Используется, когда нужно вернуться назад (например, Add -> Main).
     * @param activity Текущая Activity.
     */
    public static void finishWithSlideBack(Activity activity) {
        activity.finish();
        // Отключаем анимацию
        activity.overridePendingTransition(0, 0);
    }

    /**
     * Запускает новую Activity и очищает весь стек БЕЗ анимации.
     * (Например, Login -> Main).
     * @param context Текущий контекст/Activity.
     * @param intent Намерение для запуска новой Activity.
     */
    public static void startActivityClearStack(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        if (context instanceof Activity) {
            // Отключаем анимацию
            ((Activity) context).overridePendingTransition(0, 0);
        }
    }

    /**
     * Запускает новую Activity с заменой анимации 'Fade' на НУЛЕВУЮ.
     * @param context Текущий контекст/Activity.
     * @param intent Намерение для запуска новой Activity.
     */
    public static void startActivityWithFade(Context context, Intent intent) {
        context.startActivity(intent);
        if (context instanceof Activity) {
            // Отключаем анимацию
            ((Activity) context).overridePendingTransition(0, 0);
        }
    }

    // --- Метод для переключения вкладок (остается без изменений, так как он уже идеален) ---

    /**
     * Запускает новую Activity БЕЗ анимации и сразу же завершает текущую Activity.
     * Идеально подходит для навигации между главными вкладками.
     * @param activity Текущая Activity.
     * @param intent Намерение для запуска новой Activity.
     */
    public static void startActivityWithoutAnimationAndFinish(Activity activity, Intent intent) {
        // Установка флагов для предотвращения повторного создания Activity, если она уже есть
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        activity.startActivity(intent);

        // 1. Отключение анимации для START (появление новой Activity)
        activity.overridePendingTransition(0, 0);

        // Завершение текущей активности
        activity.finish();

        // 2. Отключение анимации для FINISH (закрытие текущей Activity).
        activity.overridePendingTransition(0, 0);
    }
}