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

import org.glavo.webp.internal.Argb;

/// YUV to RGB conversion helpers used by the VP8 decoder.
@NotNullByDefault
final class LossyYuv {

    private static final int YUV_FIX = 16;
    private static final int YUV_HALF = 1 << (YUV_FIX - 1);

    private LossyYuv() {
    }

    static void fillArgbBufferFancy(int[] buffer, byte[] yBuffer, byte[] uBuffer, byte[] vBuffer, int width, int height, int bufferWidth) {
        int chromaBufferWidth = bufferWidth / 2;
        int chromaWidth = (width + 1) / 2;

        fillRowFancyWithOneUvRowArgb(buffer, 0, yBuffer, 0, uBuffer, 0, vBuffer, 0, width);

        int rowBufferOffset = width;
        int yOffset = bufferWidth;
        int chromaWindowOffset = 0;

        while (rowBufferOffset + width * 2 <= buffer.length && yOffset + bufferWidth * 2 <= height * bufferWidth) {
            fillRowFancyWithTwoUvRowsArgb(
                    buffer, rowBufferOffset,
                    yBuffer, yOffset,
                    uBuffer, chromaWindowOffset,
                    uBuffer, chromaWindowOffset + chromaBufferWidth,
                    vBuffer, chromaWindowOffset,
                    vBuffer, chromaWindowOffset + chromaBufferWidth,
                    width, chromaWidth
            );
            fillRowFancyWithTwoUvRowsArgb(
                    buffer, rowBufferOffset + width,
                    yBuffer, yOffset + bufferWidth,
                    uBuffer, chromaWindowOffset + chromaBufferWidth,
                    uBuffer, chromaWindowOffset,
                    vBuffer, chromaWindowOffset + chromaBufferWidth,
                    vBuffer, chromaWindowOffset,
                    width, chromaWidth
            );

            rowBufferOffset += width * 2;
            yOffset += bufferWidth * 2;
            chromaWindowOffset += chromaBufferWidth;
        }

        if (rowBufferOffset < buffer.length) {
            int chromaHeight = (height + 1) / 2;
            int startChromaIndex = (chromaHeight - 1) * chromaBufferWidth;
            fillRowFancyWithOneUvRowArgb(buffer, rowBufferOffset, yBuffer, yOffset, uBuffer, startChromaIndex, vBuffer, startChromaIndex, width);
        }
    }

    static void fillArgbBufferSimple(int[] buffer, byte[] yBuffer, byte[] uBuffer, byte[] vBuffer, int width, int chromaWidth, int bufferWidth) {
        int chromaStride = bufferWidth / 2;
        int chromaRow = 0;

        for (int y = 0; y < buffer.length / width; y++) {
            fillArgbRowSimple(yBuffer, y * bufferWidth, uBuffer, chromaRow * chromaStride, vBuffer, chromaRow * chromaStride, width, chromaWidth, buffer, y * width);
            if ((y & 1) != 0) {
                chromaRow++;
            }
        }
    }

    static void fillRgbBufferFancy(byte[] buffer, byte[] yBuffer, byte[] uBuffer, byte[] vBuffer, int width, int height, int bufferWidth, int bytesPerPixel) {
        int chromaBufferWidth = bufferWidth / 2;
        int chromaWidth = (width + 1) / 2;

        fillRowFancyWithOneUvRow(buffer, 0, yBuffer, 0, uBuffer, 0, vBuffer, 0, width, bytesPerPixel);

        int rowBufferOffset = width * bytesPerPixel;
        int yOffset = bufferWidth;
        int chromaWindowOffset = 0;

        while (rowBufferOffset + width * bytesPerPixel * 2 <= buffer.length && yOffset + bufferWidth * 2 <= height * bufferWidth) {
            fillRowFancyWithTwoUvRows(
                    buffer, rowBufferOffset,
                    yBuffer, yOffset,
                    uBuffer, chromaWindowOffset,
                    uBuffer, chromaWindowOffset + chromaBufferWidth,
                    vBuffer, chromaWindowOffset,
                    vBuffer, chromaWindowOffset + chromaBufferWidth,
                    width, chromaWidth, bytesPerPixel
            );
            fillRowFancyWithTwoUvRows(
                    buffer, rowBufferOffset + width * bytesPerPixel,
                    yBuffer, yOffset + bufferWidth,
                    uBuffer, chromaWindowOffset + chromaBufferWidth,
                    uBuffer, chromaWindowOffset,
                    vBuffer, chromaWindowOffset + chromaBufferWidth,
                    vBuffer, chromaWindowOffset,
                    width, chromaWidth, bytesPerPixel
            );

            rowBufferOffset += width * bytesPerPixel * 2;
            yOffset += bufferWidth * 2;
            chromaWindowOffset += chromaBufferWidth;
        }

        if (rowBufferOffset < buffer.length) {
            int chromaHeight = (height + 1) / 2;
            int startChromaIndex = (chromaHeight - 1) * chromaBufferWidth;
            fillRowFancyWithOneUvRow(buffer, rowBufferOffset, yBuffer, yOffset, uBuffer, startChromaIndex, vBuffer, startChromaIndex, width, bytesPerPixel);
        }
    }

    static void fillRgbBufferSimple(byte[] buffer, byte[] yBuffer, byte[] uBuffer, byte[] vBuffer, int width, int chromaWidth, int bufferWidth, int bytesPerPixel) {
        int rowStride = width * bytesPerPixel;
        int chromaStride = bufferWidth / 2;
        int chromaRow = 0;

        for (int y = 0; y < buffer.length / rowStride; y++) {
            fillRgbaRowSimple(yBuffer, y * bufferWidth, uBuffer, chromaRow * chromaStride, vBuffer, chromaRow * chromaStride, width, chromaWidth, buffer, y * rowStride, bytesPerPixel);
            if ((y & 1) != 0) {
                chromaRow++;
            }
        }
    }

    private static void fillRowFancyWithTwoUvRows(
            byte[] rowBuffer,
            int rowBufferOffset,
            byte[] yRow,
            int yOffset,
            byte[] uRow1,
            int uOffset1,
            byte[] uRow2,
            int uOffset2,
            byte[] vRow1,
            int vOffset1,
            byte[] vRow2,
            int vOffset2,
            int width,
            int chromaWidth,
            int bytesPerPixel
    ) {
        setPixel(
                rowBuffer,
                rowBufferOffset,
                yRow[yOffset] & 0xFF,
                getFancyChromaValue(uRow1[uOffset1] & 0xFF, uRow1[uOffset1] & 0xFF, uRow2[uOffset2] & 0xFF, uRow2[uOffset2] & 0xFF),
                getFancyChromaValue(vRow1[vOffset1] & 0xFF, vRow1[vOffset1] & 0xFF, vRow2[vOffset2] & 0xFF, vRow2[vOffset2] & 0xFF),
                bytesPerPixel
        );

        int dst = rowBufferOffset + bytesPerPixel;
        int yIndex = yOffset + 1;
        for (int chroma = 0; chroma + 1 < chromaWidth && yIndex + 1 < yOffset + width; chroma++) {
            int u1 = uRow1[uOffset1 + chroma] & 0xFF;
            int u2 = uRow1[uOffset1 + chroma + 1] & 0xFF;
            int u3 = uRow2[uOffset2 + chroma] & 0xFF;
            int u4 = uRow2[uOffset2 + chroma + 1] & 0xFF;
            int v1 = vRow1[vOffset1 + chroma] & 0xFF;
            int v2 = vRow1[vOffset1 + chroma + 1] & 0xFF;
            int v3 = vRow2[vOffset2 + chroma] & 0xFF;
            int v4 = vRow2[vOffset2 + chroma + 1] & 0xFF;

            setPixel(rowBuffer, dst, yRow[yIndex] & 0xFF, getFancyChromaValue(u1, u2, u3, u4), getFancyChromaValue(v1, v2, v3, v4), bytesPerPixel);
            dst += bytesPerPixel;
            yIndex++;
            if (yIndex < yOffset + width) {
                setPixel(rowBuffer, dst, yRow[yIndex] & 0xFF, getFancyChromaValue(u2, u1, u4, u3), getFancyChromaValue(v2, v1, v4, v3), bytesPerPixel);
                dst += bytesPerPixel;
                yIndex++;
            }
        }

        if (yIndex < yOffset + width) {
            int finalU1 = uRow1[uOffset1 + chromaWidth - 1] & 0xFF;
            int finalU2 = uRow2[uOffset2 + chromaWidth - 1] & 0xFF;
            int finalV1 = vRow1[vOffset1 + chromaWidth - 1] & 0xFF;
            int finalV2 = vRow2[vOffset2 + chromaWidth - 1] & 0xFF;
            setPixel(rowBuffer, dst, yRow[yIndex] & 0xFF, getFancyChromaValue(finalU1, finalU1, finalU2, finalU2), getFancyChromaValue(finalV1, finalV1, finalV2, finalV2), bytesPerPixel);
        }
    }

