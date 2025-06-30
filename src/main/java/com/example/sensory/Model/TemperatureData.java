package com.example.sensory.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class TemperatureData {
    private final double time;
    private final double temperature;
    private final LocalDateTime timestamp;

public TemperatureData(double time, double temperature, LocalDateTime timestamp){
    this.time = time;
    this.temperature = temperature;
    this.timestamp = timestamp;
}

public double getTime() {
    return time;
}

public double getTemperature() {
    return temperature;
}

public LocalDateTime getTimestamp() {
    return timestamp;
}

}
