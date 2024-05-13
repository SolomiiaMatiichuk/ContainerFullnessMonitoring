package com.example.myapplication.ui.home;

import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;

import java.util.Date;


public class TimeValueFormatter extends com.github.mikephil.charting.formatter.ValueFormatter {

    @Override
    public String getFormattedValue(float value) {
        int hour = (int)value / 60;
        int minute = (int)value % 60;
        return String.format("%02d:%02d", hour, minute);
    }
}