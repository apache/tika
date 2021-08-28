package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

/// <summary>
/// An 8-bit header for a compound object would indicate the end of a stream object
/// </summary>
public class StreamObjectHeaderEnd8bit extends StreamObjectHeaderEnd {
    /// <summary>
    /// Initializes a new instance of the StreamObjectHeaderEnd8bit class with the specified type value.
    /// </summary>
    /// <param name="type">Specify the integer value of the type.</param>
    public StreamObjectHeaderEnd8bit(int type) {

        this.type = StreamObjectTypeHeaderEnd.fromIntVal(type);
        if (this.type == null) {
            throw new RuntimeException(
                    String.format("The type value %s is not defined for the stream object end 8 bit header", type));
        }

    }

    /// <summary>
    /// Initializes a new instance of the StreamObjectHeaderEnd8bit class, this is the default constructor.
    /// </summary>
    public StreamObjectHeaderEnd8bit() {
    }

    /// <summary>
    /// Initializes a new instance of the StreamObjectHeaderEnd8bit class with the specified type value.
    /// </summary>
    /// <param name="type">Specify the value of the type.</param>
    public StreamObjectHeaderEnd8bit(StreamObjectTypeHeaderEnd type) {
        this(type.getIntVal());
    }

    /// <summary>
    /// This method is used to convert the element of StreamObjectHeaderEnd8bit basic object into a byte List.
    /// </summary>
    /// <returns>Return the byte list which store the byte information of StreamObjectHeaderEnd8bit.</returns>
    @Override
    public List<Byte> SerializeToByteList() {
        BitWriter bitFieldWriter = new BitWriter(1);
        bitFieldWriter.AppendInit32(0x1, 2);
        bitFieldWriter.AppendUInit32(this.type.getIntVal(), 6);
        return bitFieldWriter.getByteList();
    }

    /// <summary>
    /// This method is used to get the byte value of the 8bit stream object header End.
    /// </summary>
    /// <returns>Return StreamObjectHeaderEnd8bit value represented by byte.</returns>
    public byte ToByte() {
        List<Byte> bytes = this.SerializeToByteList();

        if (bytes.size() != 1) {
            throw new RuntimeException("The unexpected StreamObjectHeaderEnd8bit length");
        }

        return bytes.get(0);
    }

    /// <summary>
    /// This method is used to deserialize the StreamObjectHeaderEnd8bit basic object from the specified byte array and start index.
    /// </summary>
    /// <param name="byteArray">Specify the byte array.</param>
    /// <param name="startIndex">Specify the start index from the byte array.</param>
    /// <returns>Return the length in byte of the StreamObjectHeaderEnd8bit basic object.</returns>
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        BitReader reader = new BitReader(byteArray, startIndex);
        int headerType = reader.ReadInt32(2);

        if (headerType != 0x1) {
            throw new RuntimeException(String.format(
                    "Failed to get the StreamObjectHeaderEnd8bit header type value, expect value %s, but actual value is %s",
                    0x1, headerType));
        }

        int typeValue = reader.ReadUInt32(6);
        this.type = StreamObjectTypeHeaderEnd.fromIntVal(typeValue);
        if (this.type == null) {
            throw new RuntimeException(
                    String.format("Failed to get the StreamObjectHeaderEnd8bit type value, the value %s is not defined",
                            typeValue));
        }

        return 1;
    }
}