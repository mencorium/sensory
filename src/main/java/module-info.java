module com.example.sensory {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires com.fazecast.jSerialComm;
    requires java.logging;

    // Open packages for FXML loading and reflection
    opens com.example.sensory to javafx.fxml;
    opens com.example.sensory.controller to javafx.fxml;
    opens com.example.sensory.Model to javafx.fxml;

    // Export packages
    exports com.example.sensory;          // Main application package
    exports com.example.sensory.controller; // Controller classes
    exports com.example.sensory.Model;    // Model classes (if you have any)
}