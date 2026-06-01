package com.interfaces.app.controllers;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class ConfirmImageController {

    @FXML private ImageView imageView;
    @FXML private Button sendBtn;
    @FXML private Button cancelBtn;

    private boolean confirmed = false;
    private BufferedImage originalImage;

    public void setImage(BufferedImage image) {
        this.originalImage = image;
        imageView.setImage(SwingFXUtils.toFXImage(image, null));
    }

    @FXML
    private void preview() {
        try {
            BufferedImage rgb = new BufferedImage(originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            rgb.getGraphics().drawImage(originalImage, 0, 0, null);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(rgb, "jpeg", baos);
            baos.flush();

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            BufferedImage processed = ImageIO.read(bais);
            Image fxImage = SwingFXUtils.toFXImage(processed, null);

            ImageView previewView = new ImageView(fxImage);
            previewView.setFitWidth(450);
            previewView.setFitHeight(450);
            previewView.setPreserveRatio(false);

            VBox root = new VBox(previewView);
            root.setStyle("-fx-padding: 10; -fx-background-color: white;");

            Stage popup = new Stage();
            popup.initModality(Modality.NONE);
            popup.setTitle("After JPEG Processing");
            Scene scene = new Scene(root, 470, 470);
            popup.setScene(scene);
            popup.show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void send() {
        confirmed = true;
        close();
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
