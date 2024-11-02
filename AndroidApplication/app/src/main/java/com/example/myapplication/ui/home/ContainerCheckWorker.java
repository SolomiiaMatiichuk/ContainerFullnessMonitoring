package com.example.myapplication.ui.home;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.myapplication.R;

public class ContainerCheckWorker extends Worker {

    private static final String CHANNEL_ID = "container_fullness_alert";

    public ContainerCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Perform network request to check container fullness
        int fullnessLevel = checkContainerFullness();

        if (fullnessLevel > 80) {
            sendNotification("Container fullness level is above 80%!");
        }

        return Result.success();
    }

    private int checkContainerFullness() {
        // Implement the API call to check container fullness here
        // Return the fullness level as an integer
        return 85; // Replace this with the actual fullness level
    }

    private void sendNotification(String messageBody) {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Container Alerts", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Container Alert")
                .setContentText(messageBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(1, notification);
    }
}
