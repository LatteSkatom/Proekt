package com.example.proekt;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseUser;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;


import com.example.proekt.utils.NetworkUtils;
import com.example.proekt.utils.ActivityTransitionUtils;
import com.example.proekt.utils.WindowUtils;

import com.example.proekt.network.ApiService;
import com.example.proekt.network.RetrofitClient;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.FieldValue;

import java.util.HashMap;
import java.util.Map;


import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.proekt.network.DeleteResponse;
import com.example.proekt.network.RetrofitClient;
import com.example.proekt.network.SimpleResponse;
import com.example.proekt.network.Subscription;
import com.example.proekt.network.SubscriptionResponse;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * MainActivity с поддержкой офлайн-очереди в SharedPreferences.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private boolean loadedOnce = false;
    private RecyclerView recyclerView;
    private SubscriptionAdapter adapter;
    private int userId;
    private List<Subscription> subscriptionList = new ArrayList<>();
    private Gson gson = new Gson();
    private boolean isGuest = false;
    private static final int GUEST_ID = -1; // Специальный ID для локального хранилища гостя

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WindowUtils.setupTransparentNavigationBar(this);

        // =========================
// FIREBASE INIT (ТЕСТ)
// =========================
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        FirebaseFirestoreSettings fbSettings =
                new FirebaseFirestoreSettings.Builder()
                        .setPersistenceEnabled(true)
                        .build();
        db.setFirestoreSettings(fbSettings);

        FirebaseAuth auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously()
                    .addOnSuccessListener(result -> {
                        Log.d("FIREBASE", "Anonymous UID = " + result.getUser().getUid());
                        ensureUserDocument();
                    })
                    .addOnFailureListener(e ->
                            Log.e("FIREBASE", "Auth error", e));
        } else {
            Log.d("FIREBASE", "Already signed in: " + auth.getCurrentUser().getUid());
            ensureUserDocument();
        }



        // Recycler
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Add button
        Button addButton = findViewById(R.id.add_button);
        addButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, AddActivity.class);
            intent.putExtra("user_id", userId);
            ActivityTransitionUtils.startActivityForResultWithSlide(this, intent, 1001);
        });

        ShapeableImageView settingsbutton = findViewById(R.id.settingsbutt);
        settingsbutton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, Seting_activity.class);
            intent.putExtra("user_id", userId);
            ActivityTransitionUtils.startActivityWithSlide(this, intent);
        });

        Button analitikbutton = findViewById(R.id.Analit_button);
        analitikbutton.setOnClickListener(v -> {
           Intent intent = new Intent(MainActivity.this, AnalitikActivity.class);
            intent.putExtra("user_id", userId);
            ActivityTransitionUtils.startActivityWithSlide(this, intent);
       });




        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        userId = prefs.getInt("user_id", 0);

        if (userId == 0) {
            // Если ID нет — включаем режим гостя
            userId = GUEST_ID;
            isGuest = true;

            // (Опционально) Показываем тост для отладки
            // Toast.makeText(this, "Вход в режиме гостя", Toast.LENGTH_SHORT).show();
        } else {
            // Если ID есть (обычный юзер)
            isGuest = false;
        }

