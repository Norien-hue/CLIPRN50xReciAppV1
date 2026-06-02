package com.interfaces.app.controllers;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
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

import org.bytedeco.javacv.Java2DFrameUtils;
import org.bytedeco.javacv.OpenCVFrameConverter;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

public class ConfirmImageController {

    @FXML private ImageView imageView;
    @FXML private Button sendBtn;
    @FXML private Button cancelBtn;
    @FXML private CheckBox bgCheckbox;

    private boolean confirmed = false;
    private BufferedImage originalImage;
    private BufferedImage bgRemovedImage;

    public void setImage(BufferedImage image) {
        this.originalImage = image;
        imageView.setImage(SwingFXUtils.toFXImage(image, null));

        bgCheckbox.selectedProperty().addListener((obs, old, selected) -> {
            if (selected) {
                new Thread(() -> {
                    try {
                        BufferedImage result = removeBackground(originalImage);
                        Platform.runLater(() -> {
                            bgRemovedImage = result;
                            imageView.setImage(SwingFXUtils.toFXImage(result, null));
                        });
                    } catch (Exception e) {
                        System.err.println("[ConfirmImage] bg removal error: " + e.getMessage());
                        e.printStackTrace();
                        Platform.runLater(() -> {
                            bgCheckbox.setSelected(false);
                        });
                    }
                }).start();
            } else {
                imageView.setImage(SwingFXUtils.toFXImage(originalImage, null));
            }
        });
    }

    public BufferedImage getFinalImage() {
        return bgCheckbox.isSelected() && bgRemovedImage != null ? bgRemovedImage : originalImage;
    }

    // ------------------------------------------------------------------
    //  Background removal via OpenCV GrabCut
    // ------------------------------------------------------------------
    private BufferedImage removeBackground(BufferedImage src) {
        OpenCVFrameConverter.ToMat converter = new OpenCVFrameConverter.ToMat();

        BufferedImage rgb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        rgb.getGraphics().drawImage(src, 0, 0, null);

        Mat mat = converter.convert(Java2DFrameUtils.toFrame(rgb));
        int w = mat.cols(), h = mat.rows();

        int marginX = (int)(w * 0.05);
        int marginY = (int)(h * 0.05);
        Rect rect = new Rect(marginX, marginY, w - 2 * marginX, h - 2 * marginY);

        Mat mask = new Mat();
        Mat bgdModel = new Mat();
        Mat fgdModel = new Mat();

        grabCut(mat, mask, rect, bgdModel, fgdModel, 5, GC_INIT_WITH_RECT);

        Mat result = mat.clone();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int mv = mask.ptr(y, x).get(0) & 0xFF;
                if (mv == GC_BGD || mv == GC_PR_BGD) {
                    result.ptr(y, x).put((byte)255, (byte)255, (byte)255);
                }
            }
        }

        BufferedImage dst = Java2DFrameUtils.toBufferedImage(converter.convert(result));
        mat.release(); mask.release(); bgdModel.release(); fgdModel.release(); result.release();
        return dst;
    }

    // ------------------------------------------------------------------
    //  Actions
    // ------------------------------------------------------------------
    @FXML
    private void preview() {
        try {
            BufferedImage img = getFinalImage();
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            rgb.getGraphics().drawImage(img, 0, 0, null);

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
            BufferedImage img = getFinalImage();
            BufferedImage clipInput = new BufferedImage(224, 224, BufferedImage.TYPE_3BYTE_BGR);
            Graphics2D g = clipInput.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, 224, 224, null);
            g.dispose();

            Image fxImage = SwingFXUtils.toFXImage(clipInput, null);

            ImageView iv = new ImageView(fxImage);
            iv.setFitWidth(224);
            iv.setFitHeight(224);
            iv.setPreserveRatio(false);

            Label label = new Label("What CLIP sees (224\u00D7224)");
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
