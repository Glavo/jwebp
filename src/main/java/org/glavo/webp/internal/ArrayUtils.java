// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

/// Provides shared zero-length primitive arrays for internal decoder state.
///
/// Zero-length arrays have no mutable elements, so independent consumers may safely use the
/// same instances as empty values.
@NotNullByDefault
public final class ArrayUtils {

    /// Shared zero-length byte array.
    public static final byte @Unmodifiable [] EMPTY_BYTE_ARRAY = new byte[0];

    /// Shared zero-length int array.
    public static final int @Unmodifiable [] EMPTY_INT_ARRAY = new int[0];

    /// Prevents instantiation.
    private ArrayUtils() {
    }
}
