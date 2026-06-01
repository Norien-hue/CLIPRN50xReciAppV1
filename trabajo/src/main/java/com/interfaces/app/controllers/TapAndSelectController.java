package com.interfaces.app.controllers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.interfaces.app.utils.ApiClient;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class TapAndSelectController {

    @FXML private PasswordField tapField;
    @FXML private ComboBox<String> productCombo;
    @FXML private Button acceptBtn;
    @FXML private Button cancelBtn;
    @FXML private Label statusLabel;
    @FXML private VBox resultBox;
    @FXML private Label userNameLabel;
    @FXML private Label productNameLabel;
    @FXML private Label co2SavedLabel;
    @FXML private Label co2TotalLabel;

    private ApiClient api;
    private boolean confirmed = false;

    private List<String> allItems = new ArrayList<>();
    private List<ProductMatch> allMatches = new ArrayList<>();

    private static class ProductMatch {
        String tipo;
        String barcode;
        String displayName;
        double score;

        ProductMatch(String tipo, String barcode, String displayName, double score) {
            this.tipo = tipo;
            this.barcode = barcode;
            this.displayName = displayName;
            this.score = score;
        }

        String displayString() {
            return String.format("%s (%d%%)", displayName, (int)(score * 100));
        }
    }

    @FXML
    public void initialize() {
        api = ApiClient.getInstance();

        productCombo.setEditable(true);
        productCombo.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null || newVal.isEmpty()) {
                productCombo.getItems().setAll(allItems);
            } else {
                List<String> filtered = new ArrayList<>();
                String lower = newVal.toLowerCase();
                for (int i = 0; i < allMatches.size(); i++) {
                    if (allMatches.get(i).displayName.toLowerCase().contains(lower)) {
                        filtered.add(allItems.get(i));
                    }
                }
                productCombo.getItems().setAll(filtered);
            }
            if (!productCombo.isShowing()) {
                productCombo.show();
            }
        });
    }

    public void setResults(JsonArray results) {
        allMatches.clear();
        allItems.clear();

        for (int i = 0; i < results.size(); i++) {
            JsonObject item = results.get(i).getAsJsonObject();
            String key = item.get("name").getAsString();
            double score = item.get("score").getAsDouble();

            String[] parts = key.split("_", 3);
            String tipo = parts.length > 0 ? parts[0] : "";
            String barcode = parts.length > 1 ? parts[1] : "";
            String displayName = parts.length > 2 ? parts[2] : key;

            allMatches.add(new ProductMatch(tipo, barcode, displayName, score));
        }

        allMatches.sort((a, b) -> Double.compare(b.score, a.score));

        for (ProductMatch m : allMatches) {
            allItems.add(m.displayString());
        }

        productCombo.getItems().setAll(allItems);
        if (!allItems.isEmpty()) {
            productCombo.getSelectionModel().select(0);
        }
    }

    @FXML
    private void accept() {
        String tap = tapField.getText().trim();
        if (tap.isEmpty()) {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText("Please enter a TAP");
            return;
        }

        int selectedIdx = findSelectedIndex();
        if (selectedIdx < 0) {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText("Please select a product from the list");
            return;
        }

        acceptBtn.setDisable(true);
        cancelBtn.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: black;");
        statusLabel.setText("Verifying TAP...");

        ProductMatch match = allMatches.get(selectedIdx);

        new Thread(() -> {
            try {
                if (!api.login()) {
                    Platform.runLater(() -> {
                        statusLabel.setStyle("-fx-text-fill: red;");
                        statusLabel.setText("API authentication failed");
                        acceptBtn.setDisable(false);
                        cancelBtn.setDisable(false);
                    });
                    return;
                }

                JsonObject user = api.findByTap(tap);
                if (user == null) {
                    Platform.runLater(() -> {
                        statusLabel.setStyle("-fx-text-fill: red;");
                        statusLabel.setText("Invalid TAP - user not found");
                        acceptBtn.setDisable(false);
                        cancelBtn.setDisable(false);
                    });
                    return;
                }

                int userId = user.get("id").getAsInt();
                String userName = user.get("nombre").getAsString();
                double userEmisiones = user.get("emisionesReducidas").getAsDouble();

                Platform.runLater(() -> statusLabel.setText("Registering recycling..."));

                JsonObject result = api.registerRecycling(userId, match.tipo, match.barcode);

                Platform.runLater(() -> {
                    JsonObject reciclaje = result.get("reciclaje").getAsJsonObject();
                    String prodName = reciclaje.get("productoNombre").getAsString();
                    String prodMaterial = reciclaje.get("productoMaterial").getAsString();
                    double saved = reciclaje.get("emisionesReducibles").getAsDouble();
                    double total = result.get("emisionesAcumuladas").getAsDouble();

                    confirmed = true;

                    userNameLabel.setText("User: " + userName);
                    productNameLabel.setText("Product: " + prodName + " (" + prodMaterial + ")");
                    co2SavedLabel.setText("CO\u2082 saved this time: " + saved + " kg");
                    co2TotalLabel.setText("Total accumulated: " + total + " kg CO\u2082");

                    tapField.setVisible(false);
                    productCombo.setVisible(false);
                    acceptBtn.setText("OK");
                    acceptBtn.setOnAction(e -> close());
                    acceptBtn.setDisable(false);
                    cancelBtn.setVisible(false);
                    statusLabel.setText("");
                    resultBox.setVisible(true);
                    resultBox.setManaged(true);
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setStyle("-fx-text-fill: red;");
                    statusLabel.setText("Error: " + e.getMessage());
                    acceptBtn.setDisable(false);
                    cancelBtn.setDisable(false);
                });
            }
        }).start();
    }

    private int findSelectedIndex() {
        String selected = productCombo.getSelectionModel().getSelectedItem();
        if (selected == null) {
            String typed = productCombo.getEditor().getText().trim();
            if (!typed.isEmpty()) {
                for (int i = 0; i < allItems.size(); i++) {
                    if (allItems.get(i).equals(typed)) {
                        return i;
                    }
                }
            }
            return -1;
        }
        return allItems.indexOf(selected);
    }

    @FXML
    private void cancel() {
        confirmed = false;
        close();
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    private void close() {
        Stage stage = (Stage) cancelBtn.getScene().getWindow();
        stage.close();
    }
}
