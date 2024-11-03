
package com.example.myapplication.ui.welcome;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.example.myapplication.MainActivity;
import com.example.myapplication.R;

public class WelcomeActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);



//        if (getSupportActionBar() != null) {
//            getSupportActionBar().hide();
//        }




        // Check if the user is logged in by checking for a stored token
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String token = prefs.getString("jwt_token", null);

        if (token != null) {
            // If token is found, redirect to main activity
            Intent intent = new Intent(WelcomeActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
            return;
        }


        setContentView(R.layout.activity_welcome);

        findViewById(R.id.button_login).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(WelcomeActivity.this, LoginActivity.class);
                startActivity(intent);
            }
        });

        findViewById(R.id.button_register).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(WelcomeActivity.this, RegisterActivity.class);
                startActivity(intent);
            }
        });
    }
}
