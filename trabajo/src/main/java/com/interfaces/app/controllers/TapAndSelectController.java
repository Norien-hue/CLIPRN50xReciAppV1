package com.interfaces.app.controllers;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.interfaces.app.utils.ApiClient;
import javafx.application.Platform;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

public class TapAndSelectController {

    @FXML private PasswordField tapField;
    @FXML private TextField searchField;
    @FXML private TableView<ProductMatch> productTable;
    @FXML private TableColumn<ProductMatch, String> nameColumn;
    @FXML private TableColumn<ProductMatch, Number> scoreColumn;
    @FXML private Button acceptBtn;
    @FXML private Button cancelBtn;
    @FXML private Label statusLabel;
    @FXML private VBox resultBox;
    @FXML private Label userNameLabel;
    @FXML private Label productNameLabel;
    @FXML private Label co2SavedLabel;
    @FXML private Label co2TotalLabel;

    @FXML private Button debugToggle;
    @FXML private VBox debugBox;
    @FXML private ImageView aiPreview;
    @FXML private Label embeddingLabel;
    @FXML private Label embeddingMeanLabel;
    @FXML private Label embeddingStdLabel;
    @FXML private VBox scoresBox;

    private ApiClient api;
    private boolean confirmed = false;

    private ObservableList<ProductMatch> masterData = FXCollections.observableArrayList();

    public static class ProductMatch {
        private final SimpleStringProperty tipo;
        private final SimpleStringProperty barcode;
        private final SimpleStringProperty displayName;
        private final SimpleDoubleProperty score;

        public ProductMatch(String tipo, String barcode, String displayName, double score) {
            this.tipo = new SimpleStringProperty(tipo);
            this.barcode = new SimpleStringProperty(barcode);
            this.displayName = new SimpleStringProperty(displayName);
            this.score = new SimpleDoubleProperty(score);
        }

        public String getTipo() { return tipo.get(); }
        public String getBarcode() { return barcode.get(); }
        public String getDisplayName() { return displayName.get(); }
        public double getScore() { return score.get(); }

        public SimpleStringProperty tipoProperty() { return tipo; }
        public SimpleStringProperty barcodeProperty() { return barcode; }
        public SimpleStringProperty displayNameProperty() { return displayName; }
        public SimpleDoubleProperty scoreProperty() { return score; }
    }