    private static void fillRowFancyWithOneUvRow(byte[] rowBuffer, int rowBufferOffset, byte[] yRow, int yOffset, byte[] uRow, int uOffset, byte[] vRow, int vOffset, int width, int bytesPerPixel) {
        setPixel(rowBuffer, rowBufferOffset, yRow[yOffset] & 0xFF, uRow[uOffset] & 0xFF, vRow[vOffset] & 0xFF, bytesPerPixel);

        int dst = rowBufferOffset + bytesPerPixel;
        int yIndex = yOffset + 1;
        int chromaWidth = (width + 1) / 2;
        for (int chroma = 0; chroma + 1 < chromaWidth && yIndex + 1 < yOffset + width; chroma++) {
            int u1 = uRow[uOffset + chroma] & 0xFF;
            int u2 = uRow[uOffset + chroma + 1] & 0xFF;
            int v1 = vRow[vOffset + chroma] & 0xFF;
            int v2 = vRow[vOffset + chroma + 1] & 0xFF;

            setPixel(rowBuffer, dst, yRow[yIndex] & 0xFF, getFancyChromaValue(u1, u2, u1, u2), getFancyChromaValue(v1, v2, v1, v2), bytesPerPixel);
            dst += bytesPerPixel;
            yIndex++;
            if (yIndex < yOffset + width) {
                setPixel(rowBuffer, dst, yRow[yIndex] & 0xFF, getFancyChromaValue(u2, u1, u2, u1), getFancyChromaValue(v2, v1, v2, v1), bytesPerPixel);
                dst += bytesPerPixel;
                yIndex++;
            }
        }

        if (yIndex < yOffset + width) {
            setPixel(rowBuffer, dst, yRow[yIndex] & 0xFF, uRow[uOffset + chromaWidth - 1] & 0xFF, vRow[vOffset + chromaWidth - 1] & 0xFF, bytesPerPixel);
        }
    }

