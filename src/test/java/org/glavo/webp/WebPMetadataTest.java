// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp;

import org.jetbrains.annotations.NotNullByDefault;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/// Tests the public metadata ownership and defensive-copy contract.
@NotNullByDefault
final class WebPMetadataTest {

    /// Verifies that construction and access do not expose mutable internal payloads.
    @Test
    void copiesPublicPayloadsAtBothBoundaries() {
        byte[] source = {1, 2, 3};
        WebPMetadata metadata = new WebPMetadata(source, null, null);

        source[0] = 9;
        byte[] returned = Objects.requireNonNull(metadata.getIccProfile());
        assertArrayEquals(new byte[]{1, 2, 3}, returned);

        returned[1] = 8;
        assertArrayEquals(new byte[]{1, 2, 3}, metadata.getIccProfile());
    }
}
