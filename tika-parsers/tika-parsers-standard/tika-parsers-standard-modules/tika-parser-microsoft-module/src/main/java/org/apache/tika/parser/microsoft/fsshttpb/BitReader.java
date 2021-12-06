package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.Arrays;
import java.util.BitSet;
import java.util.UUID;

/**
 * A class is used to extract values across byte boundaries with arbitrary bit positions.
 */
public class BitReader {
    /**
     * A byte array which contains the bytes need to be read.
     */
    private BitSet bitSet;

    /**
     * A start position which will be not changed in the process of reading.
     * This value will be used for recording the start position and will be used by the function reset.
     */
    private long startPosition;

    /**
     * An offset which is used to keep trace for the current read position in bit.
     */
    private long offset;

    /**
     * The length of the byte Array which contains the byte need to be read.
     */
    private long length;

    /**
     * Initializes a new instance of the BitReader class with specified bytes buffer and start position in byte.
     *
     * @param array Specify the byte array which contains the bytes need to be read.
     * @param index Specify the start position in byte.
     */
    public BitReader(byte[] array, int index) {
        this.offset = ((long) index * 8) - 1;
        this.startPosition = this.offset;
        this.length = (long) array.length * 8;
        this.bitSet = BitSet.valueOf(array);
    }

    public boolean getCurrent() {
        return bitSet.get((int) offset);
    }

    /**
     * Read specified bit length content as an UInt64 type and increase the bit offset.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the UInt64 type value.
     */
    public long ReadUInt64(int readingLength) {
        byte[] uint64Bytes = this.GetBytes(readingLength, 8);
        return LittleEndianBitConverter.ToUInt64(uint64Bytes, 0);
    }

    /**
     * Read specified bit length content as an UInt32 type and increase the bit offset with the specified length.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the UInt32 type value.
     */
    public int ReadUInt32(int readingLength) {
        byte[] uint32Bytes = this.GetBytes(readingLength, 4);
        return LittleEndianBitConverter.ToUInt32(uint32Bytes, 0);
    }

    public int ReadUInt16(int readingLength) {
        byte[] uint16Bytes = this.GetBytes(readingLength, 2);
        return LittleEndianBitConverter.ToUInt16(uint16Bytes, 0);
    }

    /**
     * Reading the bytes specified by the byte length.
     *
     * @param readingLength Specify the reading byte length.
     * @return Return the read bytes array.
     */
    public byte[] ReadBytes(int readingLength) {
        return this.GetBytes(readingLength * 8, readingLength);
    }

    /**
     * Read specified bit length content as an UInt16 type and increase the bit offset with the specified length.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the UInt16 value.
     */
    public short ReadInt16(int readingLength) {
        byte[] uint16Bytes = this.GetBytes(readingLength, 2);
        return LittleEndianBitConverter.ToInt16(uint16Bytes, 0);
    }

    /**
     * Read specified bit length content as an Int32 type and increase the bit offset with the specified length.
     *
     * @param readingLength Specify the reading bit length.
     * @return Return the Int32 type value.
     */
    public int ReadInt32(int readingLength) {
        byte[] uint32Bytes = this.GetBytes(readingLength, 4);
        return LittleEndianBitConverter.ToInt32(uint32Bytes, 0);
    }

    /**
     * Read as a GUID from the current offset position and increate the bit offset with 128 bit.
     *
     * @return Return the GUID value.
     */
    public UUID ReadGuid() {
        return UUID.nameUUIDFromBytes(this.GetBytes(128, 16));
    }

    /**
     * Advances the enumerator to the next bit of the byte array.
     *
     * @return true if the enumerator was successfully advanced to the next bit; false if the enumerator has passed the end of the byte array.
     */
    public boolean MoveNext() {
        return ++this.offset < this.length;
    }

    /**
     * Assign the internal read buffer to null.
     */
    public void Dispose() {
        this.bitSet = null;
    }

    /**
     * Sets the enumerator to its initial position, which is before the first bit in the byte array.
     */
    public void Reset() {
        this.offset = this.startPosition;
    }

    /**
     * Construct a byte array with specified bit length and the specified the byte array size.
     *
     * @param needReadlength Specify the need read bit length.
     * @param size           Specify the byte array size.
     * @return Returns the constructed byte array.
     */
    private byte[] GetBytes(int needReadlength, int size) {
        BitSet retSet = new BitSet(size);
        int i = 0;
        while (i < needReadlength) {
            if (!this.MoveNext()) {
                throw new RuntimeException("Unexpected to meet the byte array end.");
            }
            if (getCurrent()) {
                retSet.set(i);
            } else {
                retSet.clear(i);
            }
            i++;
        }
        byte [] result = new byte[size];
        Arrays.fill(result, (byte)0);
        byte [] retSetBa = retSet.toByteArray();
        for (i = 0; i < retSetBa.length; ++i) {
            result[i] = retSetBa[i];
        }
        return result;
    }

    private static String toBinaryString(BitSet bs, int nbits) {
        final StringBuilder buffer = new StringBuilder(bs.size());
        for (int i = nbits - 1; i >= 0; --i) {
            if (i < bs.size()) {
                buffer.append(bs.get(i) ? "1" : "0");
            } else {
                buffer.append("0");
            }
        }
        return buffer.toString();
    }
}
