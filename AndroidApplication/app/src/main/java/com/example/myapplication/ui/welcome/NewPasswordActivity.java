package com.example.myapplication.ui.welcome;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.myapplication.R;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;


public class NewPasswordActivity extends AppCompatActivity {

    private EditText newPasswordEditText;
    private EditText confirmPasswordEditText;
    private Button submitButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_password);

        newPasswordEditText = findViewById(R.id.editTextNewPassword);
        confirmPasswordEditText = findViewById(R.id.editTextConfirmPassword);
        submitButton = findViewById(R.id.buttonSubmit);

        submitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newPassword = newPasswordEditText.getText().toString().trim();
                String confirmPassword = confirmPasswordEditText.getText().toString().trim();

                if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                    Toast.makeText(NewPasswordActivity.this, "Будь ласка, введіть і підтвердіть новий пароль!", Toast.LENGTH_SHORT).show();
                } else if (!newPassword.equals(confirmPassword)) {
                    Toast.makeText(NewPasswordActivity.this, "Паролі не співпадають!", Toast.LENGTH_SHORT).show();
                } else {
                    updatePassword(newPassword);
                }
            }
        });
    }

    private void updatePassword(String newPassword) {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);
        String email = getIntent().getStringExtra("email"); // Assuming email is passed to this activity

        NewPasswordRequest request = new NewPasswordRequest(email, newPassword);
        Call<Void> call = apiService.setNewPassword(request);
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(NewPasswordActivity.this, "Пароль успішно оновлено!", Toast.LENGTH_SHORT).show();
                    startActivity(new Intent(NewPasswordActivity.this, LoginActivity.class));
                    finish();
                } else {
                    Toast.makeText(NewPasswordActivity.this, "Помилка при оновленні паролю!", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Toast.makeText(NewPasswordActivity.this, "Помилка: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}