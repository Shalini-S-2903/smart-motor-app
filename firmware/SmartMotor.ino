/*
 * SmartMotor ESP32 Firmware
 * ─────────────────────────
 * Startup logic:
 *   1. Load saved WiFi credentials from NVS.
 *   2. If valid credentials exist → try to connect to WiFi for 15 s.
 *   3. If connection fails (or no credentials) → start "SmartMotor-Setup" AP.
 *   4. Once connected to WiFi → connect to MQTT and begin normal operation.
 *
 * Provisioning:
 *   While in AP mode, serve HTTP on port 80:
 *     POST /provision  {"ssid":"...","password":"..."}
 *     → saves to NVS, responds {"device_id":"<MAC>"}, reboots into STA mode.
 *
 * MQTT topics (no leading slash):
 *   Publish:  devices/<ID>/heartbeat   every 10 s
 *             devices/<ID>/state        "ON" | "OFF"
 *             devices/<ID>/power        float (W)
 *             devices/<ID>/ack          {"command_id":"...","status":"SUCCESS"}
 *             devices/<ID>/pairing      6-digit code every 60 s (until claimed)
 *   Subscribe: devices/<ID>/command    {"command_id":"...","action":"ON"|"OFF"|"REPROVISION_WIFI"}
 */

#include <WiFi.h>
#include <WebServer.h>
#include <Preferences.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>

// ─── CONFIG ──────────────────────────────────────────────────────────────────
const char* AP_SSID     = "SmartMotor-Setup";
const char* AP_PASSWORD = "";  // open network for easy access
const char* MQTT_HOST   = "broker.hivemq.com";
const int   MQTT_PORT   = 1883;
const int   RELAY_PIN   = 26;  // GPIO controlling the motor relay
const int   ADC_PIN     = 34;  // Analog input for current/power sensing

// ─── GLOBALS ─────────────────────────────────────────────────────────────────
Preferences prefs;
WebServer   server(80);
WiFiClient  wifiClient;
PubSubClient mqtt(wifiClient);

String deviceId;   // Derived from MAC address
String pairCode;   // 6-digit code shown during provisioning

bool motorOn = false;

unsigned long lastHeartbeat = 0;
unsigned long lastPairPub   = 0;
unsigned long lastPowerRead = 0;

// ─── HELPERS ─────────────────────────────────────────────────────────────────
String macId() {
  uint8_t mac[6];
  esp_read_mac(mac, ESP_MAC_WIFI_STA);
  char buf[13]; 
  sprintf(buf, "%02X%02X%02X%02X%02X%02X", mac[0], mac[1], mac[2], mac[3], mac[4], mac[5]);
  return String(buf);
}

String randomCode() {
  return String(random(100000, 999999));
}

void setRelay(bool on) {
  motorOn = on;
  digitalWrite(RELAY_PIN, on ? HIGH : LOW);
}

// ─── MQTT HELPERS ────────────────────────────────────────────────────────────
void mqttPublish(const char* suffix, const char* payload, bool retained = false) {
  String topic = "devices/" + deviceId + "/" + suffix;
  mqtt.publish(topic.c_str(), payload, retained);
}

void publishState() {
  mqttPublish("state", motorOn ? "ON" : "OFF");
  Serial.printf("[MQTT] state → %s\n", motorOn ? "ON" : "OFF");
}

void publishAck(const char* commandId) {
  String payload = "{\"command_id\":\"" + String(commandId) + "\",\"status\":\"SUCCESS\"}";
  mqttPublish("ack", payload.c_str());
}

// ─── MQTT COMMAND HANDLER ────────────────────────────────────────────────────
void onMqttMessage(char* topic, byte* payload, unsigned int length) {
  String msg((char*)payload, length);
  Serial.printf("[MQTT] cmd: %s\n", msg.c_str());

  StaticJsonDocument<256> doc;
  if (deserializeJson(doc, msg) != DeserializationError::Ok) return;

  const char* commandId = doc["command_id"] | "";
  const char* action    = doc["action"]     | "";

  if (strcmp(action, "ON") == 0) {
    setRelay(true);
    publishState();
  } else if (strcmp(action, "OFF") == 0) {
    setRelay(false);
    publishState();
  } else if (strcmp(action, "REPROVISION_WIFI") == 0) {
    prefs.begin("wifi", false);
    prefs.remove("ssid");
    prefs.remove("password");
    prefs.end();
    Serial.println("[INFO] WiFi credentials cleared. Rebooting to AP mode...");
    delay(500);
    ESP.restart();
  }

  if (strlen(commandId) > 0) publishAck(commandId);
}

// ─── MQTT CONNECT ────────────────────────────────────────────────────────────
void mqttConnect() {
  while (!mqtt.connected()) {
    Serial.printf("[MQTT] Connecting to %s:%d as %s ...\n", MQTT_HOST, MQTT_PORT, deviceId.c_str());
    if (mqtt.connect(deviceId.c_str())) {
      Serial.println("[MQTT] Connected!");
      String cmdTopic = "devices/" + deviceId + "/command";
      mqtt.subscribe(cmdTopic.c_str());
      Serial.printf("[MQTT] Subscribed to %s\n", cmdTopic.c_str());
      publishState();
    } else {
      Serial.printf("[MQTT] Failed, rc=%d. Retry in 5s\n", mqtt.state());
      delay(5000);
    }
  }
}

