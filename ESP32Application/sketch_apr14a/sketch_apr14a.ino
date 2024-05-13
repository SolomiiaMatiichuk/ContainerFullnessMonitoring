#include "esp32-hal-gpio.h"
#include "WiFi.h"
#include "HTTPClient.h"
#include "BluetoothSerial.h"
#include "Preferences.h"

#define ECHO_PIN 19
#define TRIG_PIN 18

// default wifi
char ssid[32];
char password[64];
bool wifiConfigured = false;

// default user id
int user_id = 1;

// container id (cannot be changes)
const int container_id = 0x12345;

String device_name = "MONITORING_DEVICE_0x12345";

const char* serverUrl = "https://container-monitoring-server-5e33a8983798.herokuapp.com/data-endpoint";

// Define NTP Client to get time
const char* ntpServer = "pool.ntp.org";
const long gmtOffset_sec = 0;
const int daylightOffset_sec = 10800;


BluetoothSerial SerialBT;
Preferences preferences;


float lastDistance = 0.0;
unsigned long lastDistanceUpdate = 0;

bool timeConfigured = false;
bool data_send_successfully = false;
bool waitForUpdate = false;


bool loadSettings() {
  // Load settings from flash memory
  preferences.getString("ssid", ssid, sizeof(ssid));
  preferences.getString("password", password, sizeof(password));
  return (ssid[0] != '\0' && password[0] != '\0');
}

void saveSettings(const char* ssid, const char* password) {
  // Save settings to flash memory
  preferences.putString("ssid", ssid);
  preferences.putString("password", password);
}

void connectToWiFi() {
  int timeout = 10;  // timeout for wi-fi connection is 10 seconds
  WiFi.disconnect(true);
  delay(1000);
  WiFi.begin(ssid, password);
  Serial.println("Connecting to WiFi...");
  while ((WiFi.status() != WL_CONNECTED) && (timeout > 0)) {
    delay(1000);
    Serial.print("Trying to connect...");
    timeout--;
  }
  Serial.println("");

  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("Connected to WiFi network with IP Address: ");
    Serial.println(WiFi.localIP());
  } else {
    Serial.print("Timeout passed. Wi-fi connection failed. Please, try again.");
  }
}


String ssids_array[50];
String network_string;
void scan_wifi_networks() {
  WiFi.mode(WIFI_STA);
  // WiFi.scanNetworks will return the number of networks found
  int n = WiFi.scanNetworks();
  if (n == 0) {
    //SerialBT.println("no networks found");
    Serial.println("no networks found");
  } else {
    //SerialBT.println();
    //SerialBT.print(n);
    //SerialBT.println(" networks found");

    Serial.println();
    Serial.print(n);
    Serial.println(" networks found");
    delay(1000);
    for (int i = 0; i < n; ++i) {
      ssids_array[i + 1] = WiFi.SSID(i);
      Serial.print(i + 1);
      Serial.print(": ");
      Serial.println(ssids_array[i + 1]);
      network_string = i + 1;
      network_string = network_string + ": " + WiFi.SSID(i) + " (Strength:" + WiFi.RSSI(i) + ")";
      //SerialBT.println(network_string);
      Serial.println(network_string);
    }
  }
}

void setup() {

  Serial.begin(115200);
  SerialBT.begin(device_name);
  Serial.printf("Bluetooth device name: \"%s\"\n", device_name.c_str());

  preferences.begin("settings", false);

  // configure pins for communication with distance sensor
  pinMode(TRIG_PIN, OUTPUT);
  pinMode(ECHO_PIN, INPUT_PULLUP);

  // Load settings from flash memory
  wifiConfigured = loadSettings();


  // connect to wifi if configured, else wait for Bluetooth
  if (wifiConfigured) {
    Serial.print("Loaded ssid: ");
    Serial.println(ssid);
    Serial.print("Loaded password: ");
    Serial.println(password);

    connectToWiFi();
  } else {
    Serial.println("No Wi-fi data is stored yet");
  }

  if (wifiConfigured && WiFi.status() == WL_CONNECTED) {
    // Init and get the time
    configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
    timeConfigured = true;
    Serial.println("Current time configuration is successful");
  }

  Serial.println("Scanning Wi-Fi networks");
  scan_wifi_networks();
}


float getDistance() {
  // trigger distance sensor
  digitalWrite(TRIG_PIN, 0);
  delay(2);
  digitalWrite(TRIG_PIN, 1);
  delay(10);
  digitalWrite(TRIG_PIN, 0);

  float duration = pulseIn(ECHO_PIN, 1);
  return (duration * 0.0343) / 2;
}

