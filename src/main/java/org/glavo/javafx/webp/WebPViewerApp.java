package org.glavo.javafx.webp;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToolBar;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Simple JavaFX viewer for local WebP files.
///
/// The application is intentionally lightweight and uses the public decoder API directly.
/// Static images are displayed immediately and animated WebP files are played back in an
/// [ImageView] according to the frame durations exposed by [WebPDecoder].
public final class WebPViewerApp extends Application {

    private final ImageView imageView = new ImageView();
    private final Label statusLabel = new Label("Open a WebP file to start.");
    private final FileChooser fileChooser = createFileChooser();

    private Stage stage;
    private Playback playback;

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
        ScrollPane scrollPane = new ScrollPane(new StackPane(imageView));
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setPannable(true);

        BorderPane root = new BorderPane(scrollPane);
        root.setTop(toolBar);
        BorderPane.setMargin(scrollPane, new Insets(8));

        Scene scene = new Scene(root, 960, 720);
        scene.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case O -> openFileChooser();
                default -> {
                }
            }
        });

        primaryStage.setTitle("WebP Viewer");
        primaryStage.setScene(scene);
        primaryStage.show();

        List<String> arguments = getParameters().getRaw();
        if (!arguments.isEmpty()) {
            load(Path.of(arguments.get(0)));
        } else {
            Platform.runLater(this::openFileChooser);
        }
    }

    /// Stops any active playback when the application exits.
    @Override
    public void stop() {
        stopPlayback();
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

        try {
            WebPImage webpImage = WebPDecoder.decodeAll(path);
            List<Image> frameImages = new ArrayList<>(webpImage.getFrames().size());
            for (WebPFrame frame : webpImage.getFrames()) {
                frameImages.add(frame.toWritableImage());
            }

            playback = new Playback(path, webpImage, frameImages);
            playback.showFirstFrame();

            statusLabel.setText(buildStatusText(playback));
            stage.setTitle("WebP Viewer - " + path.getFileName());
        } catch (IOException ex) {
            imageView.setImage(null);
            statusLabel.setText("Failed to open " + path.getFileName() + ": " + ex.getMessage());
            stage.setTitle("WebP Viewer");
        }
    }

    private void stopPlayback() {
        if (playback != null) {
            playback.stop();
            playback = null;
        }
    }

    private Path currentDirectory() {
        if (playback != null) {
            Path parent = playback.path().getParent();
            if (parent != null && Files.isDirectory(parent)) {
                return parent;
            }
        }
        return null;
    }

    private String buildStatusText(Playback playback) {
        WebPImage image = playback.image();
        StringBuilder text = new StringBuilder();
        text.append(playback.path().getFileName())
                .append(" | ")
                .append(image.getSourceWidth())
                .append("x")
                .append(image.getSourceHeight());

        if (image.isAnimated()) {
            text.append(" | frames=").append(image.getFrames().size())
                    .append(" | loop=").append(image.getLoopCount().isForever() ? "forever" : image.getLoopCount().getRepetitions())
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

    /// Holds decoded playback state for one loaded file.
    ///
    /// The helper keeps pre-converted JavaFX [Image] instances and advances the
    /// presentation with a self-rescheduling [PauseTransition] so that frame-specific
    /// durations are honored without forcing a fixed-timestep animation loop.
    private final class Playback {
        private final Path path;
        private final WebPImage image;
        private final List<Image> frameImages;
        private final PauseTransition pauseTransition = new PauseTransition();
        private int frameIndex;

        private Playback(Path path, WebPImage image, List<Image> frameImages) {
            this.path = path;
            this.image = image;
            this.frameImages = List.copyOf(frameImages);
            pauseTransition.setOnFinished(event -> advance());
        }

        private Path path() {
            return path;
        }

        private WebPImage image() {
            return image;
        }

        private void showFirstFrame() {
            frameIndex = 0;
            imageView.setImage(frameImages.get(0));
            imageView.setFitWidth(frameImages.get(0).getWidth());
            imageView.setFitHeight(frameImages.get(0).getHeight());
            scheduleNextFrame();
        }

        private void advance() {
            if (!image.isAnimated() || frameImages.size() <= 1) {
                return;
            }

            frameIndex = (frameIndex + 1) % frameImages.size();
            imageView.setImage(frameImages.get(frameIndex));
            scheduleNextFrame();
        }

        /// Arms playback for the current frame.
        ///
        /// WebP permits zero-duration frames. To keep such files viewable in JavaFX, the viewer
        /// normalizes non-positive durations to one display pulse instead of immediately recursing.
        private void scheduleNextFrame() {
            if (!image.isAnimated() || frameImages.size() <= 1) {
                return;
            }

            int durationMillis = image.getFrames().get(frameIndex).getDurationMillis();
            pauseTransition.stop();
            pauseTransition.setDuration(Duration.millis(Math.max(1, durationMillis)));
            pauseTransition.playFromStart();
        }

        private void stop() {
            pauseTransition.stop();
        }
    }
}