    @FXML
    public void initialize() {
        api = ApiClient.getInstance();

        nameColumn.setCellValueFactory(cell -> cell.getValue().displayNameProperty());
        scoreColumn.setCellValueFactory(cell -> cell.getValue().scoreProperty());
        scoreColumn.setCellFactory(col -> new TableCell<ProductMatch, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(String.format("%.1f%%", item.doubleValue() * 100));
                }
            }
        });

        searchField.textProperty().addListener((obs, old, search) -> {
            if (search == null || search.isEmpty()) {
                productTable.setItems(masterData);
            } else {
                String lower = search.toLowerCase();
                List<ProductMatch> filtered = new ArrayList<>();
                for (ProductMatch m : masterData) {
                    if (m.getDisplayName().toLowerCase().contains(lower)) {
                        filtered.add(m);
                    }
                }
                productTable.setItems(FXCollections.observableArrayList(filtered));
            }
            productTable.getSelectionModel().select(0);
        });

        debugToggle.setOnAction(e -> {
            debugBox.setVisible(!debugBox.isVisible());
            debugBox.setManaged(debugBox.isVisible());
        });
    }

    public void setResults(JsonArray results) {
        setResults(results, null, null);
    }

    public void setResults(JsonArray results, BufferedImage frame, JsonObject debug) {
        masterData.clear();

        List<ProductMatch> unsorted = new ArrayList<>();
        for (int i = 0; i < results.size(); i++) {
            JsonObject item = results.get(i).getAsJsonObject();
            String key = item.get("name").getAsString();
            double score = item.get("score").getAsDouble();

            String[] parts = key.split("_", 3);
            String tipo = parts.length > 0 ? parts[0] : "";
            String barcode = parts.length > 1 ? parts[1] : "";
            String displayName = parts.length > 2 ? parts[2] : key;

            unsorted.add(new ProductMatch(tipo, barcode, displayName, score));
        }

        unsorted.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        masterData.addAll(unsorted);

        productTable.setItems(masterData);
        if (!masterData.isEmpty()) {
            productTable.getSelectionModel().select(0);
        }

        if (debug != null) {
            populateDebug(debug);
            if (frame != null) {
                BufferedImage resized = new BufferedImage(224, 224, BufferedImage.TYPE_3BYTE_BGR);
                resized.getGraphics().drawImage(frame, 0, 0, 224, 224, null);
                try {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(resized, "jpeg", baos);
                    Image fxImg = new Image(new ByteArrayInputStream(baos.toByteArray()));
                    aiPreview.setImage(fxImg);
                } catch (Exception e) {
                    System.err.println("[TapAndSelect] Failed to create AI preview: " + e.getMessage());
                }
            }
            debugToggle.setVisible(true);
        } else {
            debugToggle.setVisible(false);
            debugBox.setVisible(false);
            debugBox.setManaged(false);
        }
    }

    private void populateDebug(JsonObject debug) {
        JsonArray first10 = debug.getAsJsonArray("query_embedding_first_10");
        if (first10 != null) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < first10.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(first10.get(i).getAsDouble());
            }
            sb.append(", ...]");
            embeddingLabel.setText(sb.toString());
        }

        if (debug.has("query_embedding_mean")) {
            embeddingMeanLabel.setText("mean: " + debug.get("query_embedding_mean").getAsDouble());
        }
        if (debug.has("query_embedding_std")) {
            embeddingStdLabel.setText("std: " + debug.get("query_embedding_std").getAsDouble());
        }

        JsonArray allScores = debug.getAsJsonArray("all_scores");
        if (allScores != null) {
            populateScoreBars(allScores);
        }
    }

    private void populateScoreBars(JsonArray allScores) {
        scoresBox.getChildren().clear();
        double maxScore = 1.0;

        for (int i = 0; i < allScores.size(); i++) {
            JsonObject item = allScores.get(i).getAsJsonObject();
            String name = item.get("name").getAsString();
            double score = item.get("score").getAsDouble();

            String[] parts = name.split("_", 3);
            String displayName = parts.length > 2 ? parts[2] : name;

            HBox row = new HBox(6);

            Label nameLabel = new Label(displayName);
            nameLabel.setMinWidth(180);
            nameLabel.setMaxWidth(180);

            Region bar = new Region();
            bar.setMinHeight(16);
            double pct = Math.max(0, Math.min(1, score / maxScore));
            bar.setMinWidth(120 * pct);
            bar.setStyle("-fx-background-color: #388e3c; -fx-background-radius: 3;");
            HBox.setHgrow(bar, Priority.NEVER);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label scoreLabel = new Label(String.format("%.2f", score));
            scoreLabel.setMinWidth(40);

            row.getChildren().addAll(nameLabel, bar, spacer, scoreLabel);
            scoresBox.getChildren().add(row);
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

        ProductMatch match = productTable.getSelectionModel().getSelectedItem();
        if (match == null) {
            statusLabel.setStyle("-fx-text-fill: red;");
            statusLabel.setText("Please select a product from the table");
            return;
        }

        acceptBtn.setDisable(true);
        cancelBtn.setDisable(true);
        statusLabel.setStyle("-fx-text-fill: black;");
        statusLabel.setText("Verifying TAP...");

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

                Platform.runLater(() -> statusLabel.setText("Registering recycling..."));

                JsonObject result = api.registerRecycling(userId, match.getTipo(), match.getBarcode());

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
                    searchField.setVisible(false);
                    productTable.setVisible(false);
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
