package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.List;

/// <summary>
/// An 16-bit header for a compound object would indicate the end of a stream object
/// </summary>
public class StreamObjectHeaderEnd16bit extends StreamObjectHeaderEnd {
    /// <summary>
    /// Initializes a new instance of the StreamObjectHeaderEnd16bit class with the specified type value.
    /// </summary>
    /// <param name="type">Specify the integer value of the type.</param>
    public StreamObjectHeaderEnd16bit(int type) {
        this.type = StreamObjectTypeHeaderEnd.fromIntVal(type);
        if (this.type == null) {
            throw new RuntimeException(String.format(
                    "The type value RuntimeException is not defined for the stream object end 16-bit header", type));
        }

    }

    /// <summary>
    /// Initializes a new instance of the StreamObjectHeaderEnd16bit class with the specified type value.
    /// </summary>
    /// <param name="headerType">Specify the value of the type.</param>
    public StreamObjectHeaderEnd16bit(StreamObjectTypeHeaderEnd headerType) {
        this.type = headerType;
    }

    /// <summary>
    /// Initializes a new instance of the StreamObjectHeaderEnd16bit class, this is the default constructor.
    /// </summary>
    public StreamObjectHeaderEnd16bit() {
    }

    /// <summary>
    /// This method is used to convert the element of StreamObjectHeaderEnd16bit basic object into a byte List.
    /// </summary>
    /// <returns>Return the byte list which store the byte information of StreamObjectHeaderEnd16bit.</returns>
    @Override
    public List<Byte> SerializeToByteList() {
        BitWriter bitFieldWriter = new BitWriter(2);
        bitFieldWriter.AppendInit32(0x3, 2);
        bitFieldWriter.AppendUInit32(this.type.getIntVal(), 14);
        return bitFieldWriter.getByteList();
    }

    /// <summary>
    /// This method is used to get the byte value of the 16-bit stream object header End.
    /// </summary>
    /// <returns>Return StreamObjectHeaderEnd8bit value represented by unsigned short integer.</returns>
    public short ToUint16() {
        List<Byte> bytes = this.SerializeToByteList();
        return LittleEndianBitConverter.ToUInt16(bytes.toArray(new Byte[0]), 0);
    }

    /// <summary>
    /// This method is used to deserialize the StreamObjectHeaderEnd16bit basic object from the specified byte array and start index.
    /// </summary>
    /// <param name="byteArray">Specify the byte array.</param>
    /// <param name="startIndex">Specify the start index from the byte array.</param>
    /// <returns>Return the length in byte of the StreamObjectHeaderEnd16bit basic object.</returns>
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) {
        BitReader reader = new BitReader(byteArray, startIndex);
        int headerType = reader.ReadInt32(2);

        if (headerType != 0x3) {
            throw new RuntimeException(String.format(
                    "Failed to get the StreamObjectHeaderEnd16bit header type value, expect value RuntimeException, but actual value is %s",
                    0x3, headerType));
        }

        int typeValue = reader.ReadUInt32(14);
        this.type = StreamObjectTypeHeaderEnd.fromIntVal(typeValue);
        if (this.type == null) {
            throw new RuntimeException(String.format(
                    "Failed to get the StreamObjectHeaderEnd16bit type value, the value RuntimeException is not defined",
                    typeValue));
        }

        return 2;
    }
}