package com.example.sensory;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Main extends Application {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static final String APP_TITLE = "Arduino Temperature Dashboard";
    private static final int DEFAULT_WIDTH = 1000;
    private static final int DEFAULT_HEIGHT = 600;
    private static final int MIN_WIDTH = 800;
    private static final int MIN_HEIGHT = 600;

    @Override
    public void start(Stage primaryStage) {
        try {
            // Load FXML with absolute path from resources root
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/example/sensory/dashboard.fxml")
            );

            if (loader.getLocation() == null) {
                LOGGER.severe("FXML file not found: /com/example/sensory/dashboard.fxml");
                showFallbackUI(primaryStage);
                return;
            }

            Parent root = loader.load();
            Scene scene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);

            // Load CSS with proper resource path (optional)
            loadCSS(scene);

            // Configure and show the stage
            configureStage(primaryStage, scene);

            LOGGER.info("Application started successfully");

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load FXML file", e);
            showErrorDialog("FXML Loading Error",
                    "Failed to load the dashboard interface.",
                    "The application will start with a basic interface.");
            showFallbackUI(primaryStage);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error during application startup", e);
            showErrorDialog("Startup Error",
                    "An unexpected error occurred during startup.",
                    "Please check the application logs for details.");
            showFallbackUI(primaryStage);
        }
    }

    private void loadCSS(Scene scene) {
        try {
            String cssResource = "/com/example/sensory/chart.css";
            var cssUrl = getClass().getResource(cssResource);

            if (cssUrl != null) {
                scene.getStylesheets().add(cssUrl.toExternalForm());
                LOGGER.info("CSS loaded successfully");
            } else {
                LOGGER.warning("CSS file not found: " + cssResource + " - continuing without styling");
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to load CSS file - continuing without styling", e);
        }
    }

    private void configureStage(Stage stage, Scene scene) {
        stage.setTitle(APP_TITLE);
        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setOnCloseRequest(e -> {
            LOGGER.info("Application closing");
            Platform.exit();
        });
        stage.show();
    }

    private void showFallbackUI(Stage stage) {
        try {
            VBox root = new VBox(20);
            root.setStyle("-fx-padding: 20; -fx-alignment: center;");

            Label titleLabel = new Label(APP_TITLE);
            titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

            Label messageLabel = new Label("The main interface could not be loaded.");
            messageLabel.setStyle("-fx-font-size: 14px;");

            Label instructionLabel = new Label("Please check that all required files are present and try again.");
            instructionLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: gray;");

            Button exitButton = new Button("Exit Application");
            exitButton.setOnAction(e -> {
                LOGGER.info("User requested application exit");
                Platform.exit();
            });
            exitButton.setStyle("-fx-font-size: 14px; -fx-padding: 10 20;");

            root.getChildren().addAll(titleLabel, messageLabel, instructionLabel, exitButton);

            Scene fallbackScene = new Scene(root, DEFAULT_WIDTH, DEFAULT_HEIGHT);
            configureStage(stage, fallbackScene);

            LOGGER.info("Fallback UI displayed");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create fallback UI", e);
            showCriticalErrorAndExit("Critical Error",
                    "The application cannot start due to a critical error.");
        }
    }

    private void showErrorDialog(String title, String header, String content) {
        try {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);
            alert.showAndWait();
        } catch (Exception e) {
            // If we can't even show a dialog, log and continue
            LOGGER.log(Level.SEVERE, "Failed to show error dialog: " + title, e);
        }
    }

    private void showCriticalErrorAndExit(String title, String message) {
        try {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText("Critical Application Error");
            alert.setContentText(message + "\n\nThe application will now exit.");
            alert.showAndWait();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to show critical error dialog", e);
        } finally {
            LOGGER.severe("Application exiting due to critical error");
            Platform.exit();
        }
    }

    public static void main(String[] args) {
        // Set up logging
        Logger.getLogger("com.example.sensory").setLevel(Level.INFO);

        try {
            launch(args);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to launch JavaFX application", e);
            System.err.println("Critical error: Unable to start the application");
            System.exit(1);
        }
    }
}