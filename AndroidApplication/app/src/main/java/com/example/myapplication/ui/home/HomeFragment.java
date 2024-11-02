package com.example.myapplication.ui.home;


import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentHomeBinding;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;

import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.CameraUpdateFactory;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import android.content.SharedPreferences;

import okhttp3.OkHttpClient;


import retrofit2.Retrofit;
import retrofit2.Response;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.converter.gson.GsonConverterFactory;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


import java.util.Map;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;

public class HomeFragment extends Fragment implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener{
    private LineChart chart;
    private Handler handler = new Handler(Looper.getMainLooper());
    private final int delay = 60000; // Fetch data every 60 seconds
    private MapView mapView;
    private GoogleMap mMap;
    private List<Marker> markers = new ArrayList<>();
    private FragmentHomeBinding binding;

    private Runnable runnable;

    private PopupWindow popupWindow;

    private Map<Date, List<Fullness>> fullnessDataMap;

    private Date current_date;

    private double current_container_length;

    private static final String CHANNEL_ID = "container_notifications";
    private static final int NOTIFICATION_ID = 1;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        chart = rootView.findViewById(R.id.chart);


        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.maps1);
        mapFragment.getMapAsync(this);


        // Schedule WorkManager to check container fullness every 1 minutes
        PeriodicWorkRequest fullnessCheckRequest = new PeriodicWorkRequest.Builder(ContainerCheckWorker.class, 1, TimeUnit.MINUTES)
                .build();

        WorkManager.getInstance(getContext()).enqueue(fullnessCheckRequest);

       // createNotificationChannel();

        return rootView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        handler.removeCallbacks(runnable);
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Set the onMarkerClickListener to listen for clicks on markers
        mMap.setOnMarkerClickListener(this);

        // Fetch data immediately
        fetchContainerData();

        // Schedule a task to fetch data periodically
        handler.postDelayed(runnable = new Runnable() {
            public void run() {
                fetchContainerData();
                handler.postDelayed(this, delay);
            }
        }, delay);
    }

    

    private void fetchContainerData() {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://container-monitoring-server-5e33a8983798.herokuapp.com/") // Change this URL accordingly
                .addConverterFactory(GsonConverterFactory.create())
                .client(new OkHttpClient())
                .build();


        ApiService apiService = retrofit.create(ApiService.class);
        Call<List<Container>> call = apiService.getUserContainers(1); // Hardcoded user_id for now

        call.enqueue(new Callback<List<Container>>() {
            @Override
            public void onResponse(@NotNull Call<List<Container>> call, @NotNull retrofit2.Response<List<Container>> response) {
                if (response.isSuccessful()) {
                    List<Container> containerList = response.body();

                    if (containerList != null) {
                        updateMarkers(containerList);
                    }
                }
            }

            @Override
            public void onFailure(@NotNull Call<List<Container>> call, @NotNull Throwable t) {
                t.printStackTrace();
            }
        });

    }


    private void updateMarkers(List<Container> containerList) {
        // Clear previous markers
        for (Marker marker : markers) {
            marker.remove();
        }
        markers.clear();

        // Add new markers
        for (Container container : containerList) {
            LatLng latLng = new LatLng(container.getLatitude(), container.getLongitude());
            Marker marker = mMap.addMarker(new MarkerOptions().position(latLng).title(container.getName()).snippet("ID: " + container.getId() + ",Length: " + container.getLength() + ",LatestDistance: " + container.getLatestDistance()));
            setMarkerColor(marker, container.getLatestDistance(), container.getLength());
            markers.add(marker);
        }

        // Resize the map to fit all markers
        if (!markers.isEmpty()) {
            LatLngBounds.Builder builder = new LatLngBounds.Builder();
            for (Marker marker : markers) {
                builder.include(marker.getPosition());
            }
            LatLngBounds bounds = builder.build();
            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, 300);
            mMap.animateCamera(cu);
        }
    }

    private void setMarkerColor(Marker marker, double current_distance, double length) {
        int iconResource;
        double fullness = ((length - current_distance)/length)*100.0;
        if (fullness < 50) {
            iconResource = R.drawable.green;
        } else if (fullness >= 50 && fullness < 80) {
            iconResource = R.drawable.yellow;
        } else {
            iconResource = R.drawable.red;

            // Check notification setting and send notification

           // if (isNotificationsEnabled(getContext())) {
           //     sendNotification("Контейнер  " + marker.getTitle() + " є на " + (int)fullness + "% заповнений");
           // }
        }

        Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), iconResource);
        int width = 100; // Specify your desired width
        int height = 100; // Specify your desired height
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, false);
        marker.setIcon(BitmapDescriptorFactory.fromBitmap(resizedBitmap));
    }

    private boolean isNotificationsEnabled(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        return sharedPreferences.getBoolean("notifications_enabled", false);

    }



    private void sendNotification(String message) {
        Intent intent = new Intent(getContext(), getActivity().getClass());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        // Replace your PendingIntent creation code with this
        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
                .setSmallIcon(R.drawable.red)
                .setContentTitle("Контейнер майже заповнився")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
        notificationManager.notify(NOTIFICATION_ID, builder.build());

    }



    private void createNotificationChannel() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Container Notifications";
            String description = "Notifications for container fullness";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);

        }

    }


    @Override
    public boolean onMarkerClick(Marker marker) {
        // Display information about the container when its marker is clicked
        //Toast.makeText(getContext(), marker.getTitle() + "\n" + marker.getSnippet(), Toast.LENGTH_SHORT).show();
        showPopupWindow(marker);
        return false;
    }

    private void showInfoPopup(int containerId, double containerLength, double latestDistance) {
        LayoutInflater inflater = getLayoutInflater();
        View alertLayout = inflater.inflate(R.layout.popup_info, null);

        TextView textId = alertLayout.findViewById(R.id.text_id);
        TextView textFullness = alertLayout.findViewById(R.id.fullness_info);
        TextView textLength = alertLayout.findViewById(R.id.text_length);
        TextView textLastDate = alertLayout.findViewById(R.id.text_last_date);

        // Set your data here
        textId.setText(Integer.toString(containerId));
        textLength.setText(Double.toString(containerLength));
        textFullness.setText(String.format("%.1f",((containerLength - latestDistance)/containerLength) *100.0) + "%");
        textLastDate.setText("26.05.2024"); //currently test data

        AlertDialog.Builder alert = new AlertDialog.Builder(getContext());
        alert.setTitle("Інформація про контейнер");
        alert.setView(alertLayout);
        alert.setPositiveButton("OK", null);
        AlertDialog dialog = alert.create();
        dialog.show();
    }

    private void showPopupWindow(Marker marker) {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }

        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.popup_chart, null);

        chart = popupView.findViewById(R.id.chart);
        Button btnPrevious = popupView.findViewById(R.id.btnPrevious);
        Button btnNext = popupView.findViewById(R.id.btnNext);
        Button btnClose = popupView.findViewById(R.id.btnClose);
        Button btnInfo = popupView.findViewById(R.id.btnInfo);
        TextView textview = popupView.findViewById(R.id.tvCurrentDate);

        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");

        current_date = truncateTime(new Date());
        String strDate = dateFormat.format(current_date);
        textview.setText(strDate);

        int containerId = Integer.parseInt(marker.getSnippet().split(",")[0].split(": ")[1]);
        double containerLength = Double.parseDouble(marker.getSnippet().split(",")[1].split(": ")[1]);
        double latestDistance = Double.parseDouble(marker.getSnippet().split(",")[2].split(": ")[1]);
        showFullnessPlot(containerId, containerLength);

        //fetchFullnessData(Integer.parseInt(marker.getTitle()), Double.parseDouble(marker.getSnippet()));

        popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.showAtLocation(popupView, Gravity.CENTER, 0, 0);

        btnClose.setOnClickListener(view -> popupWindow.dismiss());
        btnPrevious.setOnClickListener(view -> navigateDate(btnNext, textview, -1));
        btnNext.setOnClickListener(view -> navigateDate(btnNext, textview, 1));

        btnInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showInfoPopup(containerId, containerLength, latestDistance);
            }
        });

        btnNext.setEnabled(false);
    }


    private void showFullnessPlot(int containerId, double containerLength) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Date.class, new DateDeserializer())
                .create();
        // Fetch fullness data for the selected container
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://container-monitoring-server-5e33a8983798.herokuapp.com/")
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();

        ApiService apiService = retrofit.create(ApiService.class);
        Call<List<Fullness>> call = apiService.getContainerFullness(containerId);

        call.enqueue(new Callback<List<Fullness>>() {
            @Override
            public void onResponse(@NotNull Call<List<Fullness>> call, @NotNull Response<List<Fullness>> response) {
                if (response.isSuccessful()) {
                    List<Fullness> fullnessDataList = response.body();
                    if (fullnessDataList != null) {
                        displayFullnessPlot(fullnessDataList, containerLength);
                    }
                }
            }

            @Override
            public void onFailure(@NotNull Call<List<Fullness>> call, @NotNull Throwable t) {
                t.printStackTrace();
            }
        });

    }

    // Helper method to truncate time from Date
    private Date truncateTime(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }

    private void displayFullnessPlot(List<Fullness> fullnessDataList, double containerLength) {
        // Set up chart visibility and background color
        chart.setVisibility(View.VISIBLE);
        chart.setBackgroundColor(Color.WHITE);

        // Initialize fullness data map
        fullnessDataMap = new HashMap<>();
        for (Fullness data : fullnessDataList) {
            Date date = truncateTime(data.getTimestamp()); // Remove time from date
            if (!fullnessDataMap.containsKey(date)) {
                fullnessDataMap.put(date, new ArrayList<>());
            }
            fullnessDataMap.get(date).add(data);
        }
        current_container_length = containerLength;

        // Show plot for the current date
        showPlot();
    }

    private void showPlot() {
        List<Fullness> dateFullness = fullnessDataMap.get(current_date);
        List<Entry> entries = new ArrayList<>();

        if (dateFullness != null) {
            List<Fullness> filteredData = new ArrayList<>();

            // Retain the first point
            if (!dateFullness.isEmpty()) {
                filteredData.add(dateFullness.get(0));
            }

            // Filter out points with less than 1% change in fullness
            for (int i = 1; i < dateFullness.size() - 1; i++) {
                double prevFullness = ((current_container_length - dateFullness.get(i - 1).getFullness()) / current_container_length) * 100.0;
                double currentFullness = ((current_container_length - dateFullness.get(i).getFullness()) / current_container_length) * 100.0;

                if (Math.abs(currentFullness - prevFullness) >= 1.0) {
                    filteredData.add(dateFullness.get(i));
                }
            }

            // Retain the last point
            if (!dateFullness.isEmpty()) {
                filteredData.add(dateFullness.get(dateFullness.size() - 1));
            }

            // Populate entries for the chart
            for (Fullness data : filteredData) {
                long minutes = (data.getTimestamp().getHours() * 60 + data.getTimestamp().getMinutes()) % 1440;
                float fullnessPercentage = (float) (((current_container_length - data.getFullness()) / current_container_length) * 100.0);
                entries.add(new Entry(minutes, fullnessPercentage));
            }

            // Add the current time entry with the last fullness value
            if (!filteredData.isEmpty()) {
                Fullness lastFullness = filteredData.get(0);
                Calendar calendar = Calendar.getInstance();
                long currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
                float lastFullnessPercentage = (float) (((current_container_length - lastFullness.getFullness()) / current_container_length) * 100.0);
                entries.add(new Entry(currentMinutes, lastFullnessPercentage));
            }

            // Sort entries by time (X-axis)
            entries.sort(Comparator.comparing(Entry::getX));
        }

        LineDataSet dataSet = new LineDataSet(entries, "Fullness Data");
        dataSet.setColor(Color.BLUE);
        dataSet.setCircleColor(Color.RED);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        // Remove the legend
        chart.getLegend().setEnabled(false);

        // Set the custom marker view
        CustomMarkerView markerView = new CustomMarkerView(getContext(), R.layout.marker_view);
        chart.setMarker(markerView);

        // Customize X-axis
        XAxis xAxis = chart.getXAxis();
        xAxis.setAxisMinimum(0);
        xAxis.setAxisMaximum(1440);
        xAxis.setValueFormatter(new TimeValueFormatter());
        xAxis.setGranularity(1);
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextSize(14f);

        // Customize Y-axis and lock it from panning
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(0);
        leftAxis.setAxisMaximum(100);
        leftAxis.setGranularity(10);
        leftAxis.setTextSize(14f);
        chart.getAxisRight().setEnabled(false);

        chart.setScaleYEnabled(false);
        chart.setScaleXEnabled(true);
        chart.setDragEnabled(true);

        // Add extra bottom offset for padding
        chart.setExtraBottomOffset(10f);

        // Set initial zoom so the last visible X (right side) is current time
        if (!entries.isEmpty()) {
            float currentMinutes = (float) (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) * 60 + Calendar.getInstance().get(Calendar.MINUTE));
            float visibleRangeX = 180f;

            chart.fitScreen();
            chart.zoom(1440f / visibleRangeX, 1f, 0, 0);

            chart.moveViewToX(currentMinutes - visibleRangeX / 2);
        }

        chart.getDescription().setEnabled(false);
        chart.invalidate();
    }






    private boolean isToday(Date date) {
        Calendar today = Calendar.getInstance();
        Calendar targetDate = Calendar.getInstance();
        targetDate.setTime(date);
        return today.get(Calendar.YEAR) == targetDate.get(Calendar.YEAR)
                && today.get(Calendar.DAY_OF_YEAR) == targetDate.get(Calendar.DAY_OF_YEAR);
    }

    private void navigateDate(Button btnNext, TextView textView, int dir)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(current_date);

        calendar.add(Calendar.DAY_OF_MONTH, dir);

        current_date = truncateTime(calendar.getTime());

        DateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy");
        String strDate = dateFormat.format(current_date);


        textView.setText(strDate);


        btnNext.setEnabled(!isToday(current_date));

        showPlot();
    }
}