    private static void fillRowFancyWithTwoUvRowsArgb(
            int[] rowBuffer,
            int rowBufferOffset,
            byte[] yRow,
            int yOffset,
            byte[] uRow1,
            int uOffset1,
            byte[] uRow2,
            int uOffset2,
            byte[] vRow1,
            int vOffset1,
            byte[] vRow2,
            int vOffset2,
            int width,
            int chromaWidth
    ) {
        rowBuffer[rowBufferOffset] = argbPixel(
                yRow[yOffset] & 0xFF,
                getFancyChromaValue(uRow1[uOffset1] & 0xFF, uRow1[uOffset1] & 0xFF, uRow2[uOffset2] & 0xFF, uRow2[uOffset2] & 0xFF),
                getFancyChromaValue(vRow1[vOffset1] & 0xFF, vRow1[vOffset1] & 0xFF, vRow2[vOffset2] & 0xFF, vRow2[vOffset2] & 0xFF)
        );

        int dst = rowBufferOffset + 1;
        int yIndex = yOffset + 1;
        for (int chroma = 0; chroma + 1 < chromaWidth && yIndex + 1 < yOffset + width; chroma++) {
            int u1 = uRow1[uOffset1 + chroma] & 0xFF;
            int u2 = uRow1[uOffset1 + chroma + 1] & 0xFF;
            int u3 = uRow2[uOffset2 + chroma] & 0xFF;
            int u4 = uRow2[uOffset2 + chroma + 1] & 0xFF;
            int v1 = vRow1[vOffset1 + chroma] & 0xFF;
            int v2 = vRow1[vOffset1 + chroma + 1] & 0xFF;
            int v3 = vRow2[vOffset2 + chroma] & 0xFF;
            int v4 = vRow2[vOffset2 + chroma + 1] & 0xFF;

            rowBuffer[dst++] = argbPixel(yRow[yIndex] & 0xFF, getFancyChromaValue(u1, u2, u3, u4), getFancyChromaValue(v1, v2, v3, v4));
            yIndex++;
            if (yIndex < yOffset + width) {
                rowBuffer[dst++] = argbPixel(yRow[yIndex] & 0xFF, getFancyChromaValue(u2, u1, u4, u3), getFancyChromaValue(v2, v1, v4, v3));
                yIndex++;
            }
        }

        if (yIndex < yOffset + width) {
            int finalU1 = uRow1[uOffset1 + chromaWidth - 1] & 0xFF;
            int finalU2 = uRow2[uOffset2 + chromaWidth - 1] & 0xFF;
            int finalV1 = vRow1[vOffset1 + chromaWidth - 1] & 0xFF;
            int finalV2 = vRow2[vOffset2 + chromaWidth - 1] & 0xFF;
            rowBuffer[dst] = argbPixel(
                    yRow[yIndex] & 0xFF,
                    getFancyChromaValue(finalU1, finalU1, finalU2, finalU2),
                    getFancyChromaValue(finalV1, finalV1, finalV2, finalV2)
            );
        }
    }

    private static void fillRowFancyWithOneUvRowArgb(int[] rowBuffer, int rowBufferOffset, byte[] yRow, int yOffset, byte[] uRow, int uOffset, byte[] vRow, int vOffset, int width) {
        rowBuffer[rowBufferOffset] = argbPixel(yRow[yOffset] & 0xFF, uRow[uOffset] & 0xFF, vRow[vOffset] & 0xFF);

        int dst = rowBufferOffset + 1;
        int yIndex = yOffset + 1;
        int chromaWidth = (width + 1) / 2;
        for (int chroma = 0; chroma + 1 < chromaWidth && yIndex + 1 < yOffset + width; chroma++) {
            int u1 = uRow[uOffset + chroma] & 0xFF;
            int u2 = uRow[uOffset + chroma + 1] & 0xFF;
            int v1 = vRow[vOffset + chroma] & 0xFF;
            int v2 = vRow[vOffset + chroma + 1] & 0xFF;

            rowBuffer[dst++] = argbPixel(yRow[yIndex] & 0xFF, getFancyChromaValue(u1, u2, u1, u2), getFancyChromaValue(v1, v2, v1, v2));
            yIndex++;
            if (yIndex < yOffset + width) {
                rowBuffer[dst++] = argbPixel(yRow[yIndex] & 0xFF, getFancyChromaValue(u2, u1, u2, u1), getFancyChromaValue(v2, v1, v2, v1));
                yIndex++;
            }
        }

        if (yIndex < yOffset + width) {
            rowBuffer[dst] = argbPixel(yRow[yIndex] & 0xFF, uRow[uOffset + chromaWidth - 1] & 0xFF, vRow[vOffset + chromaWidth - 1] & 0xFF);
        }
    }

