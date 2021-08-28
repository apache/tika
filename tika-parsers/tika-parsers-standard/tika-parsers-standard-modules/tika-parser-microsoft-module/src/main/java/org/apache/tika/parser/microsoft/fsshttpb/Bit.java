package org.apache.tika.parser.microsoft.fsshttpb;

/// <summary>
/// The class is used to read/set bit value for a byte array.
/// </summary>
public class Bit {
    /// <summary>
    /// Read a bit value from a byte array with the specified bit position.
    /// </summary>
    /// <param name="array">Specify the byte array.</param>
    /// <param name="bit">Specify the bit position.</param>
    /// <returns>Return the bit value in the specified bit position.</returns>
    public static boolean IsBitSet(byte[] array, long bit) {
        return (array[(int) (bit / 8)] & (1 << (int) (bit % 8))) != 0;
    }

    /// <summary>
    /// Set a bit value to "On" in the specified byte array with the specified bit position.
    /// </summary>
    /// <param name="array">Specify the byte array.</param>
    /// <param name="bit">Specify the bit position.</param>
    public static void SetBit(byte[] array, long bit) {
        array[(int) (bit / 8)] |= (byte) (1 << (int) (bit % 8));
    }

    /// <summary>
    /// Set a bit value to "Off" in the specified byte array with the specified bit position.
    /// </summary>
    /// <param name="array">Specify the byte array.</param>
    /// <param name="bit">Specify the bit position.</param>
    public static void ClearBit(byte[] array, long bit) {
        array[(int) (bit / 8)] &= (byte) (~(1 << (int) (bit % 8)));
    }
}