package com.example.myapplication.ui.home;

import com.google.gson.annotations.SerializedName;

import java.util.Date;

public class Fullness {
    @SerializedName("fullness")
    private int fullness;

    @SerializedName("timestamp")
    private Date timestamp;

    public int getFullness() {
        return fullness;
    }

    public Date getTimestamp() {
        return timestamp;
    }
}