    private static void fillRgbaRowSimple(byte[] yVec, int yOffset, byte[] uVec, int uOffset, byte[] vVec, int vOffset, int width, int chromaWidth, byte[] rgba, int dstOffset, int bytesPerPixel) {
        int yIndex = yOffset;
        int dst = dstOffset;
        for (int chroma = 0; chroma < chromaWidth && yIndex < yOffset + width; chroma++) {
            int u = uVec[uOffset + chroma] & 0xFF;
            int v = vVec[vOffset + chroma] & 0xFF;
            int rCoeff = mulhi(v, 26149);
            int guCoeff = mulhi(u, 6419);
            int gvCoeff = mulhi(v, 13320);
            int bCoeff = mulhi(u, 33050);

            setPixelFromCoefficients(rgba, dst, yVec[yIndex] & 0xFF, rCoeff, guCoeff, gvCoeff, bCoeff, bytesPerPixel);
            dst += bytesPerPixel;
            yIndex++;
            if (yIndex < yOffset + width) {
                setPixelFromCoefficients(rgba, dst, yVec[yIndex] & 0xFF, rCoeff, guCoeff, gvCoeff, bCoeff, bytesPerPixel);
                dst += bytesPerPixel;
                yIndex++;
            }
        }
    }

    private static void fillArgbRowSimple(byte[] yVec, int yOffset, byte[] uVec, int uOffset, byte[] vVec, int vOffset, int width, int chromaWidth, int[] argb, int dstOffset) {
        int yIndex = yOffset;
        int dst = dstOffset;
        for (int chroma = 0; chroma < chromaWidth && yIndex < yOffset + width; chroma++) {
            int u = uVec[uOffset + chroma] & 0xFF;
            int v = vVec[vOffset + chroma] & 0xFF;
            int rCoeff = mulhi(v, 26149);
            int guCoeff = mulhi(u, 6419);
            int gvCoeff = mulhi(v, 13320);
            int bCoeff = mulhi(u, 33050);

            argb[dst++] = argbPixelFromCoefficients(yVec[yIndex] & 0xFF, rCoeff, guCoeff, gvCoeff, bCoeff);
            yIndex++;
            if (yIndex < yOffset + width) {
                argb[dst++] = argbPixelFromCoefficients(yVec[yIndex] & 0xFF, rCoeff, guCoeff, gvCoeff, bCoeff);
                yIndex++;
            }
        }
    }

    static byte[][] convertImageYuv(byte[] imageData, int width, int height, int bytesPerPixel) {
        int mbWidth = (width + 15) / 16;
        int mbHeight = (height + 15) / 16;
        int ySize = 16 * mbWidth * 16 * mbHeight;
        int lumaWidth = 16 * mbWidth;
        int chromaWidth = 8 * mbWidth;
        int chromaSize = 8 * mbWidth * 8 * mbHeight;
        byte[] yBytes = new byte[ySize];
        byte[] uBytes = new byte[chromaSize];
        byte[] vBytes = new byte[chromaSize];

        for (int row = 0; row + 1 < height; row += 2) {
            int imageRowOffset1 = row * width * bytesPerPixel;
            int imageRowOffset2 = (row + 1) * width * bytesPerPixel;
            int yRowOffset1 = row * lumaWidth;
            int yRowOffset2 = (row + 1) * lumaWidth;
            int chromaRowOffset = (row / 2) * chromaWidth;

            for (int col = 0; col + 1 < width; col += 2) {
                int p1 = imageRowOffset1 + col * bytesPerPixel;
                int p2 = p1 + bytesPerPixel;
                int p3 = imageRowOffset2 + col * bytesPerPixel;
                int p4 = p3 + bytesPerPixel;

                yBytes[yRowOffset1 + col] = rgbToY(imageData, p1);
                yBytes[yRowOffset1 + col + 1] = rgbToY(imageData, p2);
                yBytes[yRowOffset2 + col] = rgbToY(imageData, p3);
                yBytes[yRowOffset2 + col + 1] = rgbToY(imageData, p4);

                int chromaIndex = chromaRowOffset + col / 2;
                uBytes[chromaIndex] = rgbToUAvg(imageData, p1, p2, p3, p4);
                vBytes[chromaIndex] = rgbToVAvg(imageData, p1, p2, p3, p4);
            }
        }

        return new byte[][]{yBytes, uBytes, vBytes};
    }

    private static int mulhi(int value, int coeff) {
        return (value * coeff) >> 8;
    }

    private static int clip(int value) {
        return Math.max(0, Math.min(255, value >> 6));
    }

