package org.apache.tika.parser.microsoft.fsshttpb.util;

import org.apache.tika.parser.microsoft.fsshttpb.unsigned.UByte;
import org.apache.tika.parser.microsoft.fsshttpb.unsigned.Unsigned;

/**
 * Implement a converter which converts to/from little-endian byte arrays
 */
public class LittleEndianBitConverter {
    /**
     * Prevents a default instance of the LittleEndianBitConverter class from being created
     */
    private LittleEndianBitConverter() {
    }

    /**
     * Returns a 16-bit unsigned integer converted from two bytes at a specified position in a byte array.
     *
     * @param array Specify an array of bytes.
     * @param index Specify the starting position.
     * @return Returns a 16-bit unsigned integer formed by two bytes beginning at startIndex.
     */
    public static short ToUInt16(byte[] array, int index) {
        CheckByteArgument(array, index, 2);
        return (short) ConvertFromBytes(array, index, 2);
    }

    /**
     * Returns a 32-bit unsigned integer converted from two bytes at a specified position in a byte array.
     *
     * @param array Specify an array of bytes.
     * @param index Specify the starting position.
     * @return Returns a 32-bit unsigned integer formed by two bytes beginning at startIndex.
     */
    public static int ToUInt32(byte[] array, int index) {
        CheckByteArgument(array, index, 4);
        return (int) ConvertFromBytes(array, index, 4);
    }

    /**
     * Returns a 32-bit signed integer converted from two bytes at a specified position in a byte array.
     *
     * @param array Specify an array of bytes.
     * @param index Specify the starting position.
     * @return Returns a 32-bit signed integer formed by two bytes beginning at startIndex.
     */
    public static int ToInt32(byte[] array, int index) {
        CheckByteArgument(array, index, 4);
        return (int) ConvertFromBytes(array, index, 4);
    }

    /**
     * Returns a 16-bit signed integer converted from two bytes at a specified position in a byte array.
     *
     * @param array Specify an array of bytes.
     * @param index Specify the starting position.
     * @return Returns a 16-bit signed integer formed by two bytes beginning at startIndex.
     */
    public static short ToInt16(byte[] array, int index) {
        CheckByteArgument(array, index, 4);
        return (short) ConvertFromBytes(array, index, 2);
    }

    /**
     * Returns a 64-bit unsigned integer converted from two bytes at a specified position in a byte array.
     *
     * @param array Specify an array of bytes.
     * @param index Specify the starting position.
     * @return Returns a 64-bit unsigned integer formed by two bytes beginning at startIndex.
     */
    public static long ToUInt64(byte[] array, int index) {
        CheckByteArgument(array, index, 8);
        return ConvertFromBytes(array, index, 8);
    }

    /**
     * Returns the specified 64-bit unsigned integer value as an array of bytes.
     *
     * @param value Specify the number to convert.
     * @return Returns an array of bytes with length 8.
     */
    public static byte[] GetBytes(long value) {
        byte[] buffer = new byte[8];
        ConvertToBytes(value, buffer);
        return buffer;
    }

    /**
     * Returns the specified 32-bit unsigned integer value as an array of bytes.
     *
     * @param value Specify the number to convert.
     * @return Returns an array of bytes with length 4.
     */
    public static byte[] GetBytes(int value) {
        byte[] buffer = new byte[4];
        ConvertToBytes(value, buffer);
        return buffer;
    }

    /**
     * Returns a value built from the specified number of bytes from the given buffer,
     * starting at index.
     *
     * @param buffer         Specify the data in byte array format
     * @param startIndex     Specify the first index to use
     * @param bytesToConvert Specify the number of bytes to use
     * @return Return the value built from the given bytes
     */

    private static long ConvertFromBytes(byte[] buffer, int startIndex, int bytesToConvert) {
        long ret = 0;
        int bitCount = 0;
        for (int i = 0; i < bytesToConvert; i++) {
            UByte ubyte = Unsigned.ubyte(buffer[startIndex + i]);
            ret |= ubyte.longValue() << bitCount;

            bitCount += 8;
        }

        return ret;
    }

    /**
     * This method is used to convert the specified value to the buffer.
     *
     * @param value  Specify the value to convert.
     * @param buffer Specify the buffer which copies the bytes into.
     */
    private static void ConvertToBytes(long value, byte[] buffer) {
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) (value & 0xff);
            value = value >> 8;
        }
    }

    /**
     * This method is used to check the given argument for validity.
     *
     * @param value         Specify the byte array.
     * @param startIndex    Specify the start index.
     * @param bytesRequired Specify the number of bytes.
     */
    private static void CheckByteArgument(byte[] value, int startIndex, int bytesRequired) {
        if (value == null) {
            throw new RuntimeException("value");
        }

        if (startIndex < 0) {
            throw new RuntimeException("The index cannot be less than 0.");
        }

        if (startIndex > value.length - bytesRequired) {
            throw new RuntimeException("startIndex " + startIndex + " is less than value.length (" + value.length + ") minus bytesRequired (" + bytesRequired + ")");
        }
    }
}