package com.interfaces.app.controllers;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
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
    private void whatAiSees() {
        try {
            BufferedImage clipInput = new BufferedImage(224, 224, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = clipInput.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(originalImage, 0, 0, 224, 224, null);
            g.dispose();

            Image fxImage = SwingFXUtils.toFXImage(clipInput, null);

            ImageView iv = new ImageView(fxImage);
            iv.setFitWidth(224);
            iv.setFitHeight(224);
            iv.setPreserveRatio(false);

            Label label = new Label("What CLIP RN50 sees (224\u00D7224)");
            label.setStyle("-fx-font-size: 11px; -fx-text-fill: #666;");

            VBox root = new VBox(10, label, iv);
            root.setStyle("-fx-padding: 15; -fx-background-color: white;");
            root.setAlignment(Pos.TOP_CENTER);

            Stage popup = new Stage();
            popup.setTitle("CLIP Input Preview");
            Scene scene = new Scene(root, 280, 310);
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
