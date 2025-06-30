package com.example.sensory.controller;

import com.example.sensory.Model.TemperatureData;
import com.fazecast.jSerialComm.SerialPort;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.FileChooser;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;
import java.util.logging.Level;

public class DashboardController {
    private static final Logger LOGGER = Logger.getLogger(DashboardController.class.getName());

    // UI Controls
    @FXML private ComboBox<String> portComboBox;
    @FXML private Button connectButton;
    @FXML private Button disconnectButton;
    @FXML private Label statusLabel;
    @FXML private Label currentTempLabel;
    @FXML private Label maxTempLabel;
    @FXML private Label minTempLabel;
    @FXML private Label avgTempLabel;
    @FXML private Button exportButton;
    @FXML private LineChart<Number, Number> tempChart;
    @FXML private NumberAxis xAxis;
    @FXML private NumberAxis yAxis;
    @FXML private Button clearButton;
    @FXML private ComboBox<String> timeRangeComboBox;
    @FXML private CheckBox autoScaleCheckBox;

    // Data Management
    private final XYChart.Series<Number, Number> tempSeries = new XYChart.Series<>();
    private final List<TemperatureData> temperatureData = new CopyOnWriteArrayList<>();
    private final BlockingQueue<TemperatureData> newDataQueue = new LinkedBlockingQueue<>();

    // Connection Management
    private SerialPort arduinoPort;
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private long startTime;
    private Thread serialReaderThread;
    private Thread dataProcessorThread;

    // Configuration
    private double currentTimeRange = 60.0;
    private static final int MAX_CHART_POINTS = 1000;
    private static final long CHART_UPDATE_INTERVAL_MS = 100; // Update every 100ms
    private long lastChartUpdate = 0;

    @FXML
    public void initialize() {
        setupChart();
        setupControls();
        startDataProcessor();
        LOGGER.info("Dashboard controller initialized");
    }

    private void setupChart() {
        tempSeries.setName("Temperature (°C)");
        tempChart.getData().add(tempSeries);
        tempChart.setAnimated(false); // Better performance for real-time updates
        tempChart.setCreateSymbols(false); // Remove symbols for better performance

        // Setup Y-axis
        yAxis.setAutoRanging(false);
        yAxis.setLowerBound(0);
        yAxis.setUpperBound(50);
        yAxis.setTickUnit(5);

        // Setup X-axis
        xAxis.setAutoRanging(false);
        xAxis.setLowerBound(0);
        xAxis.setUpperBound(60);
        xAxis.setTickUnit(10);
        xAxis.setLabel("Time (seconds)");
        yAxis.setLabel("Temperature (°C)");
    }

    private void setupControls() {
        refreshPortList();

        timeRangeComboBox.getItems().addAll(
                "30 seconds", "60 seconds", "120 seconds", "300 seconds", "All data"
        );
        timeRangeComboBox.setValue("60 seconds");

        timeRangeComboBox.setOnAction(e -> {
            String selected = timeRangeComboBox.getValue();
            if (selected.equals("All data")) {
                currentTimeRange = Double.MAX_VALUE;
            } else {
                currentTimeRange = Double.parseDouble(selected.split(" ")[0]);
            }
            updateChartRange();
        });

        autoScaleCheckBox.setOnAction(e -> {
            yAxis.setAutoRanging(autoScaleCheckBox.isSelected());
            if (!autoScaleCheckBox.isSelected()) {
                yAxis.setLowerBound(0);
                yAxis.setUpperBound(50);
            }
        });

        disconnectButton.setDisable(true);
        exportButton.setDisable(true);
        clearButton.setDisable(true);
    }

