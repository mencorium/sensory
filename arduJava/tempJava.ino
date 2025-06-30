#include <DHT.h>

#define DHTPIN 2
#define DHTTYPE DHT22
#define G4 392
#define buzzerPin 9  // Fixed spelling (was "buzerPin")

DHT dht(DHTPIN, DHTTYPE);

void setup() {
  Serial.begin(9600);
  dht.begin();
  pinMode(buzzerPin, OUTPUT);  // Added: Set buzzer pin as output
}

void loop() {
  float tempC = dht.readTemperature();
  
  if (!isnan(tempC)) {
    Serial.println(tempC);
    tone(buzzerPin, G4, 800);  // Fixed: Corrected spelling and added missing semicolon
  }
  delay(2000);  // DHT22 needs 2 seconds between readings
}