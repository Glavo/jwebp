// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal.lossy;

import org.jetbrains.annotations.NotNullByDefault;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Tests compact VP8 mode representations shared by the lossy decoder.
@NotNullByDefault
final class LossyCommonTest {

    /// Verifies that all sixteen nibble positions retain their assigned modes.
    @SuppressWarnings("MagicConstant")
    @Test
    void packsAndReadsAllSixteenIntraModes() {
        long modes = 0L;
        for (int index = 0; index < LossyCommon.PACKED_INTRA_MODE_COUNT; index++) {
            modes = LossyCommon.setIntraMode(modes, index, index % 10);
        }

        for (int index = 0; index < LossyCommon.PACKED_INTRA_MODE_COUNT; index++) {
            assertEquals(index % 10, LossyCommon.getIntraMode(modes, index));
        }
    }

    /// Verifies the nibble order and replacement behavior of packed modes.
    @Test
    void replacesOneModeWithoutChangingOtherNibbles() {
        long modes = LossyCommon.setIntraMode(0L, 0, LossyCommon.IntraMode.HU);
        modes = LossyCommon.setIntraMode(modes, 15, LossyCommon.IntraMode.VR);

        assertEquals(0x6000000000000009L, modes);

        modes = LossyCommon.setIntraMode(modes, 0, LossyCommon.IntraMode.TM);
        assertEquals(0x6000000000000001L, modes);
    }

    /// Verifies the correspondence between shared macroblock and 4x4 prediction modes.
    @Test
    void convertsSharedLumaModesToIntraModes() {
        assertEquals(LossyCommon.IntraMode.DC, LossyCommon.toIntraMode(LossyCommon.LumaMode.DC));
        assertEquals(LossyCommon.IntraMode.VE, LossyCommon.toIntraMode(LossyCommon.LumaMode.V));
        assertEquals(LossyCommon.IntraMode.HE, LossyCommon.toIntraMode(LossyCommon.LumaMode.H));
        assertEquals(LossyCommon.IntraMode.TM, LossyCommon.toIntraMode(LossyCommon.LumaMode.TM));
        assertThrows(
                IllegalArgumentException.class,
                () -> LossyCommon.toIntraMode(LossyCommon.LumaMode.B)
        );
    }
}
