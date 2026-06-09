package com.interfaces.app.utils;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class LoadingOverlay {

    private static final int TIMEOUT_SECONDS = 50;

    public static Stage show(String message, Stage owner) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(50, 50);
        spinner.setStyle("-fx-progress-color: #388e3c;");

        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #1b5e20; -fx-font-weight: 600;");

        Label timeoutLabel = new Label("(timeout " + TIMEOUT_SECONDS + "s)");
        timeoutLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #81c784;");

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #c8e6c9; -fx-text-fill: #1b5e20; -fx-font-weight: 600; -fx-cursor: hand;");

        VBox root = new VBox(15, spinner, msgLabel, timeoutLabel, cancelBtn);
        root.setStyle("-fx-background-color: #e8f5e9; -fx-padding: 30; -fx-border-color: #81c784; -fx-border-width: 2; -fx-background-radius: 8; -fx-border-radius: 8;");
        root.setAlignment(Pos.CENTER);

        Scene scene = new Scene(root, 260, 210);
        scene.getStylesheets().add(LoadingOverlay.class.getResource("/css/styles.css").toExternalForm());

        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.initOwner(owner);
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);
        stage.setResizable(false);

        cancelBtn.setOnAction(e -> stage.close());

        stage.show();

        Thread timeoutThread = new Thread(() -> {
            try {
                Thread.sleep(TIMEOUT_SECONDS * 1000);
                Platform.runLater(() -> {
                    if (stage.isShowing()) {
                        msgLabel.setText("Request timed out (" + TIMEOUT_SECONDS + "s)");
                        msgLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #d32f2f; -fx-font-weight: 600;");
                        timeoutLabel.setText("");
                        spinner.setVisible(false);
                        cancelBtn.setText("OK");
                        cancelBtn.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: 600; -fx-cursor: hand;");
                    }
                });
            } catch (InterruptedException ignored) {
            }
        }, "loading-timeout");
        timeoutThread.setDaemon(true);
        timeoutThread.start();

        return stage;
    }

    public static void close(Stage stage) {
        if (stage != null && stage.isShowing()) {
            Platform.runLater(stage::close);
        }
    }
}
