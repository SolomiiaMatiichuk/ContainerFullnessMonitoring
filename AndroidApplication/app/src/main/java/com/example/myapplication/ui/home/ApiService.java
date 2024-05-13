package com.example.myapplication.ui.home;

import com.example.myapplication.ui.home.Container;

import java.util.List;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface ApiService {
    @GET("user-containers/{user_id}")
    Call<List<Container>> getUserContainers(@Path("user_id") int userId);


    @GET("container-fullness/{container_id}")
    Call<List<Fullness>> getContainerFullness(@Path("container_id") int containerId);
}
