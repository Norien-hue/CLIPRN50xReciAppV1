package com.interfaces.app.controllers;

import com.google.gson.JsonObject;
import com.interfaces.app.utils.ApiClient;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class TapModalController {

    @FXML private TextField tapField;
    @FXML private Button verifyBtn;
    @FXML private Button cancelBtn;
    @FXML private Label resultLabel;
    @FXML private Label userNameLabel;
    @FXML private Label userEmissionsLabel;

    private ApiClient api;

    @FXML
    public void initialize() {
        api = ApiClient.getInstance();
    }

    @FXML
    private void verifyTap() {
        String tap = tapField.getText().trim();
        if (tap.isEmpty()) {
            resultLabel.setText("Please enter a TAP");
            resultLabel.setStyle("-fx-text-fill: red;");
            return;
        }

        try {
            JsonObject user = api.findByTap(tap);
            if (user != null) {
                String name = user.get("nombre").getAsString();
                String emisiones = user.get("emisionesReducidas").getAsString();

                userNameLabel.setText("User: " + name);
                userEmissionsLabel.setText("CO\u2082 saved: " + emisiones + " kg");
                resultLabel.setStyle("-fx-text-fill: -fx-verde-oscuro;");
                resultLabel.setText("TAP verified!");
            } else {
                resultLabel.setStyle("-fx-text-fill: red;");
                resultLabel.setText("Invalid TAP - user not found");
                userNameLabel.setText("");
                userEmissionsLabel.setText("");
            }
        } catch (Exception e) {
            resultLabel.setStyle("-fx-text-fill: red;");
            resultLabel.setText("Error: " + e.getMessage());
            userNameLabel.setText("");
            userEmissionsLabel.setText("");
        }
    }

    @FXML
    private void close() {
        Stage stage = (Stage) cancelBtn.getScene().getWindow();
        stage.close();
    }
}
