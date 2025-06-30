package com.example.sensory;

import com.fazecast.jSerialComm.*;

public class Temperature {
    public static void main(String[] args) {
        // List available serial ports
        SerialPort[] ports = SerialPort.getCommPorts();
        System.out.println("Available ports:");
        for (SerialPort port : ports) {
            System.out.println(port.getSystemPortName());
        }

        // Open the port your Arduino is connected to
        SerialPort arduinoPort = SerialPort.getCommPorts()[2]; // adjust index as needed
        arduinoPort.setBaudRate(9600);

        if (arduinoPort.openPort()) {
            System.out.println("Port opened successfully.");
        } else {
            System.out.println("Unable to open port.");
            return;
        }

        // Read data
        try (java.util.Scanner scanner = new java.util.Scanner(arduinoPort.getInputStream())) {
            while (scanner.hasNextLine()) {
                try {
                    String line = scanner.nextLine();
                    float temperature = Float.parseFloat(line);
                    System.out.printf("Current temperature: %.2f°C%n", temperature);

                    // Here you could add database storage, GUI updates, etc.
                } catch (NumberFormatException e) {
                    System.err.println("Received invalid data: " + e.getMessage());
                }
            }
        }

        arduinoPort.closePort();
    }
}