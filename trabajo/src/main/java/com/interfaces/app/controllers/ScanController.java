package com.interfaces.app.controllers;

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
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameUtils;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScanController {

    @FXML private ComboBox<String> cameraCombo;
    @FXML private ImageView cameraView;
    @FXML private Button cameraToggle;
    @FXML private Button loadImageBtn;
    @FXML private Button identifyBtn;
    @FXML private ComboBox<String> productMenu;
    @FXML private Label statusLabel;

    private OpenCVFrameGrabber grabber;
    private Thread cameraThread;
    private AtomicBoolean cameraRunning = new AtomicBoolean(false);
    private BufferedImage lastFrame;

    @FXML
    public void initialize() {
        System.out.println("[ScanController] initialize() started");
        for (int i = 0; i < 10; i++) {
            try {
                System.out.println("[ScanController] Trying camera index " + i);
                OpenCVFrameGrabber test = new OpenCVFrameGrabber(i);
                test.start();
                test.stop();
                test.release();
                cameraCombo.getItems().add("Camera " + i);
                System.out.println("[ScanController] Camera " + i + " found and added");
            } catch (Exception e) {
                System.out.println("[ScanController] Camera " + i + " failed: " + e.getMessage());
                break;
            }
        }
        if (!cameraCombo.getItems().isEmpty()) {
            cameraCombo.getSelectionModel().select(0);
            setStatus("Camera detected. Click Start Camera or load an image.");
        } else {
            setStatus("No camera found. Use 'Load Image' to select a file.");
            cameraToggle.setDisable(true);
        }
        resetProductMenu();
        System.out.println("[ScanController] initialize() finished, cameras: " + cameraCombo.getItems().size());
    }

    private void setStatus(String msg) {
        Platform.runLater(() -> statusLabel.setText(msg));
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
    private void onLoadImage() {
        System.out.println("[ScanController] onLoadImage()");
        FileChooser fc = new FileChooser();
        fc.setTitle("Select an image");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Image files", "*.jpg", "*.jpeg", "*.png", "*.bmp")
        );
        File file = fc.showOpenDialog(cameraView.getScene().getWindow());
        if (file == null) return;

        try {
            stopCamera();
            BufferedImage bi = ImageIO.read(file);
            if (bi == null) {
                setStatus("Could not read the image file.");
                return;
            }
            lastFrame = cropToSquare(bi);
            Image fxImage = SwingFXUtils.toFXImage(lastFrame, null);
            cameraView.setImage(fxImage);
            setStatus("Image loaded: " + file.getName() + ". Click Identify to send it.");
            System.out.println("[ScanController] Image loaded: " + file.getAbsolutePath());
        } catch (Exception e) {
            System.err.println("[ScanController] Load image error: " + e.getMessage());
            setStatus("Error loading image: " + e.getMessage());
        }
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
        if (cameraCombo.getValue() == null) return;
        int index = Integer.parseInt(cameraCombo.getValue().replace("Camera ", ""));
        System.out.println("[ScanController] startCamera(" + index + ")");

        try {
            grabber = new OpenCVFrameGrabber(index);
            grabber.setImageWidth(640);
            grabber.setImageHeight(480);
            grabber.start();
            cameraRunning.set(true);
            cameraToggle.setText("Stop Camera");
            setStatus("Camera running. Click Identify to capture the frame.");
            System.out.println("[ScanController] Camera started successfully");

            cameraThread = new Thread(() -> {
                while (cameraRunning.get()) {
                    try {
                        Frame frame = grabber.grab();
                        if (frame != null) {
                            BufferedImage bi = Java2DFrameUtils.toBufferedImage(frame);
                            lastFrame = cropToSquare(bi);
                            Image fxImage = SwingFXUtils.toFXImage(lastFrame, null);
                            Platform.runLater(() -> cameraView.setImage(fxImage));
                        }
                    } catch (Exception e) {
                        // skip bad frame
                    }
                }
                System.out.println("[ScanController] Camera thread ended");
            });
            cameraThread.setDaemon(true);
            cameraThread.start();
        } catch (Exception e) {
            System.err.println("[ScanController] Camera error: " + e.getMessage());
            e.printStackTrace();
            setStatus("Camera error: " + e.getMessage());
        }
    }

    private void stopCamera() {
        cameraRunning.set(false);
        if (cameraThread != null) {
            try {
                cameraThread.join(1000);
            } catch (InterruptedException ignored) {}
            cameraThread = null;
        }
        if (grabber != null) {
            try {
                grabber.stop();
                grabber.release();
            } catch (Exception ignored) {}
            grabber = null;
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
        if (lastFrame == null) {
            setStatus("No image to identify. Start camera or load an image first.");
            return;
        }
        identifyBtn.setDisable(true);
        identifyBtn.setText("Processing...");
        setStatus("Identifying... sending image to server.");
        System.out.println("[ScanController] onIdentify() - sending image to API");

        new Thread(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                BufferedImage rgb = new BufferedImage(lastFrame.getWidth(), lastFrame.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
                rgb.getGraphics().drawImage(lastFrame, 0, 0, null);
                ImageIO.write(rgb, "jpeg", baos);
                String b64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                String imageData = "data:image/jpeg;base64," + b64;

                ApiClient api = ApiClient.getInstance();
                String resultJson = api.matchProduct(imageData);
                System.out.println("[ScanController] API response: " + resultJson);

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
                            setStatus("Identification complete. Select a product and click Continue.");
                        }
                    } finally {
                        identifyBtn.setDisable(false);
                        identifyBtn.setText("Identify");
                    }
                });
            } catch (Exception e) {
                System.err.println("[ScanController] Identify failed: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    identifyBtn.setDisable(false);
                    identifyBtn.setText("Identify");
                    setStatus("Identify failed: " + e.getMessage());
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
