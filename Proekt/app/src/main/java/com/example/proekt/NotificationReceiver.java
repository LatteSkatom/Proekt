package com.example.proekt;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String serviceName = intent.getStringExtra("service_name");
        String cost = intent.getStringExtra("cost"); // сумма подписки

        String channelId = "subscription_channel";

        // --- создаём канал уведомлений (Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Напоминания о подписках",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Уведомления о предстоящем списании по подпискам");

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                try {
                    manager.createNotificationChannel(channel);
                } catch (SecurityException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }

        // --- создаём интент для открытия приложения
        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // --- текст уведомления
        String contentText = "Завтра спишется "
                + (cost != null ? cost + " ₽ " : "")
                + "по подписке: " + serviceName;

        // --- создаём уведомление
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // ✅ стандартная иконка
                .setContentTitle("Напоминание о подписке")
                .setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(contentText))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        // --- проверяем разрешение перед показом уведомления
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return; // нет разрешения — просто выходим
        }

        try {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } catch (SecurityException e) {
            e.printStackTrace(); // безопасно перехватываем SecurityException
        }
    }
}
