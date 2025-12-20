package com.example.proekt.utils;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.transition.Fade;
import android.transition.Transition;
import android.view.Window;

import com.example.proekt.R;

/**
 * Утилиты для управления плавными переходами между Activity.
 */
public class ActivityTransitionUtils {

    private static final long ENTER_FADE_DURATION_MS = 200;
    private static final long EXIT_FADE_DURATION_MS = 150;

    /**
     * Настраивает быстрые fade-переходы окна и исключает нижний бар из анимации.
     */
    public static void setupWindowFadeTransition(Activity activity) {
        activity.getWindow().requestFeature(Window.FEATURE_CONTENT_TRANSITIONS);

        Transition enter = createFadeTransition(Fade.IN, ENTER_FADE_DURATION_MS);
        Transition exit = createFadeTransition(Fade.OUT, EXIT_FADE_DURATION_MS);

        activity.getWindow().setEnterTransition(enter);
        activity.getWindow().setReenterTransition(enter);
        activity.getWindow().setExitTransition(exit);
        activity.getWindow().setReturnTransition(exit);
    }

    private static Transition createFadeTransition(int mode, long durationMs) {
        Fade fade = new Fade(mode);
        fade.setDuration(durationMs);
        fade.excludeTarget(R.id.imageView20, true);
        return fade;
    }

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
        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity);
        activity.startActivityForResult(intent, requestCode, options.toBundle());
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
        activity.finishAfterTransition();
    }

    /**
     * Запускает новую Activity и очищает весь стек с мягким fade+scale.
     * (Например, Login -> Main).
     * @param context Текущий контекст/Activity.
     * @param intent Намерение для запуска новой Activity.
     */
    public static void startActivityClearStack(Context context, Intent intent) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity);
            activity.startActivity(intent, options.toBundle());
        } else {
            context.startActivity(intent);
        }
    }

    /**
     * Запускает новую Activity с плавным fade+scale.
     * @param context Текущий контекст/Activity.
     * @param intent Намерение для запуска новой Activity.
     */
    public static void startActivityWithFade(Activity activity, Intent intent) {
        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity);
        activity.startActivity(intent, options.toBundle());
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

        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(activity);
        activity.startActivity(intent, options.toBundle());
        activity.finishAfterTransition();
    }
}
