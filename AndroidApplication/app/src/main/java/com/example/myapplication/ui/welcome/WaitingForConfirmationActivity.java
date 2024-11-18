package com.example.myapplication.ui.welcome;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;
import com.example.myapplication.ui.welcome.ApiClient;
import com.example.myapplication.ui.welcome.ApiService;
import com.example.myapplication.ui.welcome.LoginActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WaitingForConfirmationActivity extends AppCompatActivity {
    private String email;
    private Handler handler = new Handler();
    private static final int POLL_INTERVAL = 5000; // 5 seconds

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_for_confirmation);

        email = getIntent().getStringExtra("email");

        TextView statusText = findViewById(R.id.statusText);
        statusText.setText("Будь ласка, перевірте вашу електронну пошту і підтвердьте реєстрацію. Після цього ви зможете ввійти в профіль.");

        // Start polling
        handler.postDelayed(checkConfirmationStatus, POLL_INTERVAL);
    }

    private Runnable checkConfirmationStatus = new Runnable() {
        @Override
        public void run() {
            ApiService apiService = ApiClient.getClient().create(ApiService.class);
            apiService.checkEmailConfirmation(email).enqueue(new Callback<Boolean>() {
                @Override
                public void onResponse(Call<Boolean> call, Response<Boolean> response) {
                    if (response.isSuccessful() && response.body() != null && response.body()) {
                        Toast.makeText(WaitingForConfirmationActivity.this, "Реєстрація успішна! Тепер ви можете ввійти в акаунт!", Toast.LENGTH_SHORT).show();
                        // Navigate to login screen
                        Intent intent = new Intent(WaitingForConfirmationActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    } else {
                        // Retry if not yet confirmed
                        handler.postDelayed(checkConfirmationStatus, POLL_INTERVAL);
                    }
                }

                @Override
                public void onFailure(Call<Boolean> call, Throwable t) {
                    Toast.makeText(WaitingForConfirmationActivity.this, "Помилка перевірки статусу. Пробуємо ще раз...", Toast.LENGTH_SHORT).show();
                    handler.postDelayed(checkConfirmationStatus, POLL_INTERVAL);
                }
            });
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Remove callbacks to prevent memory leaks
        handler.removeCallbacks(checkConfirmationStatus);
    }
}
