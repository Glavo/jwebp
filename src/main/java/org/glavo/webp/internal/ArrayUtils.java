// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0
package org.glavo.webp.internal;

import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Unmodifiable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/// Provides primitive-array utilities used by the decoder.
///
/// Zero-length arrays have no mutable elements, so independent consumers may safely use the
/// same instances as empty values.
@NotNullByDefault
public final class ArrayUtils {

    /// Byte-array view used for little-endian `short` loads.
    private static final VarHandle SHORT_LE = MethodHandles.byteArrayViewVarHandle(
            short[].class,
            ByteOrder.LITTLE_ENDIAN
    );

    /// Byte-array view used for big-endian `short` loads.
    private static final VarHandle SHORT_BE = MethodHandles.byteArrayViewVarHandle(
            short[].class,
            ByteOrder.BIG_ENDIAN
    );

    /// Byte-array view used for little-endian `int` loads.
    private static final VarHandle INT_LE = MethodHandles.byteArrayViewVarHandle(
            int[].class,
            ByteOrder.LITTLE_ENDIAN
    );

    /// Byte-array view used for big-endian `int` loads.
    private static final VarHandle INT_BE = MethodHandles.byteArrayViewVarHandle(
            int[].class,
            ByteOrder.BIG_ENDIAN
    );

    /// Byte-array view used for little-endian `long` loads.
    private static final VarHandle LONG_LE = MethodHandles.byteArrayViewVarHandle(
            long[].class,
            ByteOrder.LITTLE_ENDIAN
    );

    /// Byte-array view used for big-endian `long` loads.
    private static final VarHandle LONG_BE = MethodHandles.byteArrayViewVarHandle(
            long[].class,
            ByteOrder.BIG_ENDIAN
    );

    /// Shared zero-length byte array.
    public static final byte @Unmodifiable [] EMPTY_BYTE_ARRAY = new byte[0];

    /// Shared zero-length char array.
    public static final char @Unmodifiable [] EMPTY_CHAR_ARRAY = new char[0];

    /// Shared zero-length int array.
    public static final int @Unmodifiable [] EMPTY_INT_ARRAY = new int[0];

    /// Returns the little-endian `short` beginning at a byte offset.
    ///
    /// The offset need not be aligned to a `short` boundary.
    ///
    /// @param array the source bytes
    /// @param offset the byte offset of the first source byte
    /// @return the decoded value
    /// @throws IndexOutOfBoundsException if two bytes are not available at `offset`
    public static short getShortLE(byte[] array, int offset) {
        return (short) SHORT_LE.get(array, offset);
    }

    /// Returns the unsigned little-endian 16-bit value beginning at a byte offset.
    ///
    /// The offset need not be aligned to a `short` boundary.
    ///
    /// @param array the source bytes
    /// @param offset the byte offset of the first source byte
    /// @return the decoded value, from `0` through `65535`
    /// @throws IndexOutOfBoundsException if two bytes are not available at `offset`
    public static int getUnsignedShortLE(byte[] array, int offset) {
        return Short.toUnsignedInt(getShortLE(array, offset));
    }

    /// Returns the big-endian `short` beginning at a byte offset.
    ///
    /// The offset need not be aligned to a `short` boundary.
    ///
    /// @param array the source bytes
    /// @param offset the byte offset of the first source byte
    /// @return the decoded value
    /// @throws IndexOutOfBoundsException if two bytes are not available at `offset`
    public static short getShortBE(byte[] array, int offset) {
        return (short) SHORT_BE.get(array, offset);
    }

    /// Returns the unsigned big-endian 16-bit value beginning at a byte offset.
    ///
    /// The offset need not be aligned to a `short` boundary.
    ///
    /// @param array the source bytes
    /// @param offset the byte offset of the first source byte
    /// @return the decoded value, from `0` through `65535`
    /// @throws IndexOutOfBoundsException if two bytes are not available at `offset`
    public static int getUnsignedShortBE(byte[] array, int offset) {
        return Short.toUnsignedInt(getShortBE(array, offset));
    }

    /// Returns the little-endian `int` beginning at a byte offset.
    ///
    /// The offset need not be aligned to an `int` boundary.
    ///
    /// @param array the source bytes
    /// @param offset the byte offset of the first source byte
    /// @return the decoded value
    /// @throws IndexOutOfBoundsException if four bytes are not available at `offset`
    public static int getIntLE(byte[] array, int offset) {
        return (int) INT_LE.get(array, offset);
    }

    /// Writes an `int` in little-endian order beginning at a byte offset.
    ///
    /// The offset need not be aligned to an `int` boundary.
    ///
    /// @param array the destination bytes
    /// @param offset the byte offset of the first destination byte
    /// @param value the value to write
    /// @throws IndexOutOfBoundsException if four bytes are not available at `offset`
    public static void setIntLE(byte[] array, int offset, int value) {
        INT_LE.set(array, offset, value);
    }

    /// Returns the unsigned little-endian 32-bit value beginning at a byte offset.
    ///
    /// The offset need not be aligned to an `int` boundary.
    ///
    /// @param array the source bytes
    /// @param offset the byte offset of the first source byte
    /// @return the decoded value, from `0` through `4294967295`
    /// @throws IndexOutOfBoundsException if four bytes are not available at `offset`
    public static long getUnsignedIntLE(byte[] array, int offset) {
        return Integer.toUnsignedLong(getIntLE(array, offset));
    }

    /// Returns the big-endian `int` beginning at a byte offset.
    ///
    /// The offset need not be aligned to an `int` boundary.
    ///
    /// @param array the source bytes
    /// @param offset the byte offset of the first source byte
    /// @return the decoded value
    /// @throws IndexOutOfBoundsException if four bytes are not available at `offset`
    public static int getIntBE(byte[] array, int offset) {
        return (int) INT_BE.get(array, offset);
    }

    /// Returns the unsigned big-endian 32-bit value beginning at a byte offset.
    ///
    /// The offset need not be aligned to an `int` boundary.
    ///
    /// @param array the source bytes
    /// @param offset the byte offset of the first source byte
    /// @return the decoded value, from `0` through `4294967295`
    /// @throws IndexOutOfBoundsException if four bytes are not available at `offset`
    public static long getUnsignedIntBE(byte[] array, int offset) {
        return Integer.toUnsignedLong(getIntBE(array, offset));
    }

    /// Returns the little-endian `long` beginning at a byte offset.
    ///
    /// The offset need not be aligned to a `long` boundary.
    ///
    /// @param array the source bytes
    /// @param offset the byte offset of the first source byte
    /// @return the decoded value
    /// @throws IndexOutOfBoundsException if eight bytes are not available at `offset`
    public static long getLongLE(byte[] array, int offset) {
        return (long) LONG_LE.get(array, offset);
    }

    /// Returns the big-endian `long` beginning at a byte offset.
    ///
    /// The offset need not be aligned to a `long` boundary.
    ///
    /// @param array the source bytes
    /// @param offset the byte offset of the first source byte
    /// @return the decoded value
    /// @throws IndexOutOfBoundsException if eight bytes are not available at `offset`
    public static long getLongBE(byte[] array, int offset) {
        return (long) LONG_BE.get(array, offset);
    }

    /// Prevents instantiation.
    private ArrayUtils() {
    }
}
