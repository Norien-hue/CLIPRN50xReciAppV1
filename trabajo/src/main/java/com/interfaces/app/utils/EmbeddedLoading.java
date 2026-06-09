package com.interfaces.app.utils;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class EmbeddedLoading {

    public static Pane show(Pane parent, String message) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(40, 40);
        spinner.setStyle("-fx-progress-color: #388e3c;");

        Label msgLabel = new Label(message);
        msgLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1b5e20; -fx-font-weight: 600;");

        VBox content = new VBox(12, spinner, msgLabel);
        content.setAlignment(Pos.CENTER);

        StackPane overlay = new StackPane(content);
        overlay.setStyle("-fx-background-color: rgba(232, 245, 233, 0.88);");
        overlay.prefWidthProperty().bind(parent.widthProperty());
        overlay.prefHeightProperty().bind(parent.heightProperty());

        parent.getChildren().add(overlay);
        overlay.toFront();

        return overlay;
    }

    public static void hide(Pane overlay) {
        if (overlay == null) return;
        Pane parent = (Pane) overlay.getParent();
        if (parent != null) {
            Platform.runLater(() -> parent.getChildren().remove(overlay));
        }
    }
}
