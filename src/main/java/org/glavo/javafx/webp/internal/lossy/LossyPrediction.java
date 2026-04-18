package org.glavo.javafx.webp.internal.lossy;

import org.glavo.javafx.webp.internal.lossy.LossyCommon.IntraMode;

/// VP8 intra-prediction helpers.
final class LossyPrediction {

    static final int LUMA_BLOCK_SIZE = (1 + 16 + 4) * (1 + 16);
    static final int LUMA_STRIDE = 1 + 16 + 4;
    static final int CHROMA_BLOCK_SIZE = (8 + 1) * (8 + 1);
    static final int CHROMA_STRIDE = 8 + 1;

    private LossyPrediction() {
    }

    static byte[] createBorderLuma(int mbx, int mby, int mbw, byte[] top, byte[] left) {
        int stride = LUMA_STRIDE;
        byte[] ws = new byte[LUMA_BLOCK_SIZE];

        for (int i = 0; i < 16 + 4; i++) {
            ws[1 + i] = mby == 0 ? (byte) 127 : top[Math.min(mbx * 16 + i, mbx == mbw - 1 ? mbx * 16 + 15 : mbx * 16 + i)];
        }

        for (int i = 17; i < stride; i++) {
            ws[4 * stride + i] = ws[i];
            ws[8 * stride + i] = ws[i];
            ws[12 * stride + i] = ws[i];
        }

        for (int i = 0; i < 16; i++) {
            ws[(i + 1) * stride] = mbx == 0 ? (byte) 129 : left[i + 1];
        }

        ws[0] = (byte) (mby == 0 ? 127 : (mbx == 0 ? 129 : left[0] & 0xFF));
        return ws;
    }

    static byte[] createBorderChroma(int mbx, int mby, byte[] top, byte[] left) {
        int stride = CHROMA_STRIDE;
        byte[] block = new byte[CHROMA_BLOCK_SIZE];

        for (int i = 0; i < 8; i++) {
            block[1 + i] = mby == 0 ? (byte) 127 : top[mbx * 8 + i];
        }
        for (int y = 0; y < 8; y++) {
            block[(y + 1) * stride] = mbx == 0 ? (byte) 129 : left[y + 1];
        }
        block[0] = (byte) (mby == 0 ? 127 : (mbx == 0 ? 129 : left[0] & 0xFF));
        return block;
    }

    static void addResidue(byte[] pblock, int[] rblock, int y0, int x0, int stride) {
        addResidue(pblock, rblock, 0, y0, x0, stride);
    }

    static void addResidue(byte[] pblock, int[] rblock, int blockOffset, int y0, int x0, int stride) {
        int pos = y0 * stride + x0;
        for (int row = 0; row < 4; row++) {
            for (int x = 0; x < 4; x++) {
                int value = (pblock[pos + x] & 0xFF) + rblock[blockOffset + row * 4 + x];
                pblock[pos + x] = (byte) Math.max(0, Math.min(255, value));
            }
            pos += stride;
        }
    }

    static void predict4x4(byte[] ws, int stride, IntraMode[] modes, int[] resdata) {
        for (int sby = 0; sby < 4; sby++) {
            for (int sbx = 0; sbx < 4; sbx++) {
                int i = sbx + sby * 4;
                int y0 = sby * 4 + 1;
                int x0 = sbx * 4 + 1;

                switch (modes[i]) {
                    case TM -> predictTmpred(ws, 4, x0, y0, stride);
                    case VE -> predictBvepred(ws, x0, y0, stride);
                    case HE -> predictBhepred(ws, x0, y0, stride);
                    case DC -> predictBdcpred(ws, x0, y0, stride);
                    case LD -> predictBldpred(ws, x0, y0, stride);
                    case RD -> predictBrdpred(ws, x0, y0, stride);
                    case VR -> predictBvrpred(ws, x0, y0, stride);
                    case VL -> predictBvlpred(ws, x0, y0, stride);
                    case HD -> predictBhdpred(ws, x0, y0, stride);
                    case HU -> predictBhupred(ws, x0, y0, stride);
                }

                addResidue(ws, resdata, i * 16, y0, x0, stride);
            }
        }
    }

