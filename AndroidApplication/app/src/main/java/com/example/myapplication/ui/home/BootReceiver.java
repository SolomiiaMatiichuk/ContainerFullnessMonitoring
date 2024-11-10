package com.example.myapplication.ui.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Schedule your Worker again or start your ForegroundService here
            PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
                    ContainerCheckWorker.class, 15, TimeUnit.MINUTES)
                    .build();
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    "containerCheckWork",
                    ExistingPeriodicWorkPolicy.KEEP,
                    workRequest
            );
        }
    }
}