// Загружаем локальные данные (это сработает и для гостя,
// создастся файл cached_subscriptions_-1)
        loadCachedSubscriptions();


    }

    @Override
    protected void onResume() {
        super.onResume();


        if (!loadedOnce && isNetworkAvailable()) {
            loadedOnce = true;
            flushOfflineQueue();
            loadSubscriptionsFromServer();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("user_id", userId);
    }

    private void loadCachedSubscriptions() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String key = "cached_subscriptions_" + userId;
        String json = prefs.getString(key, null);

        Type type = new TypeToken<List<Subscription>>() {}.getType();
        List<Subscription> cachedList = json != null ? gson.fromJson(json, type) : new ArrayList<>();

        if (cachedList == null) cachedList = new ArrayList<>();
        subscriptionList = cachedList;

        setupAdapter();
    }

    /** 🌐 Получение подписок с сервера */
    private void loadSubscriptionsFromServer() {

        // БЛОКИРОВКА: Если гость, ничего не грузим с сервера
        if (isGuest) {
            return;
        }
        Call<SubscriptionResponse> call = RetrofitClient.getInstance().getApi().getSubscriptions(userId);
        call.enqueue(new Callback<SubscriptionResponse>() {
            @Override
            public void onResponse(Call<SubscriptionResponse> call, Response<SubscriptionResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<Subscription> serverList = response.body().getSubscriptions();
                    if (serverList == null) serverList = new ArrayList<>();

                    mergeAndSaveSubscriptions(serverList);
                    setupAdapter();
                    scheduleNotifications(subscriptionList);
                } else {
                    Toast.makeText(MainActivity.this, "Ошибка загрузки с сервера", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "getSubscriptions: неверный ответ сервера: " + (response.code()));
                }
            }

            @Override
            public void onFailure(Call<SubscriptionResponse> call, Throwable t) {
                Log.e(TAG, "Ошибка сети при загрузке", t);
                Toast.makeText(MainActivity.this, "Ошибка сети, загружены локальные данные", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 🔄 Объединение локальных и серверных данных и сохранение в кэш */
    private void mergeAndSaveSubscriptions(List<Subscription> serverList) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);

        String key = "cached_subscriptions_" + userId;
        String json = prefs.getString(key, null);
        Type type = new TypeToken<List<Subscription>>() {}.getType();
        List<Subscription> localList = json != null ? gson.fromJson(json, type) : new ArrayList<>();

        if (localList == null) localList = new ArrayList<>();

        // Добавляем офлайн-подписки, которых нет на сервере
        for (Subscription localSub : localList) {
            boolean exists = false;
            for (Subscription serverSub : serverList) {
                if (localSub.getServis() != null &&
                        localSub.getServis().equalsIgnoreCase(serverSub.getServis())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) serverList.add(localSub);
        }

        subscriptionList = serverList;
        prefs.edit().putString(key, gson.toJson(serverList)).apply();
    }

    /** 🧩 Настройка адаптера */
    private void setupAdapter() {
        adapter = new SubscriptionAdapter(subscriptionList, this::onSubscriptionLongClick);
        recyclerView.setAdapter(adapter);
    }

    /** ❌ Долгое нажатие — удалить (UI + сервер при наличии сети) */
    private void onSubscriptionLongClick(Subscription subscription, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Удалить подписку")
                .setMessage("Вы действительно хотите удалить \"" + subscription.getServis() + "\"?")
                .setPositiveButton("Удалить", (dialog, which) -> deleteSubscription(subscription, position))
                .setNegativeButton("Отмена", null)
                .show();
    }

    /** 🗑️ Удаление подписки */
    private void deleteSubscription(Subscription subscription, int position) {
        adapter.removeAt(position);
        saveSubscriptionsToStorage(adapter.getSubscriptions());

        Snackbar.make(recyclerView, "Подписка удалена", Snackbar.LENGTH_LONG)
                .setAction("Отменить", v -> {
                    adapter.restoreAt(subscription, position);
                    saveSubscriptionsToStorage(adapter.getSubscriptions());
                })
                .show();
        if (isGuest) {
            return;
        }

        if (isNetworkAvailable()) {
            Call<DeleteResponse> call = RetrofitClient.getInstance().getApi()
                    .deleteSubscription(subscription.getIdSub(), userId);

            call.enqueue(new Callback<DeleteResponse>() {
                @Override
                public void onResponse(Call<DeleteResponse> call, Response<DeleteResponse> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        if (!response.body().isSuccess()) {
                            Toast.makeText(MainActivity.this,
                                    "Ошибка: " + response.body().getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                }

                @Override
                public void onFailure(Call<DeleteResponse> call, Throwable t) {
                    Log.e(TAG, "Ошибка удаления", t);
                }
            });
        }
    }

    /** 💾 Запись списка подписок в локальное хранилище */
    private void saveSubscriptionsToStorage(List<Subscription> subscriptions) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String key = "cached_subscriptions_" + userId;
        String json = new Gson().toJson(subscriptions);
        prefs.edit().putString(key, json).apply();
    }

    /** 📡 Проверка наличия сети */
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network network = cm.getActiveNetwork();
            if (network == null) return false;
            NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && (
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            );
        } else {
            try {
                android.net.NetworkInfo netInfo = cm.getActiveNetworkInfo();
                return netInfo != null && netInfo.isConnected();
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** ⏰ Планирование уведомлений (как раньше) */
    private void scheduleNotifications(List<Subscription> subscriptions) {
        for (Subscription sub : subscriptions) {
            try {
                String nextPayment = sub.getNextPaymentDate();
                if (nextPayment == null || nextPayment.isEmpty()) continue;

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Date date = sdf.parse(nextPayment);
                if (date == null) continue;

                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                calendar.add(Calendar.DAY_OF_MONTH, -1);

                long triggerTime = calendar.getTimeInMillis();
                long now = System.currentTimeMillis();
                if (triggerTime <= now) continue;

                Intent intent = new Intent(this, NotificationReceiver.class);
                intent.putExtra("service_name", sub.getServis());
                intent.putExtra("cost", sub.getCost());

                PendingIntent pendingIntent = PendingIntent.getBroadcast(
                        this,
                        sub.getIdSub(),
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                if (alarmManager != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        if (alarmManager.canScheduleExactAlarms()) {
                            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                        }
                    } else {
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Ошибка при планировании уведомления", e);
            }
        }
    }

    /** 🔁 Приход из AddActivity — обновляем данные (и пробуем прогнать очередь) */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1001 && resultCode == RESULT_OK && data != null) {
            boolean updated = data.getBooleanExtra("updated", false);

            if (updated) {
                loadCachedSubscriptions(); // обновление локально

                if (isNetworkAvailable()) {
                    flushOfflineQueue();
                    loadSubscriptionsFromServer(); // это единственный вызов
                }
            }
        }
    }


    // ---------------------------
    // ОФЛАЙН-ОЧЕРЕДЬ (SharedPreferences JSON)
    // ---------------------------

    private String offlineKey() {
        return "offline_queue_user_" + userId;
    }

    private List<JsonObject> getOfflineQueue() {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        String raw = prefs.getString(offlineKey(), null);
        if (raw == null) return new ArrayList<>();
        try {
            Type listType = new TypeToken<List<JsonObject>>() {}.getType();
            return gson.fromJson(raw, listType);
        } catch (Exception e) {
            Log.e(TAG, "Ошибка чтения офлайн-очереди", e);
            return new ArrayList<>();
        }
    }

    private void saveOfflineQueue(List<JsonObject> queue) {
        SharedPreferences prefs = getSharedPreferences("user_prefs", MODE_PRIVATE);
        prefs.edit().putString(offlineKey(), gson.toJson(queue)).apply();
    }

    /**
     * Добавить запись в офлайн-очередь.
     * subJson — объект в формате, который ожидает твой PHP: service_name, cost, next_payment_date, user_id и т.п.
     */
    public void enqueueOffline(JsonObject subJson) {
        if (isGuest) return;
        List<JsonObject> queue = getOfflineQueue();
        queue.add(subJson);
        saveOfflineQueue(queue);
        Log.i(TAG, "Добавлено в офлайн-очередь: " + subJson);
    }

    /**
     * Попытка отправить все элементы офлайн-очереди на сервер.
     * При успешной отправке элемент удаляется из очереди.
     */
    private void flushOfflineQueue() {
        if (isGuest) {
            return;
        }
        List<JsonObject> queue = getOfflineQueue();
        if (queue.isEmpty()) {
            Log.i(TAG, "Офлайн-очередь пуста.");
            return;
        }

        ApiService api = RetrofitClient.getInstance().getApi();
        // Будем прогонять последовательно: берём копию и отправляем элементы по одному.
        JsonArray remaining = new JsonArray();
        // Чтобы не блокировать UI, просто отправляем все — удаляем только успехи.
        for (JsonObject item : queue) {
            Call<SimpleResponse> call = api.addSubscription(item);
            // синхронно/последовательно мы не можем тут, используем асинхронно и помечаем незавершённые как remaining
            call.enqueue(new Callback<SimpleResponse>() {
                @Override
                public void onResponse(Call<SimpleResponse> call, Response<SimpleResponse> response) {
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        Log.i(TAG, "Офлайн-элемент отправлен: " + item);
                        // После успешной отправки — удаляем этот элемент из сохранённой очереди
                        removeSentFromQueue(item);
                    } else {
                        Log.w(TAG, "Не удалось отправить офлайн-элемент (server error): " + item);
                    }
                }

                @Override
                public void onFailure(Call<SimpleResponse> call, Throwable t) {
                    Log.w(TAG, "Не удалось отправить офлайн-элемент (network): " + item + " — " + t.getMessage());
                }
            });
        }
    }

    /** Удаляет конкретный объект из сохранённой очереди (по JSON-строке) */
    private void removeSentFromQueue(JsonObject sent) {
        List<JsonObject> queue = getOfflineQueue();
        Iterator<JsonObject> it = queue.iterator();
        boolean removed = false;
        while (it.hasNext()) {
            JsonObject cur = it.next();
            // простое сравнение через toString — нормально, т.к структура одинакова
            if (cur.toString().equals(sent.toString())) {
                it.remove();
                removed = true;
                break;
            }
        }
        if (removed) {
            saveOfflineQueue(queue);
            Log.i(TAG, "Удалён из очереди успешно: " + sent);
        } else {
            Log.w(TAG, "Не найден элемент в очереди при удалении: " + sent);
        }
    }

    private void ensureUserDocument() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

        if (user == null) return;

        String uid = user.getUid();
        DocumentReference userRef = db.collection("users").document(uid);

        userRef.get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Map<String, Object> userData = new HashMap<>();
                userData.put("name", "Гость");
                userData.put("avatarUrl", null);
                userData.put("createdAt", FieldValue.serverTimestamp());

                userRef.set(userData)
                        .addOnSuccessListener(v ->
                                Log.d("FIREBASE", "Пользователь создан"))
                        .addOnFailureListener(e ->
                                Log.e("FIREBASE", "Ошибка создания пользователя", e));
            } else {
                Log.d("FIREBASE", "Пользователь уже существует");
            }
        });
    }

}
