package com.example.myapplication.ui.welcome;

public class LoginResponse {
    private String access_token;
    private String real_name;
    private String email;

    private String role;

    private String id;

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


    // Getter for role
    public String getRole() {
        return role;
    }

    public String getId() {
        return id;
    }
}
