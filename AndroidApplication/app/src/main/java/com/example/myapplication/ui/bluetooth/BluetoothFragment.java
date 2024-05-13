package com.example.myapplication.ui.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.myapplication.R;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.UUID;

public class BluetoothFragment extends Fragment implements AdapterView.OnItemClickListener {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;

    private static final int REQUEST_BLUETOOTH_SCAN_PERMISSION = 3;

    private static String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_LOCATION_EXTRA_COMMANDS,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_PRIVILEGED
    };
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothManager bluetoothManager;
    private ArrayAdapter<String> discoveredDevicesAdapter;
    private ArrayList<BluetoothDevice> discoveredDevices;
    private ListView discoveredDevicesListView;
    private BluetoothDevice connectedDevice;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private EditText ssidEditText, passwordEditText;
    private LocationManager locationManager;
    private LocationListener locationListener;
    private Button sendWifiDataButton, sendGPSDataButton, disconnectButton;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_bluetooth, container, false);


        // Initialize UI components
        discoveredDevicesListView = view.findViewById(R.id.discovered_devices_list);
        ssidEditText = view.findViewById(R.id.ssid_edit_text);
        passwordEditText = view.findViewById(R.id.password_edit_text);
        sendWifiDataButton = view.findViewById(R.id.send_wifi_data_button);
        sendGPSDataButton = view.findViewById(R.id.send_gps_data_button);
        disconnectButton = view.findViewById(R.id.disconnect_button);

        // Initialize Bluetooth adapter
        //bluetoothManager = getSystemService(BluetoothManager.class);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Toast.makeText(getContext(), "Bluetooth is not supported on this device", Toast.LENGTH_SHORT).show();
            getActivity().finish();
        }

        // Request necessary permissions
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        }


        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_ADMIN)
                != PackageManager.PERMISSION_GRANTED) {
            {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.BLUETOOTH_ADMIN},
                        REQUEST_BLUETOOTH_SCAN_PERMISSION);
            }
        }


        // Check if Bluetooth is enabled, if not request user to enable it
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //discoverDevices();

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        requireActivity().registerReceiver(receiver, filter);

        // Initialize discovered devices list
        discoveredDevices = new ArrayList<>();
        discoveredDevicesAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1);
        discoveredDevicesListView.setAdapter(discoveredDevicesAdapter);
        discoveredDevicesListView.setOnItemClickListener(this);


        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    4);
        }
        checkPermissions();

       //  discoverDevices();

        // Button click listeners
        sendWifiDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendWifiData();
            }
        });

        sendGPSDataButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendGPSData();
            }
        });

        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                disconnect();
            }
        });

        return view;
    }

    private void checkPermissions(){
        int permission2 = ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN);
        if (permission2 != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(
                    getActivity(),
                    PERMISSIONS_LOCATION,
                    1
            );
        }
    }

    // BroadcastReceiver for discovering devices
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null) {
                    discoveredDevices.add(device);
                    discoveredDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                }
            }
        }
    };

    // Handle permission request result
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, start discovery
                discoverDevices();
            } else {
                // Permission denied, show a message or handle it accordingly
                Toast.makeText(getContext(), "Permission denied, cannot scan for Bluetooth devices", Toast.LENGTH_SHORT).show();
            }
        }
    }



    // Method to discover nearby devices
    private void discoverDevices() {
        //if (bluetoothAdapter.isDiscovering()) {
        //    bluetoothAdapter.cancelDiscovery();
       // }
        bluetoothAdapter.startDiscovery();
    }

    // Method to establish connection with selected device
    private void connectToDevice(int position) {
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        BluetoothDevice device = discoveredDevices.get(position);
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();
            connectedDevice = device;
            outputStream = bluetoothSocket.getOutputStream();
            Toast.makeText(getContext(), "Connected to " + connectedDevice.getName(), Toast.LENGTH_SHORT).show();
            sendWifiDataButton.setEnabled(true);
            sendGPSDataButton.setEnabled(true);
            disconnectButton.setEnabled(true);
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Connection failed", Toast.LENGTH_SHORT).show();
        }
    }

    // Method to send Wi-Fi data
    private void sendWifiData() {
        String ssid = ssidEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        String data = "ssid:" + ssid + ", password:" + password;
        sendData(data);
    }

    // Method to send GPS data
    private void sendGPSData() {
        if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(),
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
            return;
        }
        locationManager = (LocationManager) requireActivity().getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                double latitude = location.getLatitude();
                double longitude = location.getLongitude();
                String data = "lat:" + latitude + ", lon:" + longitude;
                sendData(data);
                locationManager.removeUpdates(this);
            }
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override
            public void onProviderEnabled(String provider) {}
            @Override
            public void onProviderDisabled(String provider) {
                Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                startActivity(intent);
            }
        };
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);
    }

    // Method to send data to connected device
    private void sendData(String data) {
        if (outputStream != null) {
            try {
                outputStream.write(data.getBytes());
                Toast.makeText(getContext(), "Data sent successfully", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Failed to send data", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Method to disconnect from the device
    private void disconnect() {
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
            if (connectedDevice != null) {
                Toast.makeText(getContext(), "Disconnected from " + connectedDevice.getName(), Toast.LENGTH_SHORT).show();
            }
            connectedDevice = null;
            outputStream = null;
            sendWifiDataButton.setEnabled(false);
            sendGPSDataButton.setEnabled(false);
            disconnectButton.setEnabled(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        connectToDevice(position);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        requireActivity().unregisterReceiver(receiver);
        disconnect();
    }
}
