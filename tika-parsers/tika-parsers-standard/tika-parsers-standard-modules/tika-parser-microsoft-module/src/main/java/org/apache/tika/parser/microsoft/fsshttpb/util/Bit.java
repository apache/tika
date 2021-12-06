package org.apache.tika.parser.microsoft.fsshttpb.util;

/**
 * The class is used to read/set bit value for a byte array
 */
public class Bit {
    /**
     * Read a bit value from a byte array with the specified bit position.
     *
     * @param array Specify the byte array.
     * @param bit   Specify the bit position.
     * @return Return the bit value in the specified bit position.
     */
    public static boolean IsBitSet(byte[] array, long bit) {
        return (array[(int) (bit / 8)] & (1 << (int) (bit % 8))) != 0;
    }

    /**
     * Set a bit value to "On" in the specified byte array with the specified bit position.
     *
     * @param array Specify the byte array.
     * @param bit   Specify the bit position.
     */
    public static void SetBit(byte[] array, long bit) {
        array[(int) (bit / 8)] |= (byte) (1 << (int) (bit % 8));
    }

    /**
     * Set a bit value to "Off" in the specified byte array with the specified bit position.
     *
     * @param array Specify the byte array.
     * @param bit   Specify the bit position.
     */
    public static void ClearBit(byte[] array, long bit) {
        array[(int) (bit / 8)] &= (byte) (~(1 << (int) (bit % 8)));
    }
}