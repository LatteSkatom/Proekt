package com.example.proekt.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import com.example.proekt.R;

/**
 * Утилиты для управления плавными переходами между Activity.
 */
public class ActivityTransitionUtils {

    /**
     * Запускает новую Activity со слайдом вправо.
     * Используется для всех стандартных переходов (например, Main -> Add).
     * @param context Текущий контекст/Activity.
     * @param intent Намерение для запуска новой Activity.
     */
    public static void startActivityWithSlide(Context context, Intent intent) {
        context.startActivity(intent);
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(
                    R.anim.slide_in_right,
                    R.anim.slide_out_left
            );
        }
    }

    /**
     * Запускает новую Activity для получения результата со слайдом вправо.
     * @param activity Текущая Activity.
     * @param intent Намерение для запуска новой Activity.
     * @param requestCode Код запроса.
     */
    public static void startActivityForResultWithSlide(Activity activity, Intent intent, int requestCode) {
        activity.startActivityForResult(intent, requestCode);
        activity.overridePendingTransition(
                R.anim.slide_in_right,
                R.anim.slide_out_left
        );
    }

    /**
     * Запускает новую Activity для получения результата с плавным fade+scale.
     * @param activity Текущая Activity.
     * @param intent Намерение для запуска новой Activity.
     * @param requestCode Код запроса.
     */
    public static void startActivityForResultWithFade(Activity activity, Intent intent, int requestCode) {
        activity.startActivityForResult(intent, requestCode);
        activity.overridePendingTransition(
                R.anim.fade_scale_in,
                R.anim.fade_scale_out
        );
    }

    /**
     * Завершает текущую Activity с обратным слайдом.
     * Используется, когда нужно вернуться назад (например, Add -> Main).
     * @param activity Текущая Activity.
     */
    public static void finishWithSlideBack(Activity activity) {
        activity.finish();
        activity.overridePendingTransition(
                R.anim.slide_in_left,
                R.anim.slide_out_right
        );
    }

    /**
     * Завершает текущую Activity с мягким fade+scale назад.
     * @param activity Текущая Activity.
     */
    public static void finishWithFadeBack(Activity activity) {
        activity.finish();
        activity.overridePendingTransition(
                R.anim.fade_scale_in_back,
                R.anim.fade_scale_out_back
        );
    }

    /**
     * Запускает новую Activity и очищает весь стек с мягким fade+scale.
     * (Например, Login -> Main).
     * @param context Текущий контекст/Activity.
     * @param intent Намерение для запуска новой Activity.
     */
    public static void startActivityClearStack(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(
                    R.anim.fade_scale_in,
                    R.anim.fade_scale_out
            );
        }
    }

    /**
     * Запускает новую Activity с плавным fade+scale.
     * @param context Текущий контекст/Activity.
     * @param intent Намерение для запуска новой Activity.
     */
    public static void startActivityWithFade(Context context, Intent intent) {
        context.startActivity(intent);
        if (context instanceof Activity) {
            ((Activity) context).overridePendingTransition(
                    R.anim.fade_scale_in,
                    R.anim.fade_scale_out
            );
        }
    }

    /**
     * Запускает новую Activity с плавным fade+scale и сразу завершает текущую Activity.
     * Идеально подходит для навигации между главными вкладками без нагромождения стека.
     * @param activity Текущая Activity.
     * @param intent Намерение для запуска новой Activity.
     */
    public static void startActivityWithFadeAndFinish(Activity activity, Intent intent) {
        // Установка флагов для предотвращения повторного создания Activity, если она уже есть
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

        activity.startActivity(intent);

        activity.overridePendingTransition(
                R.anim.fade_scale_in,
                R.anim.fade_scale_out
        );

        // Завершение текущей активности
        activity.finish();

        activity.overridePendingTransition(
                R.anim.fade_scale_in_back,
                R.anim.fade_scale_out_back
        );
    }
}