// ─── AP PROVISIONING SERVER ─────────────────────────────────────────────────
void handleProvision() {
  if (server.method() != HTTP_POST) {
    server.send(405, "text/plain", "Method Not Allowed");
    return;
  }
  String body = server.arg("plain");
  StaticJsonDocument<256> doc;
  if (deserializeJson(doc, body) != DeserializationError::Ok) {
    server.send(400, "application/json", "{\"error\":\"invalid json\"}");
    return;
  }
  const char* ssid = doc["ssid"] | "";
  const char* pass = doc["password"] | "";
  if (strlen(ssid) == 0) {
    server.send(400, "application/json", "{\"error\":\"ssid required\"}");
    return;
  }

  prefs.begin("wifi", false);
  prefs.putString("ssid", ssid);
  prefs.putString("password", pass);
  prefs.end();

  String resp = "{\"device_id\":\"" + deviceId + "\"}";
  server.send(200, "application/json", resp);
  Serial.printf("[PROVISION] Saved SSID=%s. Rebooting...\n", ssid);
  delay(500);
  ESP.restart();
}

void handleRoot() {
  server.send(200, "text/html",
    "<html><body><h2>SmartMotor Setup</h2>"
    "<p>Device ID: " + deviceId + "</p>"
    "<p>POST JSON to /provision: {\"ssid\":\"...\",\"password\":\"...\"}</p>"
    "</body></html>");
}

void startAP() {
  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID, AP_PASSWORD);
  IPAddress ip = WiFi.softAPIP();
  Serial.printf("[AP] Started '%s' — IP: %s\n", AP_SSID, ip.toString().c_str());

  server.on("/", handleRoot);
  server.on("/provision", HTTP_POST, handleProvision);
  server.begin();
  Serial.println("[AP] HTTP server started");
}

// ─── SETUP ───────────────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200);
  randomSeed(analogRead(0));
  pinMode(RELAY_PIN, OUTPUT);
  setRelay(false);

  deviceId = macId();
  pairCode = randomCode();
  Serial.printf("[INFO] Device ID: %s\n", deviceId.c_str());
  Serial.printf("[INFO] Pair code: %s\n", pairCode.c_str());

  // Load saved credentials
  prefs.begin("wifi", true);
  String savedSsid = prefs.getString("ssid", "");
  String savedPass = prefs.getString("password", "");
  prefs.end();

  bool connected = false;
  if (savedSsid.length() > 0) {
    Serial.printf("[WiFi] Connecting to '%s' ...\n", savedSsid.c_str());
    WiFi.mode(WIFI_STA);
    WiFi.begin(savedSsid.c_str(), savedPass.c_str());
    unsigned long start = millis();
    while (WiFi.status() != WL_CONNECTED && millis() - start < 15000) {
      delay(500);
      Serial.print(".");
    }
    Serial.println();
    if (WiFi.status() == WL_CONNECTED) {
      Serial.printf("[WiFi] Connected! IP: %s\n", WiFi.localIP().toString().c_str());
      connected = true;
    } else {
      Serial.println("[WiFi] Failed to connect. Starting AP...");
    }
  } else {
    Serial.println("[WiFi] No saved credentials. Starting AP...");
  }

  if (!connected) {
    startAP();
    return;
  }

  // MQTT setup
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setCallback(onMqttMessage);
  mqtt.setKeepAlive(30);
  mqtt.setBufferSize(512);
  mqttConnect();
}

// ─── LOOP ────────────────────────────────────────────────────────────────────
void loop() {
  // AP mode: just handle HTTP provisioning
  if (WiFi.getMode() == WIFI_AP) {
    server.handleClient();
    return;
  }

  // STA mode: reconnect if dropped
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WiFi] Lost connection. Reconnecting...");
    WiFi.reconnect();
    delay(5000);
    return;
  }

  if (!mqtt.connected()) mqttConnect();
  mqtt.loop();

  unsigned long now = millis();

  // Heartbeat every 10 s
  if (now - lastHeartbeat >= 10000) {
    lastHeartbeat = now;
    mqttPublish("heartbeat", "online");
    Serial.println("[MQTT] heartbeat");
  }

  // Pairing code every 60 s
  if (now - lastPairPub >= 60000) {
    lastPairPub = now;
    mqttPublish("pairing", pairCode.c_str());
    Serial.printf("[MQTT] pairing code: %s\n", pairCode.c_str());
  }

  // Power reading every 2 s
  if (now - lastPowerRead >= 2000) {
    lastPowerRead = now;
    int raw = analogRead(ADC_PIN);
    float amps = (raw / 4095.0) * 30.0;  // scale to 0–30A, adjust for your sensor
    float watts = amps * 230.0;           // single-phase 230V
    char buf[16];
    dtostrf(watts, 4, 1, buf);
    mqttPublish("power", buf);
  }
}
