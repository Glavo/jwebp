/*
 * Copyright 2026 Glavo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glavo.webp.javafx;

import javafx.concurrent.Task;
import javafx.scene.input.KeyCode;
import org.glavo.webp.WebPImage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.animation.Timeline;
import org.jetbrains.annotations.UnknownNullability;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/// Simple JavaFX viewer for local WebP files.
///
/// The application is intentionally lightweight and uses the public decoding API directly.
/// Static images are displayed immediately and animated WebP files are played back in an
/// [ImageView] according to the frame durations exposed by [WebPImage].
@NotNullByDefault
public final class WebPViewerApp extends Application {

    private final ImageView imageView = new ImageView();
    private final Label statusLabel = new Label("Open or drop a WebP file to start.");
    private final FileChooser fileChooser = createFileChooser();
    private final StackPane imagePane = new StackPane(imageView);
    private final StackPane loadingOverlay = createLoadingOverlay();

    private @UnknownNullability Stage stage;
    private @Nullable Path currentPath;
    private @Nullable WebPFXImage currentImage;
    private @Nullable ScrollPane scrollPane;
    private @Nullable Task<WebPImage> loadTask;
    private long loadRequestId;
    private @Nullable Point2D dragAnchor;
    private double dragStartHValue;
    private double dragStartVValue;

    /// Launches the viewer application.
    ///
    /// @param args optional command line arguments; the first argument may point to a WebP file
    public static void main(String[] args) {
        launch(args);
    }

    /// Builds and shows the primary viewer window.
    ///
    /// @param primaryStage the primary JavaFX stage supplied by the runtime
    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        Button openButton = new Button("Open");
        openButton.setOnAction(event -> openFileChooser());

        ToolBar toolBar = new ToolBar(openButton, statusLabel);
        ScrollPane scrollPane = new ScrollPane(imagePane);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(false);
        this.scrollPane = scrollPane;
        installDragPanHandlers();

        StackPane contentPane = new StackPane(scrollPane, loadingOverlay);
        BorderPane root = new BorderPane(contentPane);
        root.setTop(toolBar);
        BorderPane.setMargin(contentPane, new Insets(8));

        Scene scene = new Scene(root, 960, 720);
        installFileDropHandlers(scene);
        scene.setOnKeyPressed(event -> {
            if (Objects.requireNonNull(event.getCode()) == KeyCode.O) {
                openFileChooser();
            }
        });

        primaryStage.setTitle("WebP Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();

        List<String> arguments = getParameters().getRaw();
        if (!arguments.isEmpty()) {
            load(Path.of(arguments.get(0)));
        }
    }

    /// Stops any active playback when the application exits.
    @Override
    public void stop() {
        cancelLoadTask();
        stopPlayback();
    }

    private void installFileDropHandlers(Scene scene) {
        scene.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles() && findDroppedWebPFile(event.getDragboard().getFiles()) != null) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        scene.setOnDragDropped(event -> {
            Path path = findDroppedWebPFile(event.getDragboard().getFiles());
            if (path != null) {
                load(path);
                event.setDropCompleted(true);
            } else {
                event.setDropCompleted(false);
            }
            event.consume();
        });
    }

    private void installDragPanHandlers() {
        imagePane.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY || scrollPane == null || imageView.getImage() == null) {
                return;
            }
            if (!canPanImage()) {
                return;
            }

            dragAnchor = new Point2D(event.getSceneX(), event.getSceneY());
            dragStartHValue = scrollPane.getHvalue();
            dragStartVValue = scrollPane.getVvalue();
            imagePane.setStyle("-fx-cursor: closed-hand;");
            event.consume();
        });
        imagePane.setOnMouseDragged(event -> {
            if (dragAnchor == null || scrollPane == null) {
                return;
            }

            double contentWidth = imagePane.getLayoutBounds().getWidth();
            double contentHeight = imagePane.getLayoutBounds().getHeight();
            double viewportWidth = scrollPane.getViewportBounds().getWidth();
            double viewportHeight = scrollPane.getViewportBounds().getHeight();

            double dx = event.getSceneX() - dragAnchor.getX();
            double dy = event.getSceneY() - dragAnchor.getY();

            if (contentWidth > viewportWidth) {
                double delta = dx / (contentWidth - viewportWidth);
                scrollPane.setHvalue(clamp(dragStartHValue - delta));
            }
            if (contentHeight > viewportHeight) {
                double delta = dy / (contentHeight - viewportHeight);
                scrollPane.setVvalue(clamp(dragStartVValue - delta));
            }

            event.consume();
        });
        imagePane.setOnMouseReleased(event -> finishDragPan());
        imagePane.setOnMouseExited(event -> {
            if (!event.isPrimaryButtonDown()) {
                finishDragPan();
            }
        });
    }

    private void openFileChooser() {
        Path initialDirectory = currentDirectory();
        if (initialDirectory != null) {
            fileChooser.setInitialDirectory(initialDirectory.toFile());
        }

        var selectedFile = fileChooser.showOpenDialog(stage);
        if (selectedFile != null) {
            load(selectedFile.toPath());
        }
    }

    private void load(Path path) {
        stopPlayback();
        cancelLoadTask();

        long startNanos = System.nanoTime();
        long requestId = ++loadRequestId;
        Task<WebPImage> task = new Task<>() {
            @Override
            protected WebPImage call() throws Exception {
                return WebPImage.read(path);
            }
        };

        loadTask = task;
        setLoading(true);
        statusLabel.setText("Loading " + path.getFileName() + "...");
        stage.setTitle("WebP Viewer - Loading " + path.getFileName());

        task.setOnSucceeded(event -> {
            if (!isCurrentLoad(requestId, task)) {
                return;
            }

            loadTask = null;
            setLoading(false);
            applyLoadedImage(path, task.getValue(), startNanos);
        });
        task.setOnFailed(event -> {
            if (!isCurrentLoad(requestId, task)) {
                return;
            }

            loadTask = null;
            setLoading(false);
            handleLoadFailure(path, task.getException());
        });
        task.setOnCancelled(event -> {
            if (!isCurrentLoad(requestId, task)) {
                return;
            }

            loadTask = null;
            setLoading(false);
        });

        Thread worker = new Thread(task, "webp-viewer-load-" + requestId);
        worker.setDaemon(true);
        worker.start();
    }

    private void stopPlayback() {
        if (currentImage != null) {
            Timeline animation = currentImage.getAnimation();
            if (animation != null) {
                animation.stop();
            }
            currentImage = null;
        }
        currentPath = null;
    }

    private @Nullable Path currentDirectory() {
        if (currentPath != null) {
            Path parent = currentPath.getParent();
            if (parent != null && Files.isDirectory(parent)) {
                return parent;
            }
        }
        return null;
    }

    private boolean canPanImage() {
        if (scrollPane == null) {
            return false;
        }
        return imagePane.getLayoutBounds().getWidth() > scrollPane.getViewportBounds().getWidth()
                || imagePane.getLayoutBounds().getHeight() > scrollPane.getViewportBounds().getHeight();
    }

    private void finishDragPan() {
        dragAnchor = null;
        imagePane.setStyle("");
    }

    private void applyLoadedImage(Path path, WebPImage image, long startNanos) {
        WebPFXImage fxImage = new WebPFXImage(image);
        long elapsedMillis = elapsedMillis(startNanos);

        currentPath = path;
        currentImage = fxImage;

        imageView.setImage(fxImage);
        imageView.setFitWidth(fxImage.getWidth());
        imageView.setFitHeight(fxImage.getHeight());

        statusLabel.setText(buildStatusText(path, image, elapsedMillis));
        stage.setTitle("WebP Viewer - " + path.getFileName());
    }

    private void handleLoadFailure(Path path, Throwable error) {
        IOException exception = error instanceof IOException ioException
                ? ioException
                : new IOException("Failed to decode WebP image", error);

        currentPath = null;
        currentImage = null;
        imageView.setImage(null);
        imageView.setFitWidth(0);
        imageView.setFitHeight(0);
        statusLabel.setText("Failed to open " + path.getFileName() + ": " + errorMessage(exception));
        stage.setTitle("WebP Viewer");
        showLoadError(path, exception);
    }

    private void cancelLoadTask() {
        Task<WebPImage> task = loadTask;
        loadTask = null;
        if (task != null) {
            task.cancel();
        }
        setLoading(false);
    }

    private boolean isCurrentLoad(long requestId, Task<WebPImage> task) {
        return loadRequestId == requestId && loadTask == task;
    }

    private void setLoading(boolean loading) {
        loadingOverlay.setManaged(loading);
        loadingOverlay.setVisible(loading);
    }

    private void showLoadError(Path path, IOException ex) {
        if (stage == null) {
            return;
        }

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(stage);
        alert.setHeaderText("Failed to open WebP image");
        alert.setContentText(path.getFileName() + ": " + ex.getMessage());
        alert.show();
    }

    private static @Nullable Path findDroppedWebPFile(List<java.io.File> files) {
        for (java.io.File file : files) {
            Path path = file.toPath();
            if (Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".webp")) {
                return path;
            }
        }
        return null;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String errorMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message;
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private String buildStatusText(Path path, WebPImage image, long loadMillis) {
        StringBuilder text = new StringBuilder();
        text.append(path.getFileName())
                .append(" | ")
                .append(image.getSourceWidth())
                .append("x")
                .append(image.getSourceHeight());

        if (image.isAnimated()) {
            text.append(" | frames=").append(image.getFrames().size())
                    .append(" | loop=").append(image.getLoopCount() == 0 ? "forever" : image.getLoopCount())
                    .append(" | duration=").append(image.getLoopDurationMillis()).append("ms");
        } else {
            text.append(" | still");
        }

        if (image.hasAlpha()) {
            text.append(" | alpha");
        }
        if (image.isLossy()) {
            text.append(" | lossy");
        } else {
            text.append(" | lossless");
        }
        text.append(" | load=").append(loadMillis).append("ms");
        return text.toString();
    }

    private static FileChooser createFileChooser() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open WebP Image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("WebP Images", "*.webp"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        return chooser;
    }

    private static StackPane createLoadingOverlay() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(96, 96);

        StackPane overlay = new StackPane(indicator);
        overlay.setManaged(false);
        overlay.setVisible(false);
        overlay.setStyle("-fx-background-color: rgba(255, 255, 255, 0.72);");
        return overlay;
    }

}
