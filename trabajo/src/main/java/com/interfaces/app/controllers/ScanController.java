package com.interfaces.app.controllers;

import com.github.sarxos.webcam.Webcam;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.interfaces.app.utils.ApiClient;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScanController {

    @FXML private ComboBox<String> cameraCombo;
    @FXML private ImageView cameraView;
    @FXML private Button cameraToggle;
    @FXML private Button identifyBtn;
    @FXML private ComboBox<String> productMenu;

    private Webcam webcam;
    private Thread cameraThread;
    private AtomicBoolean cameraRunning = new AtomicBoolean(false);
    private BufferedImage lastFrame;

    @FXML
    public void initialize() {
        java.util.List<Webcam> webcams = Webcam.getWebcams();
        for (Webcam w : webcams) {
            cameraCombo.getItems().add(w.getName());
        }
        if (!webcams.isEmpty()) {
            cameraCombo.getSelectionModel().select(0);
        }
        resetProductMenu();
    }

    private void resetProductMenu() {
        productMenu.getItems().setAll(
            "Producto A - PET",
            "Producto B - Vidrio",
            "Producto C - Aluminio",
            "Producto D - Papel",
            "Producto E - Cart\u00f3n"
        );
        productMenu.getSelectionModel().selectFirst();
    }

    @FXML
    private void toggleCamera() {
        if (cameraRunning.get()) {
            stopCamera();
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        String selected = cameraCombo.getValue();
        if (selected == null) return;

        webcam = Webcam.getWebcamByName(selected);
        if (webcam == null) return;

        webcam.setViewSize(new java.awt.Dimension(640, 480));
        webcam.open();
        cameraRunning.set(true);
        cameraToggle.setText("Stop Camera");

        cameraThread = new Thread(() -> {
            while (cameraRunning.get() && webcam.isOpen()) {
                BufferedImage bi = webcam.getImage();
                if (bi != null) {
                    lastFrame = cropToSquare(bi);
                    Image fxImage = SwingFXUtils.toFXImage(lastFrame, null);
                    Platform.runLater(() -> cameraView.setImage(fxImage));
                }
            }
        });
        cameraThread.setDaemon(true);
        cameraThread.start();
    }

    private void stopCamera() {
        cameraRunning.set(false);
        if (cameraThread != null) {
            cameraThread.interrupt();
            cameraThread = null;
        }
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
            webcam = null;
        }
        cameraToggle.setText("Start Camera");
        cameraView.setImage(null);
        lastFrame = null;
    }

    private BufferedImage cropToSquare(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int size = Math.min(w, h);
        int x = (w - size) / 2;
        int y = (h - size) / 2;
        return src.getSubimage(x, y, size, size);
    }

    @FXML
    private void onIdentify() {
        if (lastFrame == null) return;
        identifyBtn.setDisable(true);
        identifyBtn.setText("Processing...");

        new Thread(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(lastFrame, "jpeg", baos);
                String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                String imageData = "data:image/jpeg;base64," + b64;

                ApiClient api = ApiClient.getInstance();
                String resultJson = api.matchProduct(imageData);

                Platform.runLater(() -> {
                    try {
                        JsonArray arr = new Gson().fromJson(resultJson, JsonArray.class);
                        productMenu.getItems().clear();
                        for (int i = 0; i < arr.size(); i++) {
                            JsonObject item = arr.get(i).getAsJsonObject();
                            String name = item.get("name").getAsString();
                            double score = item.get("score").getAsDouble();
                            productMenu.getItems().add(name + " (" + (int)(score * 100) + "%)");
                        }
                        if (!productMenu.getItems().isEmpty()) {
                            productMenu.getSelectionModel().selectFirst();
                        }
                    } finally {
                        identifyBtn.setDisable(false);
                        identifyBtn.setText("Identify");
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    identifyBtn.setDisable(false);
                    identifyBtn.setText("Identify");
                    System.err.println("Identify failed: " + e.getMessage());
                });
            }
        }).start();
    }

    @FXML
    private void onContinue() {
        showTapModal();
    }

    private void showTapModal() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/tap_modal.fxml"));
            VBox root = loader.load();

            Stage modal = new Stage();
            modal.initModality(Modality.APPLICATION_MODAL);
            modal.setTitle("Verify TAP");

            Scene scene = new Scene(root, 380, 250);
            scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            modal.setScene(scene);
            modal.showAndWait();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
