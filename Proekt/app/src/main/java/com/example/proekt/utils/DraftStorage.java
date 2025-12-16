package com.example.proekt.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class DraftStorage {

    private static final String PREF_NAME = "offline_subscriptions";

    public static void saveDraft(Context context, String json) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString("draft", json).apply();
    }

    public static String getDraft(Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString("draft", null);
    }

    public static void clearDraft(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().remove("draft").apply();
    }
}
