package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/// <summary>
/// An 32-bit header for a compound object would indicate the start of a stream object
/// </summary>
public class StreamObjectHeaderStart32bit extends StreamObjectHeaderStart {
    /// <summary>
    /// Initializes a new instance of the StreamObjectHeaderStart32bit class with specified type and length.
    /// </summary>
    /// <param name="type">Specify the type of the StreamObjectHeaderStart32bit.</param>
    /// <param name="length">Specify the length of the StreamObjectHeaderStart32bit.</param>
    public StreamObjectHeaderStart32bit(StreamObjectTypeHeaderStart type, int length) {
        this.headerType = StreamObjectHeaderStart.StreamObjectHeaderStart32bit;
        this.type = type;
        this.compound = StreamObject.getCompoundTypes().contains(this.type) ? 1 : 0;

        if (length >= 32767) {
            this.length = 32767;
            this.largeLength = new Compact64bitInt((long) length);
        } else {
            this.length = length;
            this.largeLength = null;
        }
    }

    /// <summary>
    /// Initializes a new instance of the StreamObjectHeaderStart32bit class, this is the default constructor.
    /// </summary>
    public StreamObjectHeaderStart32bit() {
    }

    /// <summary>
    /// Initializes a new instance of the StreamObjectHeaderStart32bit class with specified type.
    /// </summary>
    /// <param name="streamObjectTypeHeaderStart">Specify the type of the StreamObjectHeaderStart32bit.</param>
    public StreamObjectHeaderStart32bit(StreamObjectTypeHeaderStart streamObjectTypeHeaderStart) {
        this.type = streamObjectTypeHeaderStart;
    }

    /// <summary>
    /// Gets or sets an optional compact uint64 that specifies the length in bytes for additional data (if any).
    /// This field MUST be specified if the Length field contains 32767 and MUST NOT be specified if the Length field
    /// contains any other value than 32767.
    /// </summary>
    public Compact64bitInt largeLength;

    /// <summary>
    /// This method is used to convert the element of StreamObjectHeaderStart32bit basic object into a byte List.
    /// </summary>
    /// <returns>Return the byte list which store the byte information of StreamObjectHeaderStart32bit.</returns>
    @Override
    public List<Byte> SerializeToByteList() {
        BitWriter bitFieldWriter = new BitWriter(4);
        bitFieldWriter.AppendInit32(this.headerType, 2);
        bitFieldWriter.AppendInit32(this.compound, 1);
        bitFieldWriter.AppendUInit32(this.type.getIntVal(), 14);
        bitFieldWriter.AppendInit32(this.length, 15);

        List<Byte> listByte = bitFieldWriter.getByteList();

        if (this.largeLength != null) {
            listByte.addAll(this.largeLength.SerializeToByteList());
        }

        return listByte;
    }

    /// <summary>
    /// This method is used to deserialize the StreamObjectHeaderStart32bit basic object from the specified byte array and start index.
    /// </summary>
    /// <param name="byteArray">Specify the byte array.</param>
    /// <param name="startIndex">Specify the start index from the byte array.</param>
    /// <returns>Return the length in byte of the StreamObjectHeaderStart32bit basic object.</returns>
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        BitReader bitReader = new BitReader(byteArray, startIndex);
        this.headerType = bitReader.ReadInt32(2);
        if (this.headerType != StreamObjectHeaderStart.StreamObjectHeaderStart32bit) {
            throw new RuntimeException(String.format(
                    "Failed to get the StreamObjectHeaderStart32bit header type value, expect value %s, but actual value is %s",
                    StreamObjectHeaderStart.StreamObjectHeaderStart32bit, this.headerType));
        }

        this.compound = bitReader.ReadInt32(1);
        int typeValue = bitReader.ReadInt32(14);
        this.type = StreamObjectTypeHeaderStart.fromIntVal(typeValue);
        if (type == null) {
            throw new RuntimeException(String.format(
                    "Failed to get the StreamObjectHeaderStart32bit type value, the value %s is not defined",
                    typeValue));
        }

        if (StreamObject.getCompoundTypes().contains(this.type) && this.compound != 1) {
            throw new RuntimeException(String.format(
                    "Failed to parse the StreamObjectHeaderStart32bit header. If the type value is %s then the compound value should 1, but actual value is 0",
                    typeValue));
        }

        this.length = bitReader.ReadInt32(15);

        AtomicInteger index = new AtomicInteger(startIndex);
        index.addAndGet(4);

        if (this.length == 32767) {
            this.largeLength = BasicObject.parse(byteArray, index, Compact64bitInt.class);
        }

        return index.get() - startIndex;
    }
}
