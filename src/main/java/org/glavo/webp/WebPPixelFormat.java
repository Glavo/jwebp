// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp;

import org.jetbrains.annotations.NotNullByDefault;

/// Describes the packed integer representation used by decoded frame pixels.
///
/// Both formats store one pixel per `int` as `0xAARRGGBB`. They differ only in whether the color
/// channels have been multiplied by the alpha channel.
@NotNullByDefault
public enum WebPPixelFormat {

    /// Stores non-premultiplied red, green, and blue channels.
    INT_ARGB(false),

    /// Stores red, green, and blue channels premultiplied by alpha.
    ///
    /// Converting to this format may discard color information from translucent pixels, especially
    /// pixels whose alpha channel is zero.
    INT_ARGB_PRE(true);

    /// Whether this format uses premultiplied color channels.
    private final boolean premultiplied;

    /// Creates a pixel-format constant.
    ///
    /// @param premultiplied whether the color channels are premultiplied by alpha
    WebPPixelFormat(boolean premultiplied) {
        this.premultiplied = premultiplied;
    }

    /// Returns whether the color channels are premultiplied by alpha.
    ///
    /// @return `true` for a premultiplied format
    public boolean isPremultiplied() {
        return premultiplied;
    }
}
