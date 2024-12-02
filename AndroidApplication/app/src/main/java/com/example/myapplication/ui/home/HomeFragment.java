package com.example.myapplication.ui.home;


import static android.content.Context.MODE_PRIVATE;
import static java.lang.Integer.parseInt;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.PopupWindow;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import android.Manifest;
import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentHomeBinding;
import com.github.mikephil.charting.components.LimitLine;
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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import android.content.SharedPreferences;
import android.widget.Toast;

import okhttp3.OkHttpClient;


import retrofit2.Retrofit;
import retrofit2.Response;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.converter.gson.GsonConverterFactory;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


import java.util.Locale;
import java.util.Map;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;


import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TreeSet;
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

    private List<Fullness> fullnessDataList;


    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        chart = rootView.findViewById(R.id.chart);


        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.maps1);
        mapFragment.getMapAsync(this);


        // Schedule WorkManager to check container fullness every 15 minutes
        PeriodicWorkRequest fullnessCheckRequest = new PeriodicWorkRequest.Builder(ContainerCheckWorker.class, 15, TimeUnit.SECONDS)
                .build();

        WorkManager.getInstance(getContext()).enqueueUniquePeriodicWork(
                "containerCheckWork",
                ExistingPeriodicWorkPolicy.KEEP,
                fullnessCheckRequest);

        fetchContainerData();

        createNotificationChannel();

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("some")
                .setContentText("some")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);




        return rootView;
    }

    private final ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    // Notify the user that permission is required
                    Toast.makeText(getContext(), "Notification permission is required for alerts.", Toast.LENGTH_SHORT).show();
                }
            });


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getContext().getSystemService(NotificationManager.class);
            if (notificationManager != null && notificationManager.getNotificationChannel(CHANNEL_ID) == null) {
                CharSequence name = "Container Notifications";
                String description = "Notifications for container fullness";
                int importance = NotificationManager.IMPORTANCE_HIGH;
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
                channel.setDescription(description);
                notificationManager.createNotificationChannel(channel);
            }
        }

        // Check for notification permission on Android 13+ (TIRAMISU)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            // Request the permission
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }


