package com.example.myapplication.ui.welcome;

public class LoginResponse {
    private String access_token;
    private String real_name;
    private String email;

    public String getAccessToken() {
        return access_token;
    }

    // Getter for real name
    public String getRealName() {
        return real_name;
    }

    // Getter for email
    public String getEmail() {
        return email;
    }
}
