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
package org.glavo.javafx.webp.internal.lossy;

/// Shared VP8 lossy constants and enums.
///
/// This class intentionally starts with the mode and segment definitions that are required by
/// the prediction and reconstruction helpers. Additional probability tables are added alongside the
/// main VP8 decoder port, where they are actually consumed.
final class LossyCommon {

    static final int MAX_SEGMENTS = 4;
    static final int NUM_DCT_TOKENS = 12;

    static final byte DC_PRED = 0;
    static final byte V_PRED = 1;
    static final byte H_PRED = 2;
    static final byte TM_PRED = 3;
    static final byte B_PRED = 4;

    static final byte B_DC_PRED = 0;
    static final byte B_TM_PRED = 1;
    static final byte B_VE_PRED = 2;
    static final byte B_HE_PRED = 3;
    static final byte B_LD_PRED = 4;
    static final byte B_RD_PRED = 5;
    static final byte B_VR_PRED = 6;
    static final byte B_VL_PRED = 7;
    static final byte B_HD_PRED = 8;
    static final byte B_HU_PRED = 9;

    private LossyCommon() {
    }

    enum Plane {
        Y_COEFF_1,
        Y2,
        CHROMA,
        Y_COEFF_0
    }

    enum LumaMode {
        DC(DC_PRED),
        V(V_PRED),
        H(H_PRED),
        TM(TM_PRED),
        B(B_PRED);

        final byte code;

        LumaMode(byte code) {
            this.code = code;
        }

        static LumaMode fromCode(int code) {
            return switch (code) {
                case DC_PRED -> DC;
                case V_PRED -> V;
                case H_PRED -> H;
                case TM_PRED -> TM;
                case B_PRED -> B;
                default -> null;
            };
        }

        IntraMode asIntraMode() {
            return switch (this) {
                case DC -> IntraMode.DC;
                case V -> IntraMode.VE;
                case H -> IntraMode.HE;
                case TM -> IntraMode.TM;
                case B -> null;
            };
        }
    }

    enum ChromaMode {
        DC(DC_PRED),
        V(V_PRED),
        H(H_PRED),
        TM(TM_PRED);

        final byte code;

        ChromaMode(byte code) {
            this.code = code;
        }

        static ChromaMode fromCode(int code) {
            return switch (code) {
                case DC_PRED -> DC;
                case V_PRED -> V;
                case H_PRED -> H;
                case TM_PRED -> TM;
                default -> null;
            };
        }
    }

    enum IntraMode {
        DC(B_DC_PRED),
        TM(B_TM_PRED),
        VE(B_VE_PRED),
        HE(B_HE_PRED),
        LD(B_LD_PRED),
        RD(B_RD_PRED),
        VR(B_VR_PRED),
        VL(B_VL_PRED),
        HD(B_HD_PRED),
        HU(B_HU_PRED);

        final byte code;

        IntraMode(byte code) {
            this.code = code;
        }

        static IntraMode fromCode(int code) {
            return switch (code) {
                case B_DC_PRED -> DC;
                case B_TM_PRED -> TM;
                case B_VE_PRED -> VE;
                case B_HE_PRED -> HE;
                case B_LD_PRED -> LD;
                case B_RD_PRED -> RD;
                case B_VR_PRED -> VR;
                case B_VL_PRED -> VL;
                case B_HD_PRED -> HD;
                case B_HU_PRED -> HU;
                default -> null;
            };
        }
    }

    static final class Segment {
        short ydc;
        short yac;
        short y2dc;
        short y2ac;
        short uvdc;
        short uvac;
        boolean deltaValues;
        byte quantizerLevel;
        byte loopFilterLevel;
    }
}
