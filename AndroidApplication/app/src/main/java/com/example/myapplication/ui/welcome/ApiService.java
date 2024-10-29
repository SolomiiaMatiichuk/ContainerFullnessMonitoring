package com.example.myapplication.ui.welcome;


import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;

public interface ApiService {

    @POST("/register")
    Call<Void> register(@Body User user);

    @POST("/login")
    Call<LoginResponse> login(@Body User user);
}
