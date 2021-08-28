package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

/// <summary>
/// A 9-byte encoding of values in the range 0x0002000000000000 through 0xFFFFFFFFFFFFFFFF
/// </summary>
public class Compact64bitInt extends BasicObject {
    /// <summary>
    /// Specify the type value for compact uint zero type value.
    /// </summary>
    public static final int CompactUintNullType = 0;

    /// <summary>
    /// Specify the type value for compact uint 7 bits type value.
    /// </summary>
    public static final int CompactUint7bitType = 1;

    /// <summary>
    /// Specify the type value for compact uint 14 bits type value.
    /// </summary>
    public static final int CompactUint14bitType = 2;

    /// <summary>
    /// Specify the type value for compact uint 21 bits type value.
    /// </summary>
    public static final int CompactUint21bitType = 4;

    /// <summary>
    /// Specify the type value for compact uint 28 bits type value.
    /// </summary>
    public static final int CompactUint28bitType = 8;

    /// <summary>
    /// Specify the type value for compact uint 35 bits type value.
    /// </summary>
    public static final int CompactUint35bitType = 16;

    /// <summary>
    /// Specify the type value for compact uint 42 bits type value.
    /// </summary>
    public static final int CompactUint42bitType = 32;

    /// <summary>
    /// Specify the type value for compact uint 49 bits type value.
    /// </summary>
    public static final int CompactUint49bitType = 64;

    /// <summary>
    /// Specify the type value for compact uint 64 bits type value.
    /// </summary>
    public static final int CompactUint64bitType = 128;

    /// <summary>
    /// Initializes a new instance of the Compact64bitInt class with specified value.
    /// </summary>
    /// <param name="decodedValue">Decoded value</param>
    public Compact64bitInt(long decodedValue) {
        this.decodedValue = decodedValue;
    }

    /// <summary>
    /// Initializes a new instance of the Compact64bitInt class, this is the default constructor.
    /// </summary>
    public Compact64bitInt() {
        this.decodedValue = 0;
    }

    private int type;
    private long decodedValue;


    /// <summary>
    /// This method is used to convert the element of Compact64bitInt basic object into a byte List.
    /// </summary>
    /// <returns>Return the byte list which store the byte information of Compact64bitInt.</returns>
    @Override
    public List<Byte> SerializeToByteList() {
        BitWriter bitWriter = new BitWriter(9);

        if (this.decodedValue == 0) {
            bitWriter.AppendUInt64(0, 8);
        } else if (this.decodedValue >= 0x01 && this.decodedValue <= 0x7F) {
            bitWriter.AppendUInt64(CompactUint7bitType, 1);
            bitWriter.AppendUInt64(this.decodedValue, 7);
        } else if (this.decodedValue >= 0x0080 && this.decodedValue <= 0x3FFF) {
            bitWriter.AppendUInt64(CompactUint14bitType, 2);
            bitWriter.AppendUInt64(this.decodedValue, 14);
        } else if (this.decodedValue >= 0x004000 && this.decodedValue <= 0x1FFFFF) {
            bitWriter.AppendUInt64(CompactUint21bitType, 3);
            bitWriter.AppendUInt64(this.decodedValue, 21);
        } else if (this.decodedValue >= 0x0200000 && this.decodedValue <= 0xFFFFFFF) {
            bitWriter.AppendUInt64(CompactUint28bitType, 4);
            bitWriter.AppendUInt64(this.decodedValue, 28);
        } else if (this.decodedValue >= 0x010000000 && this.decodedValue <= 0x7FFFFFFFFL) {
            bitWriter.AppendUInt64(CompactUint35bitType, 5);
            bitWriter.AppendUInt64(this.decodedValue, 35);
        } else if (this.decodedValue >= 0x00800000000L && this.decodedValue <= 0x3FFFFFFFFFFL) {
            bitWriter.AppendUInt64(CompactUint42bitType, 6);
            bitWriter.AppendUInt64(this.decodedValue, 42);
        } else if (this.decodedValue >= 0x0040000000000L && this.decodedValue <= 0x1FFFFFFFFFFFFL) {
            bitWriter.AppendUInt64(CompactUint49bitType, 7);
            bitWriter.AppendUInt64(this.decodedValue, 49);
        } else if (this.decodedValue >= 0x0002000000000000L) {
            bitWriter.AppendUInt64(CompactUint64bitType, 8);
            bitWriter.AppendUInt64(this.decodedValue, 64);
        }
        return bitWriter.getByteList();
    }

    /// <summary>
    /// This method is used to deserialize the Compact64bitInt basic object from the specified byte array and start index.
    /// </summary>
    /// <param name="byteArray">Specify the byte array.</param>
    /// <param name="startIndex">Specify the start index from the byte array.</param>
    /// <returns>Return the length in byte of the Compact64bitInt basic object.</returns>
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) // return the length consumed
    {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        int numberOfContinousZeroBit = 0;
        while (numberOfContinousZeroBit < 8 && bitReader.MoveNext()) {
            if (!bitReader.getCurrent()) {
                numberOfContinousZeroBit++;
            } else {
                break;
            }
        }

        switch (numberOfContinousZeroBit) {
            case 0:
                this.decodedValue = bitReader.ReadUInt64(7);
                this.type = CompactUint7bitType;
                return 1;

            case 1:
                this.decodedValue = bitReader.ReadUInt64(14);
                this.type = CompactUint14bitType;
                return 2;

            case 2:
                this.decodedValue = bitReader.ReadUInt64(21);
                this.type = CompactUint21bitType;
                return 3;

            case 3:
                this.decodedValue = bitReader.ReadUInt64(28);
                this.type = CompactUint28bitType;
                return 4;

            case 4:
                this.decodedValue = bitReader.ReadUInt64(35);
                this.type = CompactUint35bitType;
                return 5;

            case 5:
                this.decodedValue = bitReader.ReadUInt64(42);
                this.type = CompactUint42bitType;
                return 6;

            case 6:
                this.decodedValue = bitReader.ReadUInt64(49);
                this.type = CompactUint49bitType;
                return 7;

            case 7:
                this.decodedValue = bitReader.ReadUInt64(64);
                this.type = CompactUint64bitType;
                return 9;

            case 8:
                this.decodedValue = 0;
                this.type = CompactUintNullType;
                return 1;

            default:
                throw new RuntimeException("Failed to parse the Compact64bitInt, the type value is unexpected");
        }
    }

    public int getType() {
        return type;
    }

    public long getDecodedValue() {
        return decodedValue;
    }

    public Compact64bitInt setType(int type) {
        this.type = type;
        return this;
    }

    public Compact64bitInt setDecodedValue(long decodedValue) {
        this.decodedValue = decodedValue;
        return this;
    }
}