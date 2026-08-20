// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/// Raw metadata chunks extracted from a WebP container.
///
/// The WebP specification stores ICC, EXIF and XMP payloads as opaque byte arrays. This class
/// exposes those payloads without attempting to interpret them, leaving higher-level processing to
/// the caller.
@NotNullByDefault
public final class WebPMetadata {

    /// Shared metadata container with no payloads.
    private static final WebPMetadata EMPTY = new WebPMetadata(null, null, null);

    /// Immutable ICC profile payload, or `null` when absent.
    private final byte @Nullable @Unmodifiable [] iccProfile;

    /// Immutable EXIF payload, or `null` when absent.
    private final byte @Nullable @Unmodifiable [] exifMetadata;

    /// Immutable XMP payload, or `null` when absent.
    private final byte @Nullable @Unmodifiable [] xmpMetadata;

    /// Returns an empty metadata container.
    ///
    /// @return a metadata object with no payloads
    public static WebPMetadata empty() {
        return EMPTY;
    }

    /// Creates a metadata container from raw chunk payloads.
    ///
    /// Each non-null payload is copied; subsequent changes to the supplied arrays do not affect
    /// this container.
    ///
    /// @param iccProfile the ICC payload, or `null`
    /// @param exifMetadata the EXIF payload, or `null`
    /// @param xmpMetadata the XMP payload, or `null`
    public WebPMetadata(byte @Nullable [] iccProfile, byte @Nullable [] exifMetadata, byte @Nullable [] xmpMetadata) {
        this(iccProfile, exifMetadata, xmpMetadata, true);
    }

    /// Creates a metadata container with selectable defensive copying.
    ///
    /// @param iccProfile the ICC payload, or `null`
    /// @param exifMetadata the EXIF payload, or `null`
    /// @param xmpMetadata the XMP payload, or `null`
    /// @param copy whether to copy non-null payload arrays
    private WebPMetadata(
            byte @Nullable [] iccProfile,
            byte @Nullable [] exifMetadata,
            byte @Nullable [] xmpMetadata,
            boolean copy
    ) {
        this.iccProfile = copy ? copyOrNull(iccProfile) : iccProfile;
        this.exifMetadata = copy ? copyOrNull(exifMetadata) : exifMetadata;
        this.xmpMetadata = copy ? copyOrNull(xmpMetadata) : xmpMetadata;
    }

    /// Creates metadata by taking ownership of parser-exclusive payload arrays.
    ///
    /// The caller must not retain or modify any non-null array after this call.
    ///
    /// @param iccProfile the exclusively owned ICC payload, or `null`
    /// @param exifMetadata the exclusively owned EXIF payload, or `null`
    /// @param xmpMetadata the exclusively owned XMP payload, or `null`
    /// @return an immutable metadata container
    static WebPMetadata fromOwnedPayloads(
            byte @Nullable [] iccProfile,
            byte @Nullable [] exifMetadata,
            byte @Nullable [] xmpMetadata
    ) {
        if (iccProfile == null && exifMetadata == null && xmpMetadata == null) {
            return EMPTY;
        }
        return new WebPMetadata(iccProfile, exifMetadata, xmpMetadata, false);
    }

    /// Returns the ICC profile chunk payload.
    ///
    /// @return a defensive copy of the ICC profile bytes, or `null` if absent
    public byte @Nullable [] getIccProfile() {
        return copyOrNull(iccProfile);
    }

    /// Returns the EXIF metadata chunk payload.
    ///
    /// @return a defensive copy of the EXIF bytes, or `null` if absent
    public byte @Nullable [] getExifMetadata() {
        return copyOrNull(exifMetadata);
    }

    /// Returns the XMP metadata chunk payload.
    ///
    /// @return a defensive copy of the XMP bytes, or `null` if absent
    public byte @Nullable [] getXmpMetadata() {
        return copyOrNull(xmpMetadata);
    }

    /// Returns a defensive copy of an optional byte array.
    ///
    /// @param value the source array, or `null`
    /// @return the copied array, or `null`
    private static byte @Nullable [] copyOrNull(byte @Nullable [] value) {
        return value == null ? null : value.clone();
    }

    @Override
    public String toString() {
        return "WebPMetadata[icc=" + lengthOf(iccProfile)
                + ", exif=" + lengthOf(exifMetadata)
                + ", xmp=" + lengthOf(xmpMetadata) + "]";
    }

    /// Returns the length of an optional byte array.
    ///
    /// @param value the array, or `null`
    /// @return the array length, or zero when absent
    private static int lengthOf(byte @Nullable [] value) {
        return value == null ? 0 : value.length;
    }
}
