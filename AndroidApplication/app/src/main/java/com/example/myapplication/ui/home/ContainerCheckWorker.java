package com.example.myapplication.ui.home;

import static android.content.Context.MODE_PRIVATE;
import static java.lang.Integer.parseInt;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.myapplication.R;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import okhttp3.OkHttpClient;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ContainerCheckWorker extends Worker {

    private static final String CHANNEL_ID = "container_fullness_alert";

    public ContainerCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // Start the foreground service to keep this worker alive
        Intent serviceIntent = new Intent(getApplicationContext(), MyForegroundService.class);
        ContextCompat.startForegroundService(getApplicationContext(), serviceIntent);

        // Perform network request to check container fullness
        int id_con = checkContainerFullness();
        if (id_con > 0) {
            sendNotification("Контейнер " + id_con + " заповнений на більш ніж 80%!");
        }

        return Result.success();
    }

    private int checkContainerFullness() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://container-monitoring-server-5e33a8983798.herokuapp.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient())
                .build();

        SharedPreferences prefs = getApplicationContext().getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String id = prefs.getString("id", "1");

        ApiService apiService = retrofit.create(ApiService.class);
        Call<List<Container>> call = apiService.getUserContainers(Integer.parseInt(id));

        try {
            Response<List<Container>> response = call.execute();
            if (response.isSuccessful() && response.body() != null) {
                for (Container container : response.body()) {
                    double fullness = ((container.getLength() - container.getLatestDistance()) / container.getLength()) * 100.0;
                    if (fullness >= 80) {
                        return container.getId();  // Return as soon as a full container is detected
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;  // No full container found
    }

    private void sendNotification(String messageBody) {
        NotificationManager notificationManager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Container Alerts", NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(getApplicationContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.red)
                .setContentTitle("Контейнер переповнився")
                .setContentText(messageBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(1, notification);
    }
}