    static int yuvToR(int y, int v) {
        return clip(mulhi(y, 19077) + mulhi(v, 26149) - 14234);
    }

    static int yuvToG(int y, int u, int v) {
        return clip(mulhi(y, 19077) - mulhi(u, 6419) - mulhi(v, 13320) + 8708);
    }

    static int yuvToB(int y, int u) {
        return clip(mulhi(y, 19077) + mulhi(u, 33050) - 17685);
    }

    private static int getFancyChromaValue(int main, int secondary1, int secondary2, int tertiary) {
        return (9 * main + 3 * secondary1 + 3 * secondary2 + tertiary + 8) / 16;
    }

    private static void setPixel(byte[] rgb, int offset, int y, int u, int v, int bytesPerPixel) {
        rgb[offset] = (byte) yuvToR(y, v);
        rgb[offset + 1] = (byte) yuvToG(y, u, v);
        rgb[offset + 2] = (byte) yuvToB(y, u);
        if (bytesPerPixel == 4) {
            rgb[offset + 3] = (byte) 0xFF;
        }
    }

    private static void setPixelFromCoefficients(byte[] rgb, int offset, int y, int rCoeff, int guCoeff, int gvCoeff, int bCoeff, int bytesPerPixel) {
        rgb[offset] = (byte) clip(mulhi(y, 19077) + rCoeff - 14234);
        rgb[offset + 1] = (byte) clip(mulhi(y, 19077) - guCoeff - gvCoeff + 8708);
        rgb[offset + 2] = (byte) clip(mulhi(y, 19077) + bCoeff - 17685);
        if (bytesPerPixel == 4) {
            rgb[offset + 3] = (byte) 0xFF;
        }
    }

    private static int argbPixel(int y, int u, int v) {
        return Argb.opaque(yuvToR(y, v), yuvToG(y, u, v), yuvToB(y, u));
    }

    private static int argbPixelFromCoefficients(int y, int rCoeff, int guCoeff, int gvCoeff, int bCoeff) {
        return Argb.opaque(
                clip(mulhi(y, 19077) + rCoeff - 14234),
                clip(mulhi(y, 19077) - guCoeff - gvCoeff + 8708),
                clip(mulhi(y, 19077) + bCoeff - 17685)
        );
    }

    private static byte rgbToY(byte[] rgb, int offset) {
        int luma = 16839 * (rgb[offset] & 0xFF)
                + 33059 * (rgb[offset + 1] & 0xFF)
                + 6420 * (rgb[offset + 2] & 0xFF);
        return (byte) ((luma + YUV_HALF + (16 << YUV_FIX)) >> YUV_FIX);
    }

    private static byte rgbToUAvg(byte[] rgb, int p1, int p2, int p3, int p4) {
        int u1 = rgbToURaw(rgb, p1);
        int u2 = rgbToURaw(rgb, p2);
        int u3 = rgbToURaw(rgb, p3);
        int u4 = rgbToURaw(rgb, p4);
        return (byte) ((u1 + u2 + u3 + u4) >> (YUV_FIX + 2));
    }

    private static byte rgbToVAvg(byte[] rgb, int p1, int p2, int p3, int p4) {
        int v1 = rgbToVRaw(rgb, p1);
        int v2 = rgbToVRaw(rgb, p2);
        int v3 = rgbToVRaw(rgb, p3);
        int v4 = rgbToVRaw(rgb, p4);
        return (byte) ((v1 + v2 + v3 + v4) >> (YUV_FIX + 2));
    }

    private static int rgbToURaw(byte[] rgb, int offset) {
        return -9719 * (rgb[offset] & 0xFF)
                - 19081 * (rgb[offset + 1] & 0xFF)
                + 28800 * (rgb[offset + 2] & 0xFF)
                + (128 << YUV_FIX);
    }

    private static int rgbToVRaw(byte[] rgb, int offset) {
        return 28800 * (rgb[offset] & 0xFF)
                - 24116 * (rgb[offset + 1] & 0xFF)
                - 4684 * (rgb[offset + 2] & 0xFF)
                + (128 << YUV_FIX);
    }
}
