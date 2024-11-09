package com.example.myapplication.ui.bluetooth;

import static androidx.core.content.ContextCompat.getSystemService;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.myapplication.R;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BluetoothFragment extends Fragment implements AdapterView.OnItemClickListener {
    private static final int REQUEST_ENABLE_BT = 1;
    private static final int REQUEST_LOCATION_PERMISSION = 2;

    private static final String[] PERMISSIONS_LOCATION = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADMIN
    };

    private BluetoothAdapter bluetoothAdapter;
    private ArrayAdapter<String> discoveredDevicesAdapter;
    private ArrayList<BluetoothDevice> discoveredDevices;
    private ListView discoveredDevicesListView;
    private BluetoothSocket bluetoothSocket;
    private OutputStream outputStream;
    private EditText ssidEditText, passwordEditText;
    private LocationManager locationManager;
    private LocationListener locationListener;

    private TextView bluetoothResponseTextView;

    private TextView wifiConfigTextView, gpsConfigTextView, currentLengthTextView, userIdTextView, activeDataTextView;

    private TextView configTitleTextView, titleTerminalTextView, searchTitleTextView;
    private CardView cardView;
    private ScrollView scrollView;

    private TextView currentDistanceTextView;



    // Додамо змінні для конфігурації пристрою
    private String wifiConnected = "невідомо";
    private String gpsConfigured = "невідомо";
    private String containerLength = "невідомо";
    private String userId = "невідомо";

    private String dataActive = "невідомо";

    private float received_current_distance = 0;


    private Button sendWifiDataButton, sendGPSDataButton, disconnectButton, sendUserIdButton, calibrateButton, dataActiveButton;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                Toast.makeText(getContext(), "Розпочато пошук...", Toast.LENGTH_SHORT).show();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
              //  Toast.makeText(getContext(), "Discovery Finished", Toast.LENGTH_SHORT).show();
            } else if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getName() != null && device.getName().startsWith("MONITORING_DEVICE")) {
                    discoveredDevices.add(device);
                    discoveredDevicesAdapter.add(device.getName() + "\n" + device.getAddress());
                    discoveredDevicesAdapter.notifyDataSetChanged();
                }
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_bluetooth, container, false);

        discoveredDevicesListView = view.findViewById(R.id.discovered_devices_list);
        dataActiveButton = view.findViewById(R.id.update_active_data);
        sendWifiDataButton = view.findViewById(R.id.send_wifi_data_button);
        sendGPSDataButton = view.findViewById(R.id.send_gps_data_button);
        sendUserIdButton = view.findViewById(R.id.send_user_id);
        calibrateButton = view.findViewById(R.id.calibrate);
        disconnectButton = view.findViewById(R.id.disconnect_button);

        scrollView = view.findViewById(R.id.scroll_view);
        wifiConfigTextView = view.findViewById(R.id.wifi_config_textview);
        gpsConfigTextView = view.findViewById(R.id.gps_config_textview);
        currentLengthTextView = view.findViewById(R.id.current_length_textview);
        userIdTextView = view.findViewById(R.id.user_id_textview);
        activeDataTextView = view.findViewById(R.id.active_data_textview);
        bluetoothResponseTextView = view.findViewById(R.id.bluetooth_response_textview);

         configTitleTextView = view.findViewById(R.id.config_title);
         cardView = view.findViewById(R.id.card_view);
         titleTerminalTextView = view.findViewById(R.id.title_terminal);
        searchTitleTextView = view.findViewById(R.id.search_title);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getContext(), "Блютуз не підтримується на даному пристрої", Toast.LENGTH_SHORT).show();
            getActivity().finish();
            return view;
        }

        // Check permissions
        checkPermissions();

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivity(enableBtIntent);
        } else {
            startDiscovery();
        }

        discoveredDevices = new ArrayList<>();
        discoveredDevicesAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1);
        discoveredDevicesListView.setAdapter(discoveredDevicesAdapter);
        discoveredDevicesListView.setOnItemClickListener(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        getActivity().registerReceiver(receiver, filter);

        sendWifiDataButton.setOnClickListener(v -> sendWifiData());
        dataActiveButton.setOnClickListener(v -> sendDataActive());
        sendGPSDataButton.setOnClickListener(v -> sendGPSData());
        sendUserIdButton.setOnClickListener(v -> sendUserId());
        calibrateButton.setOnClickListener(v -> showCalibratePopup());
        disconnectButton.setOnClickListener(v -> disconnect());

        updateDeviceConfiguration();

        return view;
    }

    private void checkPermissions() {
        ArrayList<String> permissionsNeeded = new ArrayList<>();
        for (String permission : PERMISSIONS_LOCATION) {
            if (ContextCompat.checkSelfPermission(getContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(getActivity(), permissionsNeeded.toArray(new String[0]), REQUEST_LOCATION_PERMISSION);
        }
    }

    private void startDiscovery() {
        if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(getActivity(), new String[]{Manifest.permission.BLUETOOTH_SCAN}, REQUEST_LOCATION_PERMISSION);
            return;
        }
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        boolean success = bluetoothAdapter.startDiscovery();
        if (!success) {
            Toast.makeText(getContext(), "Помилка при пошуку блютуз пристроїв", Toast.LENGTH_SHORT).show();
        }
    }

    private void connectToDevice(int position) {
        if (bluetoothAdapter.isDiscovering()) {
            bluetoothAdapter.cancelDiscovery();
        }
        BluetoothDevice device = discoveredDevices.get(position);
        UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(uuid);
            bluetoothSocket.connect();
            outputStream = bluetoothSocket.getOutputStream();
            InputStream inputStream = bluetoothSocket.getInputStream();

            Toast.makeText(getContext(), "Під'єднано до " + device.getName(), Toast.LENGTH_SHORT).show();
            dataActiveButton.setEnabled(true);
            sendWifiDataButton.setEnabled(true);
            sendGPSDataButton.setEnabled(true);
            calibrateButton.setEnabled(true);
            sendUserIdButton.setEnabled(true);
            disconnectButton.setEnabled(true);

            dataActiveButton.setVisibility(View.VISIBLE);
            sendWifiDataButton.setVisibility(View.VISIBLE);
            sendGPSDataButton.setVisibility(View.VISIBLE);
            sendUserIdButton.setVisibility(View.VISIBLE);
            disconnectButton.setVisibility(View.VISIBLE);
            calibrateButton.setVisibility(View.VISIBLE);

            scrollView.setVisibility(View.VISIBLE);
            configTitleTextView.setVisibility(View.VISIBLE);
            cardView.setVisibility(View.VISIBLE);
            titleTerminalTextView.setVisibility(View.VISIBLE);
            bluetoothResponseTextView.setVisibility(View.VISIBLE);
            discoveredDevicesListView.setVisibility(View.GONE);
            searchTitleTextView.setVisibility(View.GONE);


            // Отримуємо початкову конфігурацію пристрою
            outputStream.write("GET_CONFIG;".getBytes());
            outputStream.flush();

            // Start a thread to listen for incoming data
            new Thread(new BluetoothReadThread(inputStream)).start();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Помилка з'єднання", Toast.LENGTH_SHORT).show();
        }
    }


    private class BluetoothReadThread implements Runnable {
        private final InputStream inputStream;

        public BluetoothReadThread(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;
            while (true) {
                try {
                    // Read from the InputStream
                    bytes = inputStream.read(buffer);
                    String readMessage = new String(buffer, 0, bytes);
                    // Send the obtained bytes to the handler
                    handler.obtainMessage(MESSAGE_READ, readMessage).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                    break;
                }
            }
        }
    }


    private Handler handler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            if (msg.what == MESSAGE_READ) {
                String receivedData = (String) msg.obj;
                appendReceivedData(receivedData);
            }
        }
    };

    private static final int MESSAGE_READ = 0;

    private void appendReceivedData(String data) {
        String[] lines = data.split("\n");
        for (String line : lines) {
            // Оновлення конфігурації пристрою
            if (line.startsWith("CONFIG:")) {
                String[] parts = line.split(",");
                wifiConnected = parts[1].split(":")[1].trim();
                gpsConfigured = parts[2].split(":")[1].trim();
                containerLength = parts[3].split(":")[1].trim();
                userId = parts[4].split(":")[1].trim();
                dataActive = parts[5].split(":")[1].trim();
                updateDeviceConfiguration();

                bluetoothResponseTextView.append("\n" + "Дані конфігурації оновлені! " + "\n");
            }
            else if (line.startsWith("Distance:")) {
                received_current_distance = Float.parseFloat(line.split(":")[1].trim());
                if (currentDistanceTextView != null) {
                    currentDistanceTextView.setText("Поточна відстань до дна: ");

                    Spannable res = new SpannableString(received_current_distance + "см");
                    res.setSpan(new ForegroundColorSpan(Color.GREEN), 0, res.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    currentDistanceTextView.append(res);
                }
            }
            else {
                bluetoothResponseTextView.append(line);
            }
        }

        // Scroll to the bottom
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    private Spannable parseConfig(String option)
    {
        try {
            if (Integer.parseInt(option) == 1) {
                Spannable res = new SpannableString("TAK");
                res.setSpan(new ForegroundColorSpan(Color.GREEN), 0, res.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                return res;
            } else if (Integer.parseInt(option) == 0) {
                Spannable res = new SpannableString("НІ");
                res.setSpan(new ForegroundColorSpan(Color.RED), 0, res.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                return res;
            } else {
                Spannable res = new SpannableString("Невідомо");
                res.setSpan(new ForegroundColorSpan(Color.RED), 0, res.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                return res;
            }
        }
        catch (NumberFormatException nfe)
        {
            Spannable res = new SpannableString("Невідомо");
            res.setSpan(new ForegroundColorSpan(Color.RED), 0, res.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return res;
        }



    }


    private void updateDeviceConfiguration() {

        wifiConfigTextView.setText("WiFi сконфігуровано: ");
        wifiConfigTextView.append(parseConfig(wifiConnected));

        gpsConfigTextView.setText("GPS сконфігуровано: ");
        gpsConfigTextView.append(parseConfig(gpsConfigured));

        currentLengthTextView.setText("Глибина контейнера: ");
        Spannable res = new SpannableString(containerLength + " см");
        res.setSpan(new ForegroundColorSpan(Color.parseColor("#ADD8E6")), 0, res.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        currentLengthTextView.append(res);


        userIdTextView.setText("ID користувачів: ");
        res = new SpannableString(userId);
        res.setSpan(new ForegroundColorSpan(Color.parseColor("#ADD8E6")), 0, res.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        userIdTextView.append(res);


        activeDataTextView.setText("Оновлення даних включено: ");
        activeDataTextView.append(parseConfig(dataActive));
        try {
            if (Integer.parseInt(dataActive) == 1) {
                String newText = dataActiveButton.getText().toString().replace("ВІДНОВИТИ", "ЗУПИНИТИ");
                dataActiveButton.setText(newText);

            } else {
                String newText = dataActiveButton.getText().toString().replace("ЗУПИНИТИ", "ВІДНОВИТИ");
                dataActiveButton.setText(newText);

            }
        }
        catch (NumberFormatException nfe)
        {
        }
    }


    private void showCalibratePopup(){
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.popup_calibrate, null);
        builder.setView(dialogView);

        currentDistanceTextView= dialogView.findViewById(R.id.current_length_text_view);
        Button updateLengthButton = dialogView.findViewById(R.id.update_length_button);
        Button confirmLengthButton = dialogView.findViewById(R.id.confirm_length_button);

        sendData("GET_CURRENT_DISTANCE");

        // Set up the buttons
        updateLengthButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendData("GET_CURRENT_DISTANCE");
            }
        });

        confirmLengthButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Confirm the length and send it via Bluetooth
                sendData("length: " + received_current_distance);
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int id) {
                dialog.cancel();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }


    private void showWifiPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Введіть креденшали Wi-Fi");

        View viewInflated = LayoutInflater.from(getContext()).inflate(R.layout.popup_wifi, (ViewGroup) getView(), false);

        final EditText ssidEditText = viewInflated.findViewById(R.id.ssid);
        final EditText passwordEditText = viewInflated.findViewById(R.id.password);

        builder.setView(viewInflated);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String ssid = ssidEditText.getText().toString().trim();
            String password = passwordEditText.getText().toString().trim();
            String data = "wifi: " + ssid + ", " + password;
            sendData(data);
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }


    private void sendWifiData() {
        showWifiPopup();
    }


    private void sendDataActive()
    {
        boolean res = false;
        if (dataActiveButton.getText().toString().startsWith("ВІДНОВИТИ"))
        {
            String data = "update_data:1";
            res = sendData(data);

        }
        else if (dataActiveButton.getText().toString().startsWith("ЗУПИНИТИ"))
        {
            String data = "update_data:0";
            res = sendData(data);

        }
    }


    private static final int REQUEST_CODE_MAP = 1;
    private static EditText latEditText, lonEditText;

    private void showGPSPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Введіть дані GPS");

        View viewInflated = LayoutInflater.from(getContext()).inflate(R.layout.popup_gps, (ViewGroup) getView(), false);
        latEditText = viewInflated.findViewById(R.id.latitude);
        lonEditText = viewInflated.findViewById(R.id.longitude);


        // Button to select coordinates on the map
        Button selectOnMapButton = viewInflated.findViewById(R.id.selectOnMapButton);
        selectOnMapButton.setOnClickListener(view -> {
            Intent intent = new Intent(getContext(), MapActivity.class);
            startActivityForResult(intent, REQUEST_CODE_MAP);
        });

        builder.setView(viewInflated);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String lat = latEditText.getText().toString().trim();
            String lon = lonEditText.getText().toString().trim();
            String data = "gps: " + lat + ", " + lon;
            sendData(data);
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    // Receive result from MapActivity
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MAP && resultCode == Activity.RESULT_OK) {
            double latitude = data.getDoubleExtra("latitude", 0);
            double longitude = data.getDoubleExtra("longitude", 0);


            // Set the coordinates in the EditText fields
            latEditText.setText(String.valueOf(latitude));
            lonEditText.setText(String.valueOf(longitude));
        }
    }


    private void sendGPSData() {
        showGPSPopup();

    }

    private void showUserIdPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Введіть id користувача");

        View viewInflated = LayoutInflater.from(getContext()).inflate(R.layout.popup_user_id, (ViewGroup) getView(), false);

        RecyclerView userRecyclerView = viewInflated.findViewById(R.id.user_recycler_view);
        userRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        final EditText userEditText = viewInflated.findViewById(R.id.user_id);

        // Fetch users and set adapter with click listener
        fetchUsers(userRecyclerView, userEditText);

        builder.setView(viewInflated);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> {
            String user_id = userEditText.getText().toString().trim();
            String data = "user_id: " + user_id;
            sendData(data);
        });
        builder.setNegativeButton(android.R.string.cancel, (dialog, which) -> dialog.cancel());

        builder.show();
    }

    private void fetchUsers(RecyclerView userRecyclerView, EditText userEditText) {
        ApiService apiService = ApiClient.getClient().create(ApiService.class);

        Call<List<User>> call = apiService.getUsers();
        call.enqueue(new Callback<List<User>>() {
            @Override
            public void onResponse(Call<List<User>> call, Response<List<User>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<User> users = response.body();
                    UserAdapter adapter = new UserAdapter(users, user -> {
                        // Set selected user ID in the EditText
                        userEditText.setText(String.valueOf(user.getId()));
                    });
                    userRecyclerView.setAdapter(adapter);
                } else {
                    Toast.makeText(getContext(), "Помилка при отриманні користувачів", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<User>> call, Throwable t) {
                Toast.makeText(getContext(), "Помилка: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }



    private void sendUserId() {
        showUserIdPopup();
    }

    private boolean sendData(String data) {
        if (outputStream != null) {
            try {

                // Add a newline or some delimiter to indicate the end of the data
                data = data + ";";
                outputStream.write(data.getBytes());
                // Ensure the data is sent immediately
                outputStream.flush();
                Toast.makeText(getContext(), "Дані надіслано успішно", Toast.LENGTH_SHORT).show();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Сталася помилка при надсиланні даних", Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        return false;
    }

    private void disconnect() {
        try {
            if (bluetoothSocket != null) {
                bluetoothSocket.close();
            }
            Toast.makeText(getContext(), "Від'єднано", Toast.LENGTH_SHORT).show();
            dataActiveButton.setEnabled(false);
            sendWifiDataButton.setEnabled(false);
            sendGPSDataButton.setEnabled(false);
            sendUserIdButton.setEnabled(false);
            disconnectButton.setEnabled(false);
            calibrateButton.setEnabled(false);


            dataActiveButton.setVisibility(View.GONE);
            sendWifiDataButton.setVisibility(View.GONE);
            sendGPSDataButton.setVisibility(View.GONE);
            sendUserIdButton.setVisibility(View.GONE);
            disconnectButton.setVisibility(View.GONE);
            calibrateButton.setVisibility(View.GONE);


            scrollView.setVisibility(View.GONE);
            configTitleTextView.setVisibility(View.GONE);
            cardView.setVisibility(View.GONE);
            titleTerminalTextView.setVisibility(View.GONE);
            bluetoothResponseTextView.setVisibility(View.GONE);

            discoveredDevicesListView.setVisibility(View.VISIBLE);
            searchTitleTextView.setVisibility(View.VISIBLE);

            wifiConnected = "невідомо";
            gpsConfigured = "невідомо";
            containerLength = "невідомо";
            userId = "невідомо";
            dataActive = "невідомо";
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
        getActivity().unregisterReceiver(receiver);
        disconnect();
        handler.removeCallbacksAndMessages(null); // Clean up handler messages
    }

}
