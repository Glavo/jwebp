package org.glavo.javafx.webp.internal.codec;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/// Port of the ignored exhaustive test in [alpha_blending.rs](https://github.com/image-rs/image-webp/blob/f4d80bd965df2c81e65b6f43c1f70e0750bd4b0f/src/alpha_blending.rs).
final class AlphaBlendingTest {

    @Test
    @Disabled("Exhaustive optimization check mirrored from upstream #[ignore] test")
    void alphaBlendingOptimization() {
        for (int r1 = 0; r1 < 255; r1++) {
            for (int a1 = 11; a1 < 255; a1++) {
                for (int r2 = 0; r2 < 255; r2++) {
                    for (int a2 = 11; a2 < 255; a2++) {
                        byte[] optimized = AlphaBlending.blend(
                                new byte[]{(byte) r1, 0, 0, (byte) a1},
                                new byte[]{(byte) r2, 0, 0, (byte) a2}
                        );
                        byte[] reference = referenceBlend(
                                new byte[]{(byte) r1, 0, 0, (byte) a1},
                                new byte[]{(byte) r2, 0, 0, (byte) a2}
                        );

                        for (int i = 0; i < 4; i++) {
                            int delta = Math.abs((optimized[i] & 0xFF) - (reference[i] & 0xFF));
                            assertTrue(delta <= 3,
                                    "Mismatch in results. optimized="
                                            + describe(optimized)
                                            + ", reference="
                                            + describe(reference)
                                            + ", blended values=["
                                            + r1 + ", 0, 0, " + a1 + "], ["
                                            + r2 + ", 0, 0, " + a2 + ']');
                        }
                    }
                }
            }
        }
    }

    private static byte[] referenceBlend(byte[] buffer, byte[] canvas) {
        double canvasAlpha = canvas[3] & 0xFF;
        double bufferAlpha = buffer[3] & 0xFF;
        double blendAlphaValue = bufferAlpha + canvasAlpha * (1.0 - bufferAlpha / 255.0);
        int blendAlpha = (int) blendAlphaValue;

        int r = 0;
        int g = 0;
        int b = 0;
        if (blendAlpha != 0) {
            r = (int) (((buffer[0] & 0xFF) * bufferAlpha
                    + (canvas[0] & 0xFF) * canvasAlpha * (1.0 - bufferAlpha / 255.0))
                    / blendAlphaValue);
            g = (int) (((buffer[1] & 0xFF) * bufferAlpha
                    + (canvas[1] & 0xFF) * canvasAlpha * (1.0 - bufferAlpha / 255.0))
                    / blendAlphaValue);
            b = (int) (((buffer[2] & 0xFF) * bufferAlpha
                    + (canvas[2] & 0xFF) * canvasAlpha * (1.0 - bufferAlpha / 255.0))
                    / blendAlphaValue);
        }

        return new byte[]{(byte) r, (byte) g, (byte) b, (byte) blendAlpha};
    }

    private static String describe(byte[] rgba) {
        return "[" + (rgba[0] & 0xFF) + ", " + (rgba[1] & 0xFF) + ", " + (rgba[2] & 0xFF) + ", " + (rgba[3] & 0xFF) + "]";
    }
}