    private void startDataProcessor() {
        dataProcessorThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Process new data from the queue
                    TemperatureData newData = newDataQueue.take();

                    // Add to main data list
                    temperatureData.add(newData);

                    // Update UI on JavaFX thread
                    Platform.runLater(() -> {
                        updateCurrentTemperature(newData);
                        updateStatistics();
                        updateChartIfNeeded();
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error processing temperature data", e);
                }
            }
        });
        dataProcessorThread.setName("DataProcessor");
        dataProcessorThread.setDaemon(true);
        dataProcessorThread.start();
    }

    private void updateChartIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastChartUpdate >= CHART_UPDATE_INTERVAL_MS) {
            updateChart();
            lastChartUpdate = currentTime;
        }
    }

    private void refreshPortList() {
        Platform.runLater(() -> {
            portComboBox.getItems().clear();
            SerialPort[] ports = SerialPort.getCommPorts();
            for (SerialPort port : ports) {
                portComboBox.getItems().add(port.getSystemPortName() + " - " + port.getDescriptivePortName());
            }
            if (!portComboBox.getItems().isEmpty()) {
                portComboBox.getSelectionModel().select(0);
            }
        });
    }

    @FXML
    private void handleConnect() {
        if (portComboBox.getSelectionModel().isEmpty()) {
            showAlert("Error", "Please select a COM port");
            return;
        }

        String portName = portComboBox.getValue().split(" - ")[0];
        arduinoPort = SerialPort.getCommPort(portName);
        arduinoPort.setBaudRate(9600);
        arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);

        if (arduinoPort.openPort()) {
            // Wait a moment for the connection to stabilize
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            isConnected.set(true);
            startTime = System.currentTimeMillis();
            temperatureData.clear();
            newDataQueue.clear();
            tempSeries.getData().clear();

            updateUIForConnectedState();
            startSerialReaderThread();
            LOGGER.info("Connected to port: " + portName);
        } else {
            showAlert("Error", "Failed to connect to " + portName);
        }
    }

    private void updateUIForConnectedState() {
        Platform.runLater(() -> {
            statusLabel.setText("Status: Connected");
            statusLabel.setStyle("-fx-text-fill: green;");
            connectButton.setDisable(true);
            disconnectButton.setDisable(false);
            exportButton.setDisable(false);
            clearButton.setDisable(false);
            currentTempLabel.setText("--.-");
            maxTempLabel.setText("--.-");
            minTempLabel.setText("--.-");
            avgTempLabel.setText("--.-");
        });
    }

    private void startSerialReaderThread() {
        serialReaderThread = new Thread(() -> {
            try (Scanner scanner = new Scanner(arduinoPort.getInputStream())) {
                LOGGER.info("Serial reader thread started");

                while (isConnected.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        if (scanner.hasNextLine()) {
                            String line = scanner.nextLine().trim();
                            if (!line.isEmpty()) {
                                processIncomingData(line);
                            }
                        }

                        // Small delay to prevent excessive CPU usage
                        Thread.sleep(2000);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error reading serial data", e);
                    }
                }

            } catch (Exception e) {
                if (isConnected.get()) {
                    LOGGER.log(Level.SEVERE, "Serial connection error", e);
                    Platform.runLater(() -> {
                        showAlert("Error", "Connection error: " + e.getMessage());
                        handleDisconnect();
                    });
                }
            }
            LOGGER.info("Serial reader thread stopped");
        });

        serialReaderThread.setName("SerialReader");
        serialReaderThread.setDaemon(true);
        serialReaderThread.start();
    }

    private void processIncomingData(String line) {
        try {
            // Try to parse the temperature value
            double temperature = Double.parseDouble(line);

            // Validate temperature range (optional)
            if (temperature < -50 || temperature > 150) {
                LOGGER.warning("Temperature out of expected range: " + temperature);
                return;
            }

            long currentTime = System.currentTimeMillis() - startTime;
            TemperatureData dataPoint = new TemperatureData(
                    currentTime / 1000.0,
                    temperature,
                    LocalDateTime.now()
            );

            // Add to processing queue
            if (!newDataQueue.offer(dataPoint)) {
                LOGGER.warning("Data queue full, dropping data point");
            }

        } catch (NumberFormatException e) {
            LOGGER.warning("Invalid temperature data received: " + line);
        }
    }

    private void updateCurrentTemperature(TemperatureData data) {
        currentTempLabel.setText(String.format("%.1f°C", data.getTemperature()));
    }

    private void updateChart() {
        if (temperatureData.isEmpty()) {
            return;
        }

        double currentTime = (System.currentTimeMillis() - startTime) / 1000.0;
        double minTime = currentTimeRange == Double.MAX_VALUE ? 0 : Math.max(0, currentTime - currentTimeRange);

        // Create new data list for the visible time range
        ObservableList<XYChart.Data<Number, Number>> newChartData = FXCollections.observableArrayList();

        // Add data points within the time range
        temperatureData.stream()
                .filter(data -> data.getTime() >= minTime)
                .limit(MAX_CHART_POINTS) // Limit points for performance
                .forEach(data -> newChartData.add(new XYChart.Data<>(data.getTime(), data.getTemperature())));

        // Update chart data
        tempSeries.getData().setAll(newChartData);

        // Update axis range
        updateChartRange();
    }

    private void updateChartRange() {
        if (temperatureData.isEmpty()) {
            return;
        }

        double currentTime = (System.currentTimeMillis() - startTime) / 1000.0;

        if (currentTimeRange == Double.MAX_VALUE) {
            // Show all data
            double maxTime = temperatureData.get(temperatureData.size() - 1).getTime();
            xAxis.setLowerBound(0);
            xAxis.setUpperBound(Math.max(maxTime, 10)); // Minimum 10 seconds
            xAxis.setTickUnit(Math.max(1, maxTime / 10));
        } else {
            // Show sliding time window
            double minTime = Math.max(0, currentTime - currentTimeRange);
            double maxTime = Math.max(minTime + currentTimeRange, currentTime + 5); // Add 5 second buffer

            xAxis.setLowerBound(minTime);
            xAxis.setUpperBound(maxTime);
            xAxis.setTickUnit(currentTimeRange / 6);
        }
    }

    @FXML
    private void handleDisconnect() {
        isConnected.set(false);

        if (serialReaderThread != null) {
            serialReaderThread.interrupt();
            try {
                serialReaderThread.join(1000); // Wait up to 1 second
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (arduinoPort != null && arduinoPort.isOpen()) {
            arduinoPort.closePort();
        }

        Platform.runLater(() -> {
            statusLabel.setText("Status: Disconnected");
            statusLabel.setStyle("-fx-text-fill: red;");
            connectButton.setDisable(false);
            disconnectButton.setDisable(true);
            exportButton.setDisable(true);
            clearButton.setDisable(true);
        });

        LOGGER.info("Disconnected from Arduino");
    }

    @FXML
    private void handleClear() {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Clear Data");
        confirmation.setHeaderText("Clear all temperature data?");
        confirmation.setContentText("This action cannot be undone.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                temperatureData.clear();
                newDataQueue.clear();
                tempSeries.getData().clear();
                startTime = System.currentTimeMillis();

                Platform.runLater(() -> {
                    currentTempLabel.setText("--.-");
                    maxTempLabel.setText("--.-");
                    minTempLabel.setText("--.-");
                    avgTempLabel.setText("--.-");
                    showAlert("Success", "All data cleared successfully");
                });
            }
        });
    }

    @FXML
    private void handleExport() {
        if (temperatureData.isEmpty()) {
            showAlert("Error", "No data to export");
            return;
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Temperature Data");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        fileChooser.setInitialFileName("temperature_data_" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");

        File file = fileChooser.showSaveDialog(exportButton.getScene().getWindow());
        if (file != null) {
            exportDataToFile(file);
        }
    }

    private void exportDataToFile(File file) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            writer.write("Time (s),Temperature (°C),Timestamp\n");
            for (TemperatureData data : temperatureData) {
                writer.write(String.format("%.1f,%.2f,%s\n",
                        data.getTime(),
                        data.getTemperature(),
                        data.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                ));
            }
            showAlert("Success", "Data exported to:\n" + file.getAbsolutePath());
        } catch (IOException e) {
            showAlert("Error", "Export failed: " + e.getMessage());
        }
    }

    private void updateStatistics() {
        if (!temperatureData.isEmpty()) {
            double max = temperatureData.stream()
                    .mapToDouble(TemperatureData::getTemperature)
                    .max().orElse(0);

            double min = temperatureData.stream()
                    .mapToDouble(TemperatureData::getTemperature)
                    .min().orElse(0);

            double avg = temperatureData.stream()
                    .mapToDouble(TemperatureData::getTemperature)
                    .average().orElse(0);

            maxTempLabel.setText(String.format("%.1f°C", max));
            minTempLabel.setText(String.format("%.1f°C", min));
            avgTempLabel.setText(String.format("%.1f°C", avg));
        }
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void shutdown() {
        handleDisconnect();

        if (dataProcessorThread != null) {
            dataProcessorThread.interrupt();
            try {
                dataProcessorThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        LOGGER.info("Dashboard controller shutdown complete");
    }
}