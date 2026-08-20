// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.javafx;

import org.jetbrains.annotations.NotNullByDefault;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/// Allocates adaptive pixel storage used by [WebPFXImage].
///
/// Ordinary images use heap storage for efficient JVM access. Large retained pixel sets use
/// direct storage to reduce heap pressure, while regions too large for one direct [ByteBuffer]
/// fall back to heap storage.
@NotNullByDefault
final class WebPFXImageStorage {

    /// Retained byte size at which newly allocated storage moves off heap.
    static final int DIRECT_ALLOCATION_THRESHOLD_BYTES = 64 * 1024 * 1024;

    /// Retained pixel count at which newly allocated storage moves off heap.
    static final int DIRECT_ALLOCATION_THRESHOLD_PIXELS =
            DIRECT_ALLOCATION_THRESHOLD_BYTES / Integer.BYTES;

    /// Maximum byte size targeted for one packed animation allocation.
    static final int MAX_PACKED_CHUNK_BYTES = 128 * 1024 * 1024;

    /// Maximum number of integer pixels representable by one direct byte buffer.
    static final int MAX_DIRECT_PIXEL_COUNT = Integer.MAX_VALUE / Integer.BYTES;

    /// Prevents instantiation.
    private WebPFXImageStorage() {
    }

    /// Allocates adaptive storage for one tightly packed image.
    ///
    /// @param width the positive image width
    /// @param height the positive image height
    /// @return a position-zero buffer containing one element per pixel
    /// @throws IllegalArgumentException if a dimension is not positive or the pixel count exceeds
    ///                                  the capacity of an [IntBuffer]
    static IntBuffer allocate(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException(
                    "Image dimensions must be positive: " + width + "x" + height
            );
        }

        int pixelCount;
        try {
            pixelCount = Math.multiplyExact(width, height);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Image dimensions are too large: " + width + "x" + height, ex);
        }
        return allocatePixels(pixelCount);
    }

    /// Allocates adaptive storage for one pixel region.
    ///
    /// @param pixelCount the non-negative number of integer pixels
    /// @return a position-zero buffer with the requested capacity
    /// @throws IllegalArgumentException if `pixelCount` is negative
    static IntBuffer allocatePixels(int pixelCount) {
        return allocatePixels(pixelCount, prefersDirect(pixelCount, 1));
    }

    /// Allocates pixel storage using the requested location when representable.
    ///
    /// A direct preference falls back to heap storage when its byte size cannot be represented by
    /// one [ByteBuffer].
    ///
    /// @param pixelCount the non-negative number of integer pixels
    /// @param directPreferred whether direct storage is preferred
    /// @return a position-zero buffer with the requested capacity
    /// @throws IllegalArgumentException if `pixelCount` is negative
    static IntBuffer allocatePixels(int pixelCount, boolean directPreferred) {
        if (pixelCount < 0) {
            throw new IllegalArgumentException("pixelCount < 0: " + pixelCount);
        }
        if (directPreferred && pixelCount <= MAX_DIRECT_PIXEL_COUNT) {
            int byteCount = pixelCount * Integer.BYTES;
            return ByteBuffer.allocateDirect(byteCount)
                    .order(ByteOrder.nativeOrder())
                    .asIntBuffer();
        }
        return IntBuffer.allocate(pixelCount);
    }

    /// Allocates equal-sized regions packed into bounded heap or direct chunks.
    ///
    /// The combined retained size selects the storage location. Each chunk contains only complete
    /// regions and is capped at [#MAX_PACKED_CHUNK_BYTES] unless one region alone is larger. A
    /// direct animation may span any number of chunks and therefore may exceed the capacity of one
    /// [ByteBuffer].
    ///
    /// @param regionCount the positive number of regions
    /// @param pixelCount the positive number of integer pixels in each region
    /// @return position-zero region slices in allocation order
    /// @throws IllegalArgumentException if either argument is not positive
    static IntBuffer[] allocateRegions(int regionCount, int pixelCount) {
        if (regionCount <= 0) {
            throw new IllegalArgumentException("regionCount <= 0: " + regionCount);
        }
        if (pixelCount <= 0) {
            throw new IllegalArgumentException("pixelCount <= 0: " + pixelCount);
        }

        boolean directPreferred = prefersDirect(pixelCount, regionCount);
        int maxChunkPixels = MAX_PACKED_CHUNK_BYTES / Integer.BYTES;
        int regionsPerChunk = Math.max(1, maxChunkPixels / pixelCount);
        IntBuffer[] regions = new IntBuffer[regionCount];
        int regionIndex = 0;
        while (regionIndex < regionCount) {
            int chunkRegionCount = Math.min(regionsPerChunk, regionCount - regionIndex);
            int chunkPixelCount;
            try {
                chunkPixelCount = Math.multiplyExact(pixelCount, chunkRegionCount);
            } catch (ArithmeticException ex) {
                throw new IllegalArgumentException("Packed pixel chunk is too large", ex);
            }
            IntBuffer chunk = allocatePixels(chunkPixelCount, directPreferred);
            for (int chunkIndex = 0; chunkIndex < chunkRegionCount; chunkIndex++) {
                regions[regionIndex++] = chunk.slice(chunkIndex * pixelCount, pixelCount);
            }
        }
        return regions;
    }

    /// Returns whether a retained set should use direct storage.
    ///
    /// Direct storage is selected only when the combined size reaches the allocation threshold and
    /// each individual region can be represented by one direct [ByteBuffer].
    ///
    /// @param pixelCount the non-negative number of pixels per region
    /// @param regionCount the positive number of retained regions
    /// @return `true` when direct storage is preferred
    /// @throws IllegalArgumentException if `pixelCount` is negative or `regionCount` is not positive
    static boolean prefersDirect(int pixelCount, int regionCount) {
        if (pixelCount < 0) {
            throw new IllegalArgumentException("pixelCount < 0: " + pixelCount);
        }
        if (regionCount <= 0) {
            throw new IllegalArgumentException("regionCount <= 0: " + regionCount);
        }
        return pixelCount <= MAX_DIRECT_PIXEL_COUNT
                && (long) pixelCount * regionCount >= DIRECT_ALLOCATION_THRESHOLD_PIXELS;
    }
}
