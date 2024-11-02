package com.example.myapplication.ui.welcome;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ResetPasswordWaitingActivity extends AppCompatActivity {

    private ProgressBar progressBar;
    private TextView textViewMessage;
    private String email;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_password_waiting);

        progressBar = findViewById(R.id.progressBar);
        textViewMessage = findViewById(R.id.textViewMessage);

        email = getIntent().getStringExtra("email");

        textViewMessage.setText("Посилання для скидання паролю було надіслано на пошту. Підтвердіть, будь ласка.");

        // Periodically check the server for confirmation
        handler.postDelayed(this::checkConfirmationStatus, 5000);  // Check every 5 seconds
    }

    private void checkConfirmationStatus() {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        Call<Boolean> call = apiService.checkPasswordResetConfirmed(email);

        call.enqueue(new Callback<Boolean>() {
            @Override
            public void onResponse(Call<Boolean> call, Response<Boolean> response) {
                if (response.isSuccessful() && Boolean.TRUE.equals(response.body())) {
                    // Navigate to NewPasswordActivity
                    Intent intent = new Intent(ResetPasswordWaitingActivity.this, NewPasswordActivity.class);
                    intent.putExtra("email", email);
                    startActivity(intent);
                    finish();
                } else {
                    // If not confirmed, keep checking
                    handler.postDelayed(ResetPasswordWaitingActivity.this::checkConfirmationStatus, 5000);
                }
            }

            @Override
            public void onFailure(Call<Boolean> call, Throwable t) {
                Toast.makeText(ResetPasswordWaitingActivity.this, "Помилка перевірки статусу підтвердження", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);  // Stop handler when activity is destroyed
    }
}