void parseData(String data) {
  data.trim();  // Remove leading and trailing whitespace

  if (data.startsWith("wifi:")) {
    data.remove(0, 5);  // Remove "wifi:" prefix
    data.trim();

    // Split received data into SSID and password
    String ssid_str = data.substring(0, data.indexOf(','));
    String password_str = data.substring(data.indexOf(',') + 1);

    // Save SSID and password to flash memory
    ssid_str.toCharArray(ssid, sizeof(ssid));
    password_str.toCharArray(password, sizeof(password));

    // Connect to WiFi with new credentials
    connectToWiFi();

    if (WiFi.status() == WL_CONNECTED) {
      saveSettings(ssid, password);
      wifiConfigured = true;

      SerialBT.println("Connected to Wifi successfully");

      if (timeConfigured == false) {
        configTime(gmtOffset_sec, daylightOffset_sec, ntpServer);
      }
    } else {
      SerialBT.println("Wifi connection FAILED. Please, check correctness of ssid and password!");
    }
  } else if (data.startsWith("gps:")) {
    data.remove(0, 4);  // Remove "gps:" prefix
    data.trim();

    String latitude_str = data.substring(0, data.indexOf(','));
    String longitude_str = data.substring(data.indexOf(',') + 1);

    float latitude = latitude_str.toFloat();
    float longitude = longitude_str.toFloat();

    // Now you have latitude and longitude, you can use them as needed
    Serial.print("Latitude: ");
    Serial.println(latitude);
    Serial.print("Longitude: ");
    Serial.println(longitude);
  } else if (data.startsWith("user_id:")) {
    data.remove(0, 8);  // Remove "user_id:" prefix
    data.trim();

    user_id = data.toInt();
    Serial.print("User ID: ");
    Serial.println(user_id);
  } else {
    SerialBT.println("Invalid data format: " + data);
  }
}


void loop() {

  // Check if data is available via Bluetooth
  if (SerialBT.available()) {
    String data = SerialBT.readStringUntil('\n');
    parseData(data);
  }

  // If WiFi is configured, send data to server
  if (wifiConfigured && WiFi.status() == WL_CONNECTED) {
    float distance = getDistance();
    Serial.print("Current distance: ");
    Serial.println(distance);
    unsigned long currentTime = millis();

    if(lastDistanceUpdate == 0)
    {
            lastDistanceUpdate = millis();
      lastDistance = distance;
    }


    // Send data if distance changes more than 2.5 cm
    if (abs(distance - lastDistance) > 2.5) {
      lastDistanceUpdate = millis();
      lastDistance = distance;
      waitForUpdate = true;
    }

    else if ((waitForUpdate == true && (abs(distance - lastDistance) <= 2.5) && (currentTime - lastDistanceUpdate > 10000)) || (data_send_successfully == false)) {
      waitForUpdate = false;

      struct tm timeinfo;
      if (!getLocalTime(&timeinfo)) {
        Serial.println("Failed to obtain time");
        return;
      }
      Serial.println(&timeinfo, "%A, %B %d %Y %H:%M:%S");
      Serial.println();

      // Format timestamp
      char formattedTime[20];
      strftime(formattedTime, sizeof(formattedTime), "%Y-%m-%d %H:%M:%S", &timeinfo);

      // Create JSON payload
      String jsonPayload = "{\"user_id\": " + String(user_id) + ", \"container_id\": " + String(container_id) + ", \"distance\": " + String(distance) + ", \"timestamp\": \"" + String(formattedTime) + "\"}";


      // Send HTTP POST request to server
      HTTPClient http;
      http.begin(serverUrl);
      http.addHeader("Content-Type", "application/json");
      int httpResponseCode = http.POST(jsonPayload);

      if (httpResponseCode > 0) {
        String response = http.getString();
        Serial.println("HTTP Response code: " + String(httpResponseCode));
        Serial.println("Server response: " + response);
        data_send_successfully = true;
      } else {
        Serial.println("Error sending POST request");
        Serial.println("HTTP Response code: " + String(httpResponseCode));
        data_send_successfully = false;
      }

      http.end();
    }

    delay(2000);  // update data every 2 seconds
  } else {
    // If WiFi is not connected, wait for Bluetooth
    Serial.println("Waiting for Bluetooth...");
    while (!SerialBT.available()) {
      delay(1000);
      if (wifiConfigured && WiFi.status() == WL_CONNECTED)
        break;
    }
  }
}
