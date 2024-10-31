package com.example.myapplication.ui.welcome;


import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    @POST("/register")
    Call<Void> register(@Body User user);

    @POST("/login")
    Call<LoginResponse> login(@Body User user);

    @GET("/check_confirmation")
    Call<Boolean> checkEmailConfirmation(@Query("email") String email);

}
