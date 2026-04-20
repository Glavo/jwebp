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
package org.glavo.webp.internal.lossy;

import org.jetbrains.annotations.NotNullByDefault;

import org.glavo.webp.internal.lossy.LossyCommon.IntraMode;

/// VP8 intra-prediction helpers.
@NotNullByDefault
final class LossyPrediction {

    static final int LUMA_BLOCK_SIZE = (1 + 16 + 4) * (1 + 16);
    static final int LUMA_STRIDE = 1 + 16 + 4;
    static final int CHROMA_BLOCK_SIZE = (8 + 1) * (8 + 1);
    static final int CHROMA_STRIDE = 8 + 1;

    private LossyPrediction() {
    }

    static void fillBorderLuma(byte[] ws, int mbx, int mby, int mbw, byte[] top, byte[] left) {
        int stride = LUMA_STRIDE;

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
    }

    static void fillBorderChroma(byte[] block, int mbx, int mby, byte[] top, byte[] left) {
        int stride = CHROMA_STRIDE;

        for (int i = 0; i < 8; i++) {
            block[1 + i] = mby == 0 ? (byte) 127 : top[mbx * 8 + i];
        }
        for (int y = 0; y < 8; y++) {
            block[(y + 1) * stride] = mbx == 0 ? (byte) 129 : left[y + 1];
        }
        block[0] = (byte) (mby == 0 ? 127 : (mbx == 0 ? 129 : left[0] & 0xFF));
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
        int pos = (y0 - 1) * stride + x0;
        int top0 = a[pos] & 0xFF;
        int top1 = a[pos + 1] & 0xFF;
        int top2 = a[pos + 2] & 0xFF;
        int top3 = a[pos + 3] & 0xFF;
        int top4 = a[pos + 4] & 0xFF;
        int[] avg = {
                avg3(p, top0, top1),
                avg3(top0, top1, top2),
                avg3(top1, top2, top3),
                avg3(top2, top3, top4)
        };
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                a[(y0 + y) * stride + x0 + x] = (byte) avg[x];
            }
        }
    }

    static void predictBhepred(byte[] a, int x0, int y0, int stride) {
        int p = a[(y0 - 1) * stride + x0 - 1] & 0xFF;
        int left0 = a[y0 * stride + x0 - 1] & 0xFF;
        int left1 = a[(y0 + 1) * stride + x0 - 1] & 0xFF;
        int left2 = a[(y0 + 2) * stride + x0 - 1] & 0xFF;
        int left3 = a[(y0 + 3) * stride + x0 - 1] & 0xFF;
        int[] avg = {
                avg3(p, left0, left1),
                avg3(left0, left1, left2),
                avg3(left1, left2, left3),
                avg3(left2, left3, left3)
        };
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                a[(y0 + y) * stride + x0 + x] = (byte) avg[y];
            }
        }
    }

    static void predictBldpred(byte[] a, int x0, int y0, int stride) {
        int pos = (y0 - 1) * stride + x0;
        int top0 = a[pos] & 0xFF;
        int top1 = a[pos + 1] & 0xFF;
        int top2 = a[pos + 2] & 0xFF;
        int top3 = a[pos + 3] & 0xFF;
        int top4 = a[pos + 4] & 0xFF;
        int top5 = a[pos + 5] & 0xFF;
        int top6 = a[pos + 6] & 0xFF;
        int top7 = a[pos + 7] & 0xFF;
        int[] avg = {
                avg3(top0, top1, top2),
                avg3(top1, top2, top3),
                avg3(top2, top3, top4),
                avg3(top3, top4, top5),
                avg3(top4, top5, top6),
                avg3(top5, top6, top7),
                avg3(top6, top7, top7)
        };
        for (int y = 0; y < 4; y++) {
            int rowOffset = (y0 + y) * stride + x0;
            a[rowOffset] = (byte) avg[y];
            a[rowOffset + 1] = (byte) avg[y + 1];
            a[rowOffset + 2] = (byte) avg[y + 2];
            a[rowOffset + 3] = (byte) avg[y + 3];
        }
    }

    static void predictBrdpred(byte[] a, int x0, int y0, int stride) {
        int pos = (y0 - 1) * stride + x0 - 1;
        int e0 = a[pos + 4 * stride] & 0xFF;
        int e1 = a[pos + 3 * stride] & 0xFF;
        int e2 = a[pos + 2 * stride] & 0xFF;
        int e3 = a[pos + stride] & 0xFF;
        int e4 = a[pos] & 0xFF;
        int e5 = a[pos + 1] & 0xFF;
        int e6 = a[pos + 2] & 0xFF;
        int e7 = a[pos + 3] & 0xFF;
        int e8 = a[pos + 4] & 0xFF;
        int[] avg = {
                avg3(e0, e1, e2),
                avg3(e1, e2, e3),
                avg3(e2, e3, e4),
                avg3(e3, e4, e5),
                avg3(e4, e5, e6),
                avg3(e5, e6, e7),
                avg3(e6, e7, e8)
        };
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 4; x++) {
                a[(y0 + y) * stride + x0 + x] = (byte) avg[3 - y + x];
            }
        }
    }

    static void predictBvrpred(byte[] a, int x0, int y0, int stride) {
        int pos = (y0 - 1) * stride + x0 - 1;
        int e1 = a[pos + 3 * stride] & 0xFF;
        int e2 = a[pos + 2 * stride] & 0xFF;
        int e3 = a[pos + stride] & 0xFF;
        int e4 = a[pos] & 0xFF;
        int e5 = a[pos + 1] & 0xFF;
        int e6 = a[pos + 2] & 0xFF;
        int e7 = a[pos + 3] & 0xFF;
        int e8 = a[pos + 4] & 0xFF;
        a[(y0 + 3) * stride + x0] = (byte) avg3(e1, e2, e3);
        a[(y0 + 2) * stride + x0] = (byte) avg3(e2, e3, e4);
        a[(y0 + 3) * stride + x0 + 1] = (byte) avg3(e3, e4, e5);
        a[(y0 + 1) * stride + x0] = (byte) avg3(e3, e4, e5);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg2(e4, e5);
        a[y0 * stride + x0] = (byte) avg2(e4, e5);
        a[(y0 + 3) * stride + x0 + 2] = (byte) avg3(e4, e5, e6);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(e4, e5, e6);
        a[(y0 + 2) * stride + x0 + 2] = (byte) avg2(e5, e6);
        a[y0 * stride + x0 + 1] = (byte) avg2(e5, e6);
        a[(y0 + 3) * stride + x0 + 3] = (byte) avg3(e5, e6, e7);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg3(e5, e6, e7);
        a[(y0 + 2) * stride + x0 + 3] = (byte) avg2(e6, e7);
        a[y0 * stride + x0 + 2] = (byte) avg2(e6, e7);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(e6, e7, e8);
        a[y0 * stride + x0 + 3] = (byte) avg2(e7, e8);
    }

    static void predictBvlpred(byte[] a, int x0, int y0, int stride) {
        int pos = (y0 - 1) * stride + x0;
        int top0 = a[pos] & 0xFF;
        int top1 = a[pos + 1] & 0xFF;
        int top2 = a[pos + 2] & 0xFF;
        int top3 = a[pos + 3] & 0xFF;
        int top4 = a[pos + 4] & 0xFF;
        int top5 = a[pos + 5] & 0xFF;
        int top6 = a[pos + 6] & 0xFF;
        int top7 = a[pos + 7] & 0xFF;
        a[y0 * stride + x0] = (byte) avg2(top0, top1);
        a[(y0 + 1) * stride + x0] = (byte) avg3(top0, top1, top2);
        a[(y0 + 2) * stride + x0] = (byte) avg2(top1, top2);
        a[y0 * stride + x0 + 1] = (byte) avg2(top1, top2);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(top1, top2, top3);
        a[(y0 + 3) * stride + x0] = (byte) avg3(top1, top2, top3);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg2(top2, top3);
        a[y0 * stride + x0 + 2] = (byte) avg2(top2, top3);
        a[(y0 + 3) * stride + x0 + 1] = (byte) avg3(top2, top3, top4);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg3(top2, top3, top4);
        a[(y0 + 2) * stride + x0 + 2] = (byte) avg2(top3, top4);
        a[y0 * stride + x0 + 3] = (byte) avg2(top3, top4);
        a[(y0 + 3) * stride + x0 + 2] = (byte) avg3(top3, top4, top5);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(top3, top4, top5);
        a[(y0 + 2) * stride + x0 + 3] = (byte) avg3(top4, top5, top6);
        a[(y0 + 3) * stride + x0 + 3] = (byte) avg3(top5, top6, top7);
    }

    static void predictBhdpred(byte[] a, int x0, int y0, int stride) {
        int pos = (y0 - 1) * stride + x0 - 1;
        int e0 = a[pos + 4 * stride] & 0xFF;
        int e1 = a[pos + 3 * stride] & 0xFF;
        int e2 = a[pos + 2 * stride] & 0xFF;
        int e3 = a[pos + stride] & 0xFF;
        int e4 = a[pos] & 0xFF;
        int e5 = a[pos + 1] & 0xFF;
        int e6 = a[pos + 2] & 0xFF;
        int e7 = a[pos + 3] & 0xFF;
        a[(y0 + 3) * stride + x0] = (byte) avg2(e0, e1);
        a[(y0 + 3) * stride + x0 + 1] = (byte) avg3(e0, e1, e2);
        a[(y0 + 2) * stride + x0] = (byte) avg2(e1, e2);
        a[(y0 + 3) * stride + x0 + 2] = (byte) avg2(e1, e2);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg3(e1, e2, e3);
        a[(y0 + 3) * stride + x0 + 3] = (byte) avg3(e1, e2, e3);
        a[(y0 + 2) * stride + x0 + 2] = (byte) avg2(e2, e3);
        a[(y0 + 1) * stride + x0] = (byte) avg2(e2, e3);
        a[(y0 + 2) * stride + x0 + 3] = (byte) avg3(e2, e3, e4);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(e2, e3, e4);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg2(e3, e4);
        a[y0 * stride + x0] = (byte) avg2(e3, e4);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(e3, e4, e5);
        a[y0 * stride + x0 + 1] = (byte) avg3(e3, e4, e5);
        a[y0 * stride + x0 + 2] = (byte) avg3(e4, e5, e6);
        a[y0 * stride + x0 + 3] = (byte) avg3(e5, e6, e7);
    }

    static void predictBhupred(byte[] a, int x0, int y0, int stride) {
        int left0 = a[y0 * stride + x0 - 1] & 0xFF;
        int left1 = a[(y0 + 1) * stride + x0 - 1] & 0xFF;
        int left2 = a[(y0 + 2) * stride + x0 - 1] & 0xFF;
        int left3 = a[(y0 + 3) * stride + x0 - 1] & 0xFF;
        a[y0 * stride + x0] = (byte) avg2(left0, left1);
        a[y0 * stride + x0 + 1] = (byte) avg3(left0, left1, left2);
        a[y0 * stride + x0 + 2] = (byte) avg2(left1, left2);
        a[(y0 + 1) * stride + x0] = (byte) avg2(left1, left2);
        a[y0 * stride + x0 + 3] = (byte) avg3(left1, left2, left3);
        a[(y0 + 1) * stride + x0 + 1] = (byte) avg3(left1, left2, left3);
        a[(y0 + 1) * stride + x0 + 2] = (byte) avg2(left2, left3);
        a[(y0 + 2) * stride + x0] = (byte) avg2(left2, left3);
        a[(y0 + 1) * stride + x0 + 3] = (byte) avg3(left2, left3, left3);
        a[(y0 + 2) * stride + x0 + 1] = (byte) avg3(left2, left3, left3);
        a[(y0 + 2) * stride + x0 + 2] = (byte) left3;
        a[(y0 + 2) * stride + x0 + 3] = (byte) left3;
        a[(y0 + 3) * stride + x0] = (byte) left3;
        a[(y0 + 3) * stride + x0 + 1] = (byte) left3;
        a[(y0 + 3) * stride + x0 + 2] = (byte) left3;
        a[(y0 + 3) * stride + x0 + 3] = (byte) left3;
    }

    static int avg3(int left, int center, int right) {
        return (left + 2 * center + right + 2) >> 2;
    }

    static int avg2(int left, int right) {
        return (left + right + 1) >> 1;
    }

}
