package com.example.myapplication.ui.home;

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

import com.example.myapplication.R;
import com.example.myapplication.databinding.FragmentHomeBinding;
import com.github.mikephil.charting.components.XAxis;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;

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

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_home, container, false);

        chart = rootView.findViewById(R.id.chart);


        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.maps1);
        mapFragment.getMapAsync(this);

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
            Marker marker = mMap.addMarker(new MarkerOptions().position(latLng).title(container.getName()).snippet("ID: " + container.getId() + ",Length: " + container.getLength()));
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
        showFullnessPlot(containerId, containerLength);

        //fetchFullnessData(Integer.parseInt(marker.getTitle()), Double.parseDouble(marker.getSnippet()));

        popupWindow = new PopupWindow(popupView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.showAtLocation(popupView, Gravity.CENTER, 0, 0);

        btnClose.setOnClickListener(view -> popupWindow.dismiss());
        btnPrevious.setOnClickListener(view -> navigateDate(btnNext, textview, -1));
        btnNext.setOnClickListener(view -> navigateDate(btnNext, textview, 1));

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
        // Create a line chart to display fullness data
        chart.setVisibility(View.VISIBLE);
        chart.setBackgroundColor(Color.WHITE);



        fullnessDataMap = new HashMap<>();
        for (Fullness data : fullnessDataList) {
            Date date = truncateTime(data.getTimestamp()); // Truncate time
            if (!fullnessDataMap.containsKey(date)) {
                fullnessDataMap.put(date, new ArrayList<>());
            }
            fullnessDataMap.get(date).add(data);
        }
        current_container_length = containerLength;

        showPlot();

    }

    private void showPlot() {
        List<Fullness> dateFullness = fullnessDataMap.get(current_date);
        List<Entry> entries = new ArrayList<>();

        if (dateFullness != null) {

            for (Fullness data : dateFullness) {
                // Normalize timestamp to minutes of the day
                long minutes = (data.getTimestamp().getHours() * 60 + data.getTimestamp().getMinutes());
                entries.add(new Entry((minutes), (int) (((current_container_length - data.getFullness()) / current_container_length) * 100.0)));
            }
        }


        LineDataSet dataSet = new LineDataSet(entries, "Fullness Data");
        LineData lineData = new LineData(dataSet);

        chart.setData(lineData);

        //Description description = new Description();
        //description.setText("Fullness Data Plot");
        //chart.setDescription(description);


        // Set X-axis range
        chart.getXAxis().setAxisMinimum(0);
        chart.getXAxis().setAxisMaximum(1440);
        chart.getXAxis().setValueFormatter(new TimeValueFormatter());
        chart.getXAxis().setGranularityEnabled(true);
        chart.getXAxis().setGranularity(1);
        chart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);

        // Set Y-axis range
        chart.getAxisLeft().setAxisMinimum(0);
        chart.getAxisLeft().setAxisMaximum(100);
        chart.getAxisLeft().setGranularity(1);

        chart.getAxisRight().setEnabled(false);

        //chart.getLayoutParams().height = ViewGroup.LayoutParams.MATCH_PARENT;
        //chart.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;

        chart.invalidate(); // refresh
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

