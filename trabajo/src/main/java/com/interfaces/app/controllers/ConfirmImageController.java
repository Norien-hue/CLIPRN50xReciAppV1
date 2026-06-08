package com.interfaces.app.controllers;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
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
import java.util.ArrayDeque;
import java.util.Deque;

public class ConfirmImageController {

    @FXML private ImageView imageView;
    @FXML private Button sendBtn;
    @FXML private Button cancelBtn;
    @FXML private CheckBox bgCheckbox;
    @FXML private Slider sensitivitySlider;
    @FXML private Label sensitivityValueLabel;

    private boolean confirmed = false;
    private BufferedImage originalImage;
    private BufferedImage bgRemovedImage;

    public void setImage(BufferedImage image) {
        this.originalImage = image;
        imageView.setImage(SwingFXUtils.toFXImage(image, null));

        sensitivitySlider.valueProperty().addListener((obs, old, val) -> {
            int thresh = val.intValue();
            sensitivityValueLabel.setText(String.valueOf(thresh));
            if (bgCheckbox.isSelected()) {
                runRemoveBackground();
            }
        });

        bgCheckbox.selectedProperty().addListener((obs, old, selected) -> {
            sensitivitySlider.getParent().setVisible(selected);
            sensitivitySlider.getParent().setManaged(selected);
            if (selected) {
                runRemoveBackground();
            } else {
                bgRemovedImage = null;
                imageView.setImage(SwingFXUtils.toFXImage(originalImage, null));
            }
        });
    }

    private void runRemoveBackground() {
        bgCheckbox.setDisable(true);
        sensitivitySlider.setDisable(true);
        new Thread(() -> {
            try {
                int threshold = (int) sensitivitySlider.getValue();
                BufferedImage result = removeBackground(originalImage, threshold);
                Platform.runLater(() -> {
                    bgRemovedImage = result;
                    imageView.setImage(SwingFXUtils.toFXImage(result, null));
                    bgCheckbox.setDisable(false);
                    sensitivitySlider.setDisable(false);
                });
            } catch (Exception e) {
                System.err.println("[ConfirmImage] bg removal error: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> {
                    bgCheckbox.setSelected(false);
                    bgCheckbox.setDisable(false);
                    sensitivitySlider.setDisable(false);
                });
            }
        }, "bg-removal").start();
    }

    public BufferedImage getFinalImage() {
        return bgCheckbox.isSelected() && bgRemovedImage != null ? bgRemovedImage : originalImage;
    }

    private BufferedImage removeBackground(BufferedImage src, int threshold) {
        int w = src.getWidth();
        int h = src.getHeight();
        int[] pixels = new int[w * h];
        src.getRGB(0, 0, w, h, pixels, 0, w);

        int[] gray = new int[w * h];
        int[] grad = new int[w * h];
        computeGradient(pixels, gray, grad, w, h);

        int edgeThreshold = 20;
        boolean[] bg = new boolean[w * h];
        boolean[] visited = new boolean[w * h];
        int[][] starts = {{0, 0}, {w - 1, 0}, {0, h - 1}, {w - 1, h - 1}};
        int[] cornerColors = {pixels[0], pixels[w - 1], pixels[w * (h - 1)], pixels[w * h - 1]};

        Deque<Integer> queue = new ArrayDeque<>();
        for (int c = 0; c < 4; c++) {
            int sx = starts[c][0], sy = starts[c][1];
            int idx = sy * w + sx;
            if (visited[idx]) continue;
            int refColor = cornerColors[c];
            queue.addLast(idx);
            visited[idx] = true;

            while (!queue.isEmpty()) {
                int p = queue.removeFirst();
                int px = p % w, py = p / w;
                if (colorDist(pixels[p], refColor) < threshold && grad[p] < edgeThreshold) {
                    bg[p] = true;
                    if (px > 0) { int ni = py * w + (px - 1); if (!visited[ni]) { visited[ni] = true; queue.addLast(ni); } }
                    if (px < w - 1) { int ni = py * w + (px + 1); if (!visited[ni]) { visited[ni] = true; queue.addLast(ni); } }
                    if (py > 0) { int ni = (py - 1) * w + px; if (!visited[ni]) { visited[ni] = true; queue.addLast(ni); } }
                    if (py < h - 1) { int ni = (py + 1) * w + px; if (!visited[ni]) { visited[ni] = true; queue.addLast(ni); } }
                }
            }
        }

        boolean[] blurred = medianBlurMask(bg, w, h, 3);

        for (int i = 0; i < pixels.length; i++) {
            if (blurred[i]) {
                pixels[i] = 0xFFFFFFFF;
            }
        }

        BufferedImage dst = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        dst.setRGB(0, 0, w, h, pixels, 0, w);
        return dst;
    }

    private void computeGradient(int[] pixels, int[] gray, int[] grad, int w, int h) {
        for (int i = 0; i < pixels.length; i++) {
            int r = (pixels[i] >> 16) & 0xFF;
            int g = (pixels[i] >> 8) & 0xFF;
            int b = pixels[i] & 0xFF;
            gray[i] = (r * 77 + g * 151 + b * 28) >> 8;
        }
        for (int y = 1; y < h - 1; y++) {
            int row = y * w;
            for (int x = 1; x < w - 1; x++) {
                int idx = row + x;
                int gx = gray[row + x + 1] - gray[row + x - 1];
                int gy = gray[row + w + x] - gray[row - w + x];
                grad[idx] = Math.abs(gx) + Math.abs(gy);
            }
        }
    }

    private int colorDist(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xFF, g1 = (rgb1 >> 8) & 0xFF, b1 = rgb1 & 0xFF;
        int r2 = (rgb2 >> 16) & 0xFF, g2 = (rgb2 >> 8) & 0xFF, b2 = rgb2 & 0xFF;
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
    }

    private boolean[] medianBlurMask(boolean[] mask, int w, int h, int radius) {
        boolean[] result = new boolean[mask.length];
        int r = radius / 2;
        for (int y = r; y < h - r; y++) {
            for (int x = r; x < w - r; x++) {
                int idx = y * w + x;
                int trueCount = 0;
                int total = 0;
                for (int dy = -r; dy <= r; dy++) {
                    int row = (y + dy) * w;
                    for (int dx = -r; dx <= r; dx++) {
                        if (mask[row + x + dx]) trueCount++;
                        total++;
                    }
                }
                result[idx] = trueCount > total / 2;
            }
        }
        return result;
    }

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