    static void predictVpred(byte[] a, int size, int x0, int y0, int stride) {
        for (int y = 0; y < size; y++) {
            System.arraycopy(a, x0, a, (y0 + y) * stride + x0, size);
        }
    }

    static void predictHpred(byte[] a, int size, int x0, int y0, int stride) {
        for (int y = 0; y < size; y++) {
            byte left = a[(y0 + y) * stride + x0 - 1];
            for (int x = 0; x < size; x++) {
                a[(y0 + y) * stride + x0 + x] = left;
            }
        }
    }

    static void predictDcpred(byte[] a, int size, int stride, boolean above, boolean left) {
        int sum = 0;
        int shift = size == 8 ? 2 : 3;
        if (left) {
            for (int y = 0; y < size; y++) {
                sum += a[(y + 1) * stride] & 0xFF;
            }
            shift++;
        }
        if (above) {
            for (int x = 1; x <= size; x++) {
                sum += a[x] & 0xFF;
            }
            shift++;
        }

        int dc = !left && !above ? 128 : (sum + (1 << (shift - 1))) >> shift;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                a[1 + stride * (y + 1) + x] = (byte) dc;
            }
        }
    }

    static void predictTmpred(byte[] a, int size, int x0, int y0, int stride) {
        int p = a[(y0 - 1) * stride + x0 - 1] & 0xFF;
        for (int y = 0; y < size; y++) {
            int leftMinusP = (a[(y0 + y) * stride + x0 - 1] & 0xFF) - p;
            for (int x = 0; x < size; x++) {
                int value = leftMinusP + (a[(y0 - 1) * stride + x0 + x] & 0xFF);
                a[(y0 + y) * stride + x0 + x] = (byte) Math.max(0, Math.min(255, value));
            }
        }
    }

    static void predictBdcpred(byte[] a, int x0, int y0, int stride) {
        int value = 4;
        for (int x = 0; x < 4; x++) {
            value += a[(y0 - 1) * stride + x0 + x] & 0xFF;
        }
        for (int i = 0; i < 4; i++) {
            value += a[(y0 + i) * stride + x0 - 1] & 0xFF;
        }
        value >>= 3;
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                a[(y0 + y) * stride + x0 + x] = (byte) value;
            }
        }
    }

    static void predictBvepred(byte[] a, int x0, int y0, int stride) {
        int p = a[(y0 - 1) * stride + x0 - 1] & 0xFF;
        int[] top = topPixels(a, x0, y0, stride);
        int[] avg = {
                avg3(p, top[0], top[1]),
                avg3(top[0], top[1], top[2]),
                avg3(top[1], top[2], top[3]),
                avg3(top[2], top[3], top[4])
        };
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                a[(y0 + y) * stride + x0 + x] = (byte) avg[x];
            }
        }
    }

    static void predictBhepred(byte[] a, int x0, int y0, int stride) {
        int p = a[(y0 - 1) * stride + x0 - 1] & 0xFF;
        int[] left = leftPixels(a, x0, y0, stride);
        int[] avg = {
                avg3(p, left[0], left[1]),
                avg3(left[0], left[1], left[2]),
                avg3(left[1], left[2], left[3]),
                avg3(left[2], left[3], left[3])
        };
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                a[(y0 + y) * stride + x0 + x] = (byte) avg[y];
            }
        }
    }

    static void predictBldpred(byte[] a, int x0, int y0, int stride) {
        int[] top = topPixels(a, x0, y0, stride);
        int[] avg = {
                avg3(top[0], top[1], top[2]),
                avg3(top[1], top[2], top[3]),
                avg3(top[2], top[3], top[4]),
                avg3(top[3], top[4], top[5]),
                avg3(top[4], top[5], top[6]),
                avg3(top[5], top[6], top[7]),
                avg3(top[6], top[7], top[7])
        };
        for (int y = 0; y < 4; y++) {
            System.arraycopy(new byte[]{(byte) avg[y], (byte) avg[y + 1], (byte) avg[y + 2], (byte) avg[y + 3]}, 0, a, (y0 + y) * stride + x0, 4);
        }
    }

    static void predictBrdpred(byte[] a, int x0, int y0, int stride) {
        int[] e = edgePixels(a, x0, y0, stride);
        int[] avg = {
                avg3(e[0], e[1], e[2]),
                avg3(e[1], e[2], e[3]),
                avg3(e[2], e[3], e[4]),
                avg3(e[3], e[4], e[5]),
                avg3(e[4], e[5], e[6]),
                avg3(e[5], e[6], e[7]),
                avg3(e[6], e[7], e[8])
        };
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                a[(y0 + y) * stride + x0 + x] = (byte) avg[3 - y + x];
            }
        }
    }

    static void predictBvrpred(byte[] a, int x0, int y0, int stride) {
        int[] e = edgePixels(a, x0, y0, stride);
        a[(y0 + 3) * stride + x0] = (byte) avg3(e[1], e[2], e[3]);
        a[(y0 + 2) * stride + x0] = (byte) avg3(e[2], e[3], e[4]);
        a[(y0 + 3) * stride + x0 + 1] = (byte) avg3(e[3], e[4], e[5]);
        a[(y0 + 1) * stride + x0] = (byte) avg3(e[3], e[4], e[5]);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg2(e[4], e[5]);
        a[y0 * stride + x0] = (byte) avg2(e[4], e[5]);
        a[(y0 + 3) * stride + x0 + 2] = (byte) avg3(e[4], e[5], e[6]);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(e[4], e[5], e[6]);
        a[(y0 + 2) * stride + x0 + 2] = (byte) avg2(e[5], e[6]);
        a[y0 * stride + x0 + 1] = (byte) avg2(e[5], e[6]);
        a[(y0 + 3) * stride + x0 + 3] = (byte) avg3(e[5], e[6], e[7]);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg3(e[5], e[6], e[7]);
        a[(y0 + 2) * stride + x0 + 3] = (byte) avg2(e[6], e[7]);
        a[y0 * stride + x0 + 2] = (byte) avg2(e[6], e[7]);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(e[6], e[7], e[8]);
        a[y0 * stride + x0 + 3] = (byte) avg2(e[7], e[8]);
    }

    static void predictBvlpred(byte[] a, int x0, int y0, int stride) {
        int[] top = topPixels(a, x0, y0, stride);
        a[y0 * stride + x0] = (byte) avg2(top[0], top[1]);
        a[(y0 + 1) * stride + x0] = (byte) avg3(top[0], top[1], top[2]);
        a[(y0 + 2) * stride + x0] = (byte) avg2(top[1], top[2]);
        a[y0 * stride + x0 + 1] = (byte) avg2(top[1], top[2]);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(top[1], top[2], top[3]);
        a[(y0 + 3) * stride + x0] = (byte) avg3(top[1], top[2], top[3]);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg2(top[2], top[3]);
        a[y0 * stride + x0 + 2] = (byte) avg2(top[2], top[3]);
        a[(y0 + 3) * stride + x0 + 1] = (byte) avg3(top[2], top[3], top[4]);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg3(top[2], top[3], top[4]);
        a[(y0 + 2) * stride + x0 + 2] = (byte) avg2(top[3], top[4]);
        a[y0 * stride + x0 + 3] = (byte) avg2(top[3], top[4]);
        a[(y0 + 3) * stride + x0 + 2] = (byte) avg3(top[3], top[4], top[5]);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(top[3], top[4], top[5]);
        a[(y0 + 2) * stride + x0 + 3] = (byte) avg3(top[4], top[5], top[6]);
        a[(y0 + 3) * stride + x0 + 3] = (byte) avg3(top[5], top[6], top[7]);
    }

    static void predictBhdpred(byte[] a, int x0, int y0, int stride) {
        int[] e = edgePixels(a, x0, y0, stride);
        a[(y0 + 3) * stride + x0] = (byte) avg2(e[0], e[1]);
        a[(y0 + 3) * stride + x0 + 1] = (byte) avg3(e[0], e[1], e[2]);
        a[(y0 + 2) * stride + x0] = (byte) avg2(e[1], e[2]);
        a[(y0 + 3) * stride + x0 + 2] = (byte) avg2(e[1], e[2]);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg3(e[1], e[2], e[3]);
        a[(y0 + 3) * stride + x0 + 3] = (byte) avg3(e[1], e[2], e[3]);
        a[(y0 + 2) * stride + x0 + 2] = (byte) avg2(e[2], e[3]);
        a[(y0 + 1) * stride + x0] = (byte) avg2(e[2], e[3]);
        a[(y0 + 2) * stride + x0 + 3] = (byte) avg3(e[2], e[3], e[4]);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(e[2], e[3], e[4]);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg2(e[3], e[4]);
        a[y0 * stride + x0] = (byte) avg2(e[3], e[4]);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(e[3], e[4], e[5]);
        a[y0 * stride + x0 + 1] = (byte) avg3(e[3], e[4], e[5]);
        a[y0 * stride + x0 + 2] = (byte) avg3(e[4], e[5], e[6]);
        a[y0 * stride + x0 + 3] = (byte) avg3(e[5], e[6], e[7]);
    }

    static void predictBhupred(byte[] a, int x0, int y0, int stride) {
        int[] left = leftPixels(a, x0, y0, stride);
        a[y0 * stride + x0] = (byte) avg2(left[0], left[1]);
        a[y0 * stride + x0 + 1] = (byte) avg3(left[0], left[1], left[2]);
        a[y0 * stride + x0 + 2] = (byte) avg2(left[1], left[2]);
        a[(y0 + 1) * stride + x0] = (byte) avg2(left[1], left[2]);
        a[y0 * stride + x0 + 3] = (byte) avg3(left[1], left[2], left[3]);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(left[1], left[2], left[3]);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg2(left[2], left[3]);
        a[(y0 + 2) * stride + x0] = (byte) avg2(left[2], left[3]);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(left[2], left[3], left[3]);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg3(left[2], left[3], left[3]);
        a[(y0 + 2) * stride + x0 + 2] = (byte) left[3];
        a[(y0 + 2) * stride + x0 + 3] = (byte) left[3];
        a[(y0 + 3) * stride + x0] = (byte) left[3];
        a[(y0 + 3) * stride + x0 + 1] = (byte) left[3];
        a[(y0 + 3) * stride + x0 + 2] = (byte) left[3];
        a[(y0 + 3) * stride + x0 + 3] = (byte) left[3];
    }

    static int avg3(int left, int center, int right) {
        return (left + 2 * center + right + 2) >> 2;
    }

    static int avg2(int left, int right) {
        return (left + right + 1) >> 1;
    }

    static int[] topPixels(byte[] a, int x0, int y0, int stride) {
        int pos = (y0 - 1) * stride + x0;
        return new int[]{
                a[pos] & 0xFF,
                a[pos + 1] & 0xFF,
                a[pos + 2] & 0xFF,
                a[pos + 3] & 0xFF,
                a[pos + 4] & 0xFF,
                a[pos + 5] & 0xFF,
                a[pos + 6] & 0xFF,
                a[pos + 7] & 0xFF
        };
    }

    private static int[] leftPixels(byte[] a, int x0, int y0, int stride) {
        return new int[]{
                a[y0 * stride + x0 - 1] & 0xFF,
                a[(y0 + 1) * stride + x0 - 1] & 0xFF,
                a[(y0 + 2) * stride + x0 - 1] & 0xFF,
                a[(y0 + 3) * stride + x0 - 1] & 0xFF
        };
    }

    static int[] edgePixels(byte[] a, int x0, int y0, int stride) {
        int pos = (y0 - 1) * stride + x0 - 1;
        return new int[]{
                a[pos + 4 * stride] & 0xFF,
                a[pos + 3 * stride] & 0xFF,
                a[pos + 2 * stride] & 0xFF,
                a[pos + stride] & 0xFF,
                a[pos] & 0xFF,
                a[pos + 1] & 0xFF,
                a[pos + 2] & 0xFF,
                a[pos + 3] & 0xFF,
                a[pos + 4] & 0xFF
        };
    }
}
