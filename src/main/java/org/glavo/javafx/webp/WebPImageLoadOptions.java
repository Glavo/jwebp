package org.glavo.javafx.webp;

/**
 * Immutable loading options that mirror the scaling-related parameters of the JavaFX
 * {@link javafx.scene.image.Image} constructors.
 *
 * <p>The options only control the dimensions and filtering used for decoded output. They never
 * alter the source WebP metadata or animation timing.
 */
public final class WebPImageLoadOptions {

    private static final WebPImageLoadOptions DEFAULTS = new Builder().build();

    private final double requestedWidth;
    private final double requestedHeight;
    private final boolean preserveRatio;
    private final boolean smooth;

    private WebPImageLoadOptions(double requestedWidth, double requestedHeight, boolean preserveRatio, boolean smooth) {
        this.requestedWidth = requestedWidth;
        this.requestedHeight = requestedHeight;
        this.preserveRatio = preserveRatio;
        this.smooth = smooth;
    }

    /**
     * Returns the default options.
     *
     * <p>The default configuration requests the intrinsic image size, does not preserve ratio
     * because no explicit bounding box is provided, and enables smooth filtering.
     *
     * @return the shared default options instance
     */
    public static WebPImageLoadOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Creates a new builder.
     *
     * @return a builder initialized with default option values
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns the requested bounding-box width.
     *
     * <p>A value less than or equal to zero means that the intrinsic source width is used.
     *
     * @return the requested width in JavaFX coordinates
     */
    public double getRequestedWidth() {
        return requestedWidth;
    }

    /**
     * Returns the requested bounding-box height.
     *
     * <p>A value less than or equal to zero means that the intrinsic source height is used.
     *
     * @return the requested height in JavaFX coordinates
     */
    public double getRequestedHeight() {
        return requestedHeight;
    }

    /**
     * Returns whether the source aspect ratio should be preserved when fitting into the requested
     * width/height bounding box.
     *
     * @return {@code true} if the source aspect ratio must be preserved
     */
    public boolean isPreserveRatio() {
        return preserveRatio;
    }

    /**
     * Returns whether a higher-quality scaling filter should be used.
     *
     * <p>When scaling is not required this flag has no practical effect.
     *
     * @return {@code true} for higher-quality filtering, {@code false} for faster scaling
     */
    public boolean isSmooth() {
        return smooth;
    }

    /**
     * Builder for {@link WebPImageLoadOptions}.
     */
    public static final class Builder {
        private double requestedWidth;
        private double requestedHeight;
        private boolean preserveRatio;
        private boolean smooth = true;

        private Builder() {
        }

        /**
         * Sets the requested bounding-box width.
         *
         * @param requestedWidth the requested width, or a non-positive value to keep the intrinsic
         *                       width
         * @return this builder
         */
        public Builder requestedWidth(double requestedWidth) {
            this.requestedWidth = requestedWidth;
            return this;
        }

        /**
         * Sets the requested bounding-box height.
         *
         * @param requestedHeight the requested height, or a non-positive value to keep the intrinsic
         *                        height
         * @return this builder
         */
        public Builder requestedHeight(double requestedHeight) {
            this.requestedHeight = requestedHeight;
            return this;
        }

        /**
         * Sets whether the source aspect ratio should be preserved.
         *
         * @param preserveRatio whether scaling should preserve the original aspect ratio
         * @return this builder
         */
        public Builder preserveRatio(boolean preserveRatio) {
            this.preserveRatio = preserveRatio;
            return this;
        }

        /**
         * Sets whether the higher-quality scaling filter should be used.
         *
         * @param smooth {@code true} for higher-quality filtering, {@code false} for faster scaling
         * @return this builder
         */
        public Builder smooth(boolean smooth) {
            this.smooth = smooth;
            return this;
        }

        /**
         * Creates the immutable options instance.
         *
         * @return the configured load options
         */
        public WebPImageLoadOptions build() {
            return new WebPImageLoadOptions(requestedWidth, requestedHeight, preserveRatio, smooth);
        }
    }
}
