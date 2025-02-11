#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <addons/TokenHelper.h>
#include <addons/RTDBHelper.h>
#include <time.h>

#define LED_PIN 32
#define BUTTON_OPEN 26
#define BUTTON_CLOSE 27

#define WIFI_SSID "CNT FIBRA_FLIA IMBAQUINGO."
#define WIFI_PASSWORD "IMBAQUINGO1987"
#define API_KEY "AIzaSyCcfM6SJdrjE17RZjS-ajQsdgN7u5OgQSE"
#define DATABASE_URL "https://proyecto-puertas-appinventor-default-rtdb.firebaseio.com/"

FirebaseData fbdo;
FirebaseData stream;
FirebaseAuth auth;
FirebaseConfig config;

bool signupOK = false;
String userId = "abi";

// Función que se ejecutará cuando haya cambios en Firebase
void streamCallback(FirebaseStream data)
{
    String path = data.dataPath();
    FirebaseJson *json = data.to<FirebaseJson *>();
    FirebaseJsonData result;

    Serial.println("Datos recibidos de Firebase:");
    Serial.println("Ruta: " + path);
    
    if (json->get(result, "estado")) {
        String estado = result.stringValue;
        Serial.println("Estado: " + estado);
        
        if (estado == "Puerta Abierta") {
            parpadearLED(1);
        } else if (estado == "Puerta Cerrada") {
            parpadearLED(2);
        }
    }
}

void streamTimeoutCallback(bool timeout)
{
    if (timeout) {
        Serial.println("Stream timeout, reconectando...");
    }
    if (!stream.httpConnected()) {
        Serial.println(stream.errorReason());
    }
}

void setup() {
    Serial.begin(115200);
    pinMode(LED_PIN, OUTPUT);
    pinMode(BUTTON_OPEN, INPUT_PULLUP);
    pinMode(BUTTON_CLOSE, INPUT_PULLUP);
    digitalWrite(LED_PIN, LOW);

    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
    Serial.print("Conectando a WiFi");
    while (WiFi.status() != WL_CONNECTED) {
        Serial.print(".");
        delay(300);
    }
    Serial.println("\nConectado con IP: " + WiFi.localIP().toString());

    configTime(-5 * 3600, 0, "pool.ntp.org", "time.nist.gov");

    config.api_key = API_KEY;
    config.database_url = DATABASE_URL;

    if (Firebase.signUp(&config, &auth, "", "")) {
        Serial.println("Signup OK");
        signupOK = true;
    } else {
        Serial.printf("%s\n", config.signer.signupError.message.c_str());
    }

    Firebase.begin(&config, &auth);
    Firebase.reconnectWiFi(true);

    // Configurar la escucha en tiempo real
    String streamPath = "/usuarios/" + userId + "/puerta/historial";
    if (!Firebase.RTDB.beginStream(&stream, streamPath)) {
        Serial.println("No se pudo comenzar stream");
        Serial.println(stream.errorReason());
    }

    // Asignar función de callback para el stream
    Firebase.RTDB.setStreamCallback(&stream, streamCallback, streamTimeoutCallback);
    
    // Leer estado inicial
    leerUltimoEstado();
}

void loop() {
    if (Firebase.ready() && signupOK) {
        checkButtons();
    }
}

void checkButtons() {
    static bool lastStateOpen = HIGH;
    static bool lastStateClose = HIGH;
    static unsigned long lastDebounceTime = 0;
    const unsigned long debounceDelay = 50;

    bool currentStateOpen = digitalRead(BUTTON_OPEN);
    bool currentStateClose = digitalRead(BUTTON_CLOSE);

    // Debounce para botón de abrir
    if (currentStateOpen != lastStateOpen) {
        if ((millis() - lastDebounceTime) > debounceDelay) {
            if (currentStateOpen == LOW) {
                actualizarEstadoPuerta("Puerta Abierta", 1);
            }
            lastDebounceTime = millis();
        }
    }
    lastStateOpen = currentStateOpen;

    // Debounce para botón de cerrar
    if (currentStateClose != lastStateClose) {
        if ((millis() - lastDebounceTime) > debounceDelay) {
            if (currentStateClose == LOW) {
                actualizarEstadoPuerta("Puerta Cerrada", 2);
            }
            lastDebounceTime = millis();
        }
    }
    lastStateClose = currentStateClose;
}

void actualizarEstadoPuerta(const char* estado, int numParpadeos) {
    if (Firebase.ready() && signupOK) {
        struct tm timeinfo;
        if (!getLocalTime(&timeinfo)) {
            Serial.println("Error obteniendo la hora");
            return;
        }
        
        char timestamp[30];
        strftime(timestamp, sizeof(timestamp), "%m/%d/%Y %I:%M:%S %p", &timeinfo);

        FirebaseJson json;
        json.set("estado", estado);
        json.set("timestamp", timestamp);
        json.set("led", numParpadeos);

        String path = "/usuarios/" + userId + "/puerta/historial/";
        
        if (Firebase.RTDB.pushJSON(&fbdo, path, &json)) {
            Serial.println("Estado actualizado en Firebase");
            parpadearLED(numParpadeos);
        } else {
            Serial.println("Error: " + fbdo.errorReason());
        }
    }
}

void parpadearLED(int veces) {
    Serial.println("Parpadeando LED " + String(veces) + " veces");
    for (int i = 0; i < veces; i++) {
        digitalWrite(LED_PIN, HIGH);
        delay(500);
        digitalWrite(LED_PIN, LOW);
        if (i < veces - 1) {
            delay(500);
        }
    }
    
    // Mantener el LED en el estado final correspondiente
    if (veces == 1) {  // Puerta Abierta
        digitalWrite(LED_PIN, HIGH);
    } else if (veces == 2) {  // Puerta Cerrada
        digitalWrite(LED_PIN, LOW);
    }
}

void leerUltimoEstado() {
    String path = "/usuarios/" + userId + "/puerta/historial";
    
    if (Firebase.RTDB.getJSON(&fbdo, path)) {
        FirebaseJson *json = fbdo.to<FirebaseJson *>();
        FirebaseJsonData result;
        
        String ultimaClave;
        size_t count = json->iteratorBegin();
        FirebaseJson::IteratorValue value;
        
        // Obtener la última entrada
        for (size_t i = 0; i < count; i++) {
            value = json->valueAt(i);
            ultimaClave = value.key;
        }
        json->iteratorEnd();
        
        if (!ultimaClave.isEmpty()) {
            String estadoPath = path + "/" + ultimaClave + "/estado";
            if (Firebase.RTDB.getString(&fbdo, estadoPath)) {
                String estado = fbdo.stringData();
                Serial.println("Estado inicial: " + estado);
                if (estado == "Puerta Abierta") {
                    digitalWrite(LED_PIN, HIGH);
                } else if (estado == "Puerta Cerrada") {
                    digitalWrite(LED_PIN, LOW);
                }
            }
        }
    }
}