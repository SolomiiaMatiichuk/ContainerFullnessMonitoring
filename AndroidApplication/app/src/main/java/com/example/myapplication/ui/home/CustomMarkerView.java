package com.example.myapplication.ui.home;

import android.content.Context;
import android.widget.TextView;

import com.example.myapplication.R;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class CustomMarkerView extends MarkerView {

    private final TextView tvContent;
    private final DecimalFormat percentageFormat = new DecimalFormat("0.0");
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public CustomMarkerView(Context context, int layoutResource) {
        super(context, layoutResource);
        tvContent = findViewById(R.id.tvContent);
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        // Format and set the time and percentage
        String time = timeFormat.format(convertMinutesToDate((int) e.getX()));
        String percentage = percentageFormat.format(e.getY()) + "%";
        tvContent.setText("Час: " + time + "\nРівень заповненості: " + percentage);
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        return new MPPointF(-(getWidth() / 2), -getHeight());
    }

    // Helper method to convert minutes to a Date object
    private Date convertMinutesToDate(int minutes) {
        Date date = new Date();
        date.setHours(minutes / 60);
        date.setMinutes(minutes % 60);
        return date;
    }
}
