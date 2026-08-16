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