//    private void sendNotification(String message) {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
//                ContextCompat.checkSelfPermission(getContext(), Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//            return; // Exit if permission is not granted
//        }
//
//        Intent intent = new Intent(getContext(), getActivity().getClass());
//        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
//        PendingIntent pendingIntent = PendingIntent.getActivity(getContext(), 0, intent, PendingIntent.FLAG_IMMUTABLE);
//
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(getContext(), CHANNEL_ID)
//                .setSmallIcon(R.drawable.red)
//                .setContentTitle("Контейнер майже заповнився")
//                .setContentText(message)
//                .setPriority(NotificationCompat.PRIORITY_HIGH)
//                .setContentIntent(pendingIntent)
//                .setAutoCancel(true);
//
//        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getContext());
//        notificationManager.notify(NOTIFICATION_ID, builder.build());
//    }



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

        SharedPreferences prefs = getActivity().getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        String id = prefs.getString("id", "1");


        ApiService apiService = retrofit.create(ApiService.class);
        Call<List<Container>> call = apiService.getUserContainers(parseInt(id));

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
            Marker marker = mMap.addMarker(new MarkerOptions()
                    .position(latLng)
                    .title(container.getName())
                    .snippet("ID: " + container.getId() + ",Length: " + container.getLength() + ",LatestDistance: " + container.getLatestDistance()));
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

            if (markers.size() == 1) {
                // Add buffer around a single marker to avoid a too-close zoom
                double bufferDistance = 0.05;
                LatLng singleMarker = markers.get(0).getPosition();
                bounds = new LatLngBounds(
                        new LatLng(singleMarker.latitude - bufferDistance, singleMarker.longitude - bufferDistance),
                        new LatLng(singleMarker.latitude + bufferDistance, singleMarker.longitude + bufferDistance)
                );
            }

            CameraUpdate cu = CameraUpdateFactory.newLatLngBounds(bounds, 300);
            mMap.animateCamera(cu);
        }
    }

    private void setMarkerColor(Marker marker, double current_distance, double length) {
        int iconResource;
        double fullness =calculatePercentage(current_distance,length);
        if (fullness < 50) {
            iconResource = R.drawable.green;
        } else if (fullness >= 50 && fullness < 80) {
            iconResource = R.drawable.yellow;
        } else {
            iconResource = R.drawable.red;

        }

        Bitmap originalBitmap = BitmapFactory.decodeResource(getResources(), iconResource);
        int width = 100; // Specify your desired width
        int height = 100; // Specify your desired height
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, false);
        marker.setIcon(BitmapDescriptorFactory.fromBitmap(resizedBitmap));
    }






    @Override
    public boolean onMarkerClick(Marker marker) {
        // Display information about the container when its marker is clicked
        //Toast.makeText(getContext(), marker.getTitle() + "\n" + marker.getSnippet(), Toast.LENGTH_SHORT).show();
        showPopupWindow(marker);
        return false;
    }
    private double calculatePercentage(double fullness, double containerLength) {
        double percentage = ((containerLength - fullness) / containerLength) * 100.0;
        return Math.max(percentage, 0); // Ensure no negative percentages
    }



    private void showTrendsPopup(List<Fullness> fullnessDataList, double containerLength) {
        AlertDialog.Builder alert = new AlertDialog.Builder(requireContext());
        alert.setTitle("Статистика між періодами");

        View customLayout = LayoutInflater.from(requireContext()).inflate(R.layout.popup_trends, null);
        TableLayout trendsTable = customLayout.findViewById(R.id.trends_table);
        Spinner startDateSpinner = customLayout.findViewById(R.id.start_date_spinner);
        Spinner endDateSpinner = customLayout.findViewById(R.id.end_date_spinner);

        // Extract and sort unique dates
        List<String> dateOptions = extractUniqueDates(fullnessDataList); // Sorted in ascending order
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, dateOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        startDateSpinner.setAdapter(adapter);
        endDateSpinner.setAdapter(adapter);

        // Set default selections
        startDateSpinner.setSelection(0); // Earliest date
        endDateSpinner.setSelection(dateOptions.size() - 1); // Latest date

        // Add listener to spinners to update the table when dates are changed
        AdapterView.OnItemSelectedListener dateChangeListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String startDateStr = (String) startDateSpinner.getSelectedItem();
                String endDateStr = (String) endDateSpinner.getSelectedItem();

                if (startDateStr != null && endDateStr != null) {
                    Date startDate = parseDate(startDateStr);
                    Date endDate = parseDate(endDateStr);

                    if (startDate != null && endDate != null && !startDate.after(endDate)) {
                        updateTrendsTable(trendsTable, filterDataByDateRange(fullnessDataList, startDate, endDate), containerLength);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        };

        startDateSpinner.setOnItemSelectedListener(dateChangeListener);
        endDateSpinner.setOnItemSelectedListener(dateChangeListener);

        // Initialize table with full data
        updateTrendsTable(trendsTable, filterDataByDateRange(fullnessDataList, parseDate(dateOptions.get(0)), parseDate(dateOptions.get(dateOptions.size() - 1))), containerLength);

        alert.setView(customLayout);
        alert.setPositiveButton("OK", null);
        alert.show();
    }

    // Extract unique and sorted dates from Fullness data
    // Extract unique and sorted dates from Fullness data
    private List<String> extractUniqueDates(List<Fullness> fullnessDataList) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
        TreeSet<Date> uniqueDates = new TreeSet<>();

        for (Fullness data : fullnessDataList) {
            // Truncate time part to ensure uniqueness by date only
            uniqueDates.add(truncateToDate(data.getTimestamp()));
        }

        // Convert sorted dates back to string format
        List<String> sortedDates = new ArrayList<>();
        for (Date date : uniqueDates) {
            sortedDates.add(dateFormat.format(date));
        }

        return sortedDates;
    }

    // Helper method to truncate a Date object to just the date part
    private Date truncateToDate(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }


    // Parse string date to Date object
    private Date parseDate(String dateStr) {
        try {
            return new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(dateStr);
        } catch (ParseException e) {
            e.printStackTrace();
            return null;
        }
    }

    // Filter Fullness data by date range
    private List<Fullness> filterDataByDateRange(List<Fullness> fullnessDataList, Date startDate, Date endDate) {
        // Truncate startDate and endDate to remove time components
        Date truncatedStartDate = truncateToDate(startDate);
        Date truncatedEndDate = truncateToDate(endDate);

        List<Fullness> filteredData = new ArrayList<>();
        for (Fullness data : fullnessDataList) {
            // Truncate timestamp to remove time components
            Date truncatedTimestamp = truncateToDate(data.getTimestamp());

            // Check if timestamp is within the inclusive range
            if (!truncatedTimestamp.before(truncatedStartDate) && !truncatedTimestamp.after(truncatedEndDate)) {
                filteredData.add(data);
            }
        }

        return filteredData;
    }

    // Update trends table dynamically
    private void updateTrendsTable(TableLayout trendsTable, List<Fullness> filteredData, double containerLength) {
        // Clear existing rows except header
        trendsTable.removeViews(1, trendsTable.getChildCount() - 1);

        // Add trend rows
        addTableRow(trendsTable, "Середнє заповнення за період",
                String.format("%.1f", calculateAverageFullness(filteredData, containerLength)) + "%");
        addTableRow(trendsTable, "Кількість очищень за період",
                      String.format("%d", countOfClean(filteredData, containerLength)));
        addTableRow(trendsTable, "Кількість переповнень за період",
                String.format("%d", countOverflows(filteredData, containerLength)));
        addTableRow(trendsTable, "Максимальне заповнення",
                String.format("%.1f", calculateMaxFullness(filteredData, containerLength)) + "%");
        addTableRow(trendsTable, "Мінімальне заповнення",
                String.format("%.1f", calculateMinFullness(filteredData, containerLength)) + "%");
    }

    private int countOverflows(List<Fullness> filteredData, double containerLength) {
        int overflowCount = 0;
        boolean wasBelowThreshold = false;

        for (Fullness data : filteredData) {
            // Calculate the fullness percentage
            double percentage = ((containerLength - data.getFullness()) / containerLength) * 100.0;

            // Check for transition from <90% to >90%
            if (percentage < 90) {
                wasBelowThreshold = true; // Set the flag if fullness is below 90%
            } else if (wasBelowThreshold && percentage > 90) {
                overflowCount++; // Count an overflow
                wasBelowThreshold = false; // Reset the flag after an overflow
            }
        }

        return overflowCount;
    }


    private int countOfClean(List<Fullness> filteredData, double containerLength) {
        int cleanCount = 0;
        boolean wasAboveThreshold = false;

        for (Fullness data : filteredData) {
            double percentage = calculatePercentage(data.getFullness(), containerLength);
            if (percentage > 15) {
                wasAboveThreshold = true;
            } else if (wasAboveThreshold && percentage < 10) {
                cleanCount++;
                wasAboveThreshold = false;
            }
        }

        return cleanCount;
    }




    // Helper function to add rows to the table
    private void addTableRow(TableLayout tableLayout, String label, String value) {
        TableRow row = new TableRow(getContext());
        row.setBackgroundResource(R.drawable.item_border); // Apply border drawable to each row

        TextView labelView = new TextView(getContext());
        labelView.setText(label);
        labelView.setPadding(8, 8, 8, 8);
        labelView.setTextSize(14);
        labelView.setTextColor(Color.BLACK);
        labelView.setSingleLine(false); // Allow multi-line wrapping
        labelView.setEllipsize(null); // Prevent truncation
        labelView.setLayoutParams(new TableRow.LayoutParams(
                0, TableRow.LayoutParams.WRAP_CONTENT, 1f)); // Use layout weight for even column distribution

        TextView valueView = new TextView(getContext());
        valueView.setText(value);
        valueView.setPadding(8, 8, 8, 8);
        valueView.setTextSize(14);
        valueView.setTextColor(Color.BLACK);
        valueView.setLayoutParams(new TableRow.LayoutParams(
                0, TableRow.LayoutParams.WRAP_CONTENT, 1f)); // Use layout weight for even column distribution

        row.addView(labelView);
        row.addView(valueView);

        tableLayout.addView(row);
    }





    // Function to calculate average fullness
    private double calculateAverageFullness(List<Fullness> fullnessDataList, double containerLength) {
        if (fullnessDataList.isEmpty()) {
            return 0; // Avoid division by zero
        }

        double weightedSum = 0;
        double totalTime = 0;

        for (int i = 0; i < fullnessDataList.size() - 1; i++) {
            Fullness current = fullnessDataList.get(i);
            Fullness next = fullnessDataList.get(i + 1);

            // Calculate fullness percentage for the current data point
            double currentPercentage = calculatePercentage(current.getFullness(), containerLength);

            // Calculate the time difference between two points
            double timeInterval = next.getTimestamp().getTime() - current.getTimestamp().getTime();

            // Add weighted fullness to the total
            weightedSum += currentPercentage * timeInterval;

            // Accumulate total time
            totalTime += timeInterval;
        }

        // Return the weighted average fullness
        return totalTime == 0 ? 0 : weightedSum / totalTime;
    }

    // Function to calculate maximum fullness
    private double calculateMaxFullness(List<Fullness> fullnessDataList, double containerLength) {
        double max = 0;
        for (Fullness data : fullnessDataList) {
            double fullness = calculatePercentage(data.getFullness(),containerLength);
            if (fullness > max) {
                max = fullness;
            }
        }
        return max;
    }

    // Function to calculate minimum fullness
    private double calculateMinFullness(List<Fullness> fullnessDataList, double containerLength) {
        double min = 100;
        for (Fullness data : fullnessDataList) {
            double fullness = calculatePercentage(data.getFullness(),containerLength);
            if (fullness < min) {
                min = fullness;
            }
        }
        return min;
    }



    private void showInfoPopup(int containerId, double containerLength, double latestDistance) {
        LayoutInflater inflater = getLayoutInflater();
        View alertLayout = inflater.inflate(R.layout.popup_info, null);

        TableLayout containerDetailsTable = alertLayout.findViewById(R.id.container_details_table);
        Button btnTrends = alertLayout.findViewById(R.id.btnTrends);

        if (fullnessDataList != null && !fullnessDataList.isEmpty()) {
            Fullness latestFullness = Collections.max(fullnessDataList, Comparator.comparing(Fullness::getTimestamp));
            double lastFullnessPercentage = ((containerLength - latestFullness.getFullness()) / containerLength) * 100.0;

            String lastCleaningDateTime = findLastCleaningDateTime(fullnessDataList, containerLength);

            Date latestDate = latestFullness.getTimestamp();

            // Calculate average filling rate excluding cleaning
            double averageFillingRate = calculateAverageFillRateExcludingCleaning(fullnessDataList, containerLength);

            // Populate the table with details
            populateContainerDetailsTable(
                    containerDetailsTable,
                    containerId,
                    containerLength,
                    Math.max(lastFullnessPercentage, 0),
                    new SimpleDateFormat("dd.MM.yyyy HH:mm").format(latestDate),
                    lastCleaningDateTime.isEmpty() ? "Немає даних" : lastCleaningDateTime,
                    averageFillingRate
            );
        } else {
            // Populate with "no data" messages
            populateContainerDetailsTable(
                    containerDetailsTable,
                    containerId,
                    containerLength,
                    -1, // No fullness available
                    "Немає даних",
                    "Немає даних",
                    0 // No average rate available
            );
        }

        btnTrends.setOnClickListener(v -> showTrendsPopup(fullnessDataList, containerLength));

        AlertDialog.Builder alert = new AlertDialog.Builder(getContext());
        alert.setTitle("Інформація про контейнер");
        alert.setView(alertLayout);
        alert.setPositiveButton("OK", null);
        AlertDialog dialog = alert.create();
        dialog.show();
    }

    // Helper method to populate the table
    private void populateContainerDetailsTable(TableLayout tableLayout, int containerId, double containerLength, double fullnessPercentage, String lastUpdate, String lastCleaningDateTime, double averageFillingRate) {
        tableLayout.removeAllViews();

        addTableRow(tableLayout, "ID контейнера", String.valueOf(containerId));
        addTableRow(tableLayout, "Довжина контейнера", containerLength + " см");
        if (fullnessPercentage >= 0) {
            addTableRow(tableLayout, "Заповненість", String.format("%.1f%%", fullnessPercentage));
        } else {
            addTableRow(tableLayout, "Заповненість", "Немає даних");
        }
        addTableRow(tableLayout, "Дата та час останнього оновлення", lastUpdate);
        addTableRow(tableLayout, "Дата та час останнього очищення", lastCleaningDateTime);
        addTableRow(tableLayout, "Середня швидкість заповнення", String.format("%.2f%%/год", averageFillingRate));
    }

    // Helper method to calculate average filling rate excluding cleaning
    private double calculateAverageFillRateExcludingCleaning(List<Fullness> data, double containerLength) {
        if (data.size() < 2) {
            return 0; // Не вистачає даних для розрахунку
        }

        double totalFillChange = 0; // Сумарна зміна заповнення
        double totalTimeHours = 0; // Сумарний часовий інтервал (у годинах)
        boolean wasFilling = false;

        for (int i = 1; i < data.size(); i++) {
            Fullness previous = data.get(i - 1);
            Fullness current = data.get(i);

            // Обчислюємо рівень заповнення у відсотках
            double previousPercentage = ((containerLength - previous.getFullness()) / containerLength) * 100.0;
            double currentPercentage = ((containerLength - current.getFullness()) / containerLength) * 100.0;

            // Check if it's a filling event
            if (currentPercentage > previousPercentage) {
                wasFilling = true;
                totalFillChange += currentPercentage - previousPercentage;

                // Додаємо часовий інтервал у годинах
                long timeDiffMillis = current.getTimestamp().getTime() - previous.getTimestamp().getTime();
                totalTimeHours += timeDiffMillis / (1000.0 * 60 * 60); // Перетворюємо мілісекунди у години
            } else {
                wasFilling = false; // Ignore if it's a cleaning event
            }
        }

        // Розраховуємо середню швидкість заповнення
        return totalTimeHours > 0 ? totalFillChange / totalTimeHours : 0;
    }




    private String findLastCleaningDateTime(List<Fullness> fullnessDataList, double containerLength) {
        SimpleDateFormat dateTimeFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault());
        boolean wasAboveThreshold = false; // Flag to track if fullness was above 15%

        // Iterate through the list in reverse to find the most recent cleaning
        for (int i = fullnessDataList.size() - 1; i >= 0; i--) {
            Fullness data = fullnessDataList.get(i);
            double percentage = ((containerLength - data.getFullness()) / containerLength) * 100.0;

            if (percentage > 15) {
                wasAboveThreshold = true;
            } else if (wasAboveThreshold && percentage < 10) {
                return dateTimeFormat.format(data.getTimestamp()); // Return the cleaning date and time
            }
        }

        return ""; // No cleaning found
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

        int containerId = parseInt(marker.getSnippet().split(",")[0].split(": ")[1]);
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
        setButtonTransparency(btnNext, false);
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
                    fullnessDataList = response.body();
                    Collections.sort(fullnessDataList, (data1, data2) -> data1.getTimestamp().compareTo(data2.getTimestamp()));
                    if (fullnessDataList != null) {
                        displayFullnessPlot(containerLength);
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

    private void displayFullnessPlot(double containerLength) {
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


            if (isToday(filteredData.get(0).getTimestamp())) {

                // Add the current time entry with the last fullness value
                if (!filteredData.isEmpty()) {
                    Fullness lastFullness = filteredData.get(filteredData.size() - 1);
                    Calendar calendar = Calendar.getInstance();
                    long currentMinutes = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE);
                    float lastFullnessPercentage = (float) (((current_container_length - lastFullness.getFullness()) / current_container_length) * 100.0);
                    entries.add(new Entry(currentMinutes, lastFullnessPercentage));
                }
            }
            else
            {
                // Add the current time entry with the last fullness value
                if (!filteredData.isEmpty()) {
                    Fullness lastFullness = filteredData.get(0);
                    Calendar calendar = Calendar.getInstance();
                    long endOfDayMinutes = 1440; // 24:00 in minutes
                    float lastFullnessPercentage = (float) (((current_container_length - lastFullness.getFullness()) / current_container_length) * 100.0);
                    entries.add(new Entry(endOfDayMinutes, lastFullnessPercentage));
                }
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

        // Apply Fill to create a background gradient
        dataSet.setDrawFilled(true);
        if (Build.VERSION.SDK_INT >= 18) { // Fill background only supported on API level 18 and above
            dataSet.setFillDrawable(createGradientDrawable());
        } else {
            dataSet.setFillColor(Color.LTGRAY); // Fallback for lower API levels
        }

        LineData lineData = new LineData(dataSet);
        chart.setData(lineData);

        chart.setDrawGridBackground(true);  // Enable grid background
        chart.setGridBackgroundColor(Color.parseColor("#E6E6E6")); // Light gray background color


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
        xAxis.setTextSize(14f); // Increase text size for X-axis

        // Customize Y-axis and lock it from panning
        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(0);
        leftAxis.setAxisMaximum(100);
        leftAxis.setGranularity(10);
        leftAxis.setTextSize(14f); // Increase text size for Y-axis
        chart.getAxisRight().setEnabled(false);

        chart.setScaleYEnabled(false);
        chart.setScaleXEnabled(true);
        chart.setDragEnabled(true);

        // Add extra bottom offset for padding
        chart.setExtraBottomOffset(10f); // Adjust as needed for your layout

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

    private Drawable createGradientDrawable() {
        GradientDrawable gradientDrawable = new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{Color.parseColor("#8000FF00"), Color.parseColor("#80FFFF00"), Color.parseColor("#80FF0000")});
        gradientDrawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        return gradientDrawable;
    }


    // Method to set transparency for a button when disabled
    private void setButtonTransparency(Button button, boolean enabled) {
        if (enabled) {
            button.setAlpha(1f);  // Fully opaque when enabled
        } else {
            button.setAlpha(0.7f);  // 50% transparent when disabled
        }
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
        setButtonTransparency(btnNext, !isToday(current_date));

        showPlot();
    }
}

