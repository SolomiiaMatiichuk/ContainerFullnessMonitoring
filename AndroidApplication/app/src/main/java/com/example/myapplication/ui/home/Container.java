package com.example.myapplication.ui.home;

import com.google.gson.annotations.SerializedName;

public class Container {
    @SerializedName("id")
    private int id;
    @SerializedName("name")
    private String name;
    @SerializedName("latitude")
    private double latitude;
    @SerializedName("longitude")
    private double longitude;
    @SerializedName("length")
    private double length;

    @SerializedName("latest_distance")
    private double latest_distance;

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLength() {
        return length;
    }

    public double getLatestDistance() {
        return latest_distance;
    }
}
