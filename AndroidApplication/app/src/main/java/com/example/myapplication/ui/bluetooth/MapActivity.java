package com.example.myapplication.ui.bluetooth;

import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.example.myapplication.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private LatLng selectedLatLng;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;

        // Set initial position and zoom level
        LatLng initialLatLng = new LatLng(48.3794, 31.1656); // Center of Ukraine
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initialLatLng, 5));

        // Add a marker on map click
        mMap.setOnMapClickListener(latLng -> {
            selectedLatLng = latLng;
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(latLng).title("Selected Location"));
        });
    }

    @Override
    public void onBackPressed() {
        if (mMap != null) {
            mMap.clear(); // Clear all markers and overlays on the map
        }

        if (selectedLatLng != null) {
            Intent resultIntent = new Intent();
            resultIntent.putExtra("latitude", selectedLatLng.latitude);
            resultIntent.putExtra("longitude", selectedLatLng.longitude);
            setResult(RESULT_OK, resultIntent);
        }
        // Remove the map fragment from the activity
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            getSupportFragmentManager().beginTransaction().remove(mapFragment).commit();
        }
        super.onBackPressed();
    }
}
