package com.example.myapplication.ui.bluetooth;

public class User {
    private int id;
    private String email;
    private String username;

    // Constructors
    public User(int id, String email, String username) {
        this.id = id;
        this.email = email;
        this.username = username;
    }

    // Getters
    public int getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getUsername() {
        return username;
    }

    // Setters (if you need them)
    public void setId(int id) {
        this.id = id;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
