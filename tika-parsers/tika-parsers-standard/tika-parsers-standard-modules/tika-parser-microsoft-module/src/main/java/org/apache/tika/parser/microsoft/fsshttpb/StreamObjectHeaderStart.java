package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.concurrent.atomic.AtomicReference;

/// <summary>
/// This class specifies the base class for 16-bit or 32-bit stream object header start.
/// </summary>
public abstract class StreamObjectHeaderStart extends BasicObject {
    /// <summary>
    /// Specify for 16-bit stream object header start.
    /// </summary>
    public static final int StreamObjectHeaderStart16bit = 0x0;

    /// <summary>
    /// Specify for 32-bit stream object header start.
    /// </summary>
    public static final int StreamObjectHeaderStart32bit = 0x02;

    /// <summary>
    /// Initializes a new instance of the StreamObjectHeaderStart class.
    /// </summary>
    protected StreamObjectHeaderStart() {
    }

    /// <summary>
    /// Initializes a new instance of the StreamObjectHeaderStart class with specified header type.
    /// </summary>
    /// <param name="streamObjectTypeHeaderStart">Specify the value of the StreamObjectHeaderStart Type.</param>
    protected StreamObjectHeaderStart(StreamObjectTypeHeaderStart streamObjectTypeHeaderStart) {
        this.type = streamObjectTypeHeaderStart;
    }

    /// <summary>
    /// Gets or sets the type of the stream object.
    /// value 0 for 16-bit stream object header start,
    /// value 2 for 32-bit stream object header start.
    /// </summary>
    protected int headerType;

    /// <summary>
    /// Gets or sets a value that specifies if set a compound parse type is needed and
    /// MUST be ended with either an 8-bit stream object header end or a 16-bit stream object header end.
    /// If the bit is zero, it specifies a single object. Otherwise it specifies a compound object.
    /// </summary>
    protected int compound;

    /// <summary>
    /// Gets or sets a value that specifies the stream object type.
    /// </summary>
    protected StreamObjectTypeHeaderStart type;

    /// <summary>
    /// Gets or sets a 15-bit unsigned integer that specifies the length in bytes for additional data (if any).
    /// </summary>
    protected int length;

    /// <summary>
    /// This method is used to parse the actual 16bit or 32bit stream header.
    /// </summary>
    /// <param name="byteArray">Specify the Byte array.</param>
    /// <param name="startIndex">Specify the start position.</param>
    /// <param name="streamObjectHeader">Specify the out value for the parse result.</param>
    /// <returns>Return true if success, otherwise returns false. </returns>
    public static int TryParse(byte[] byteArray, int startIndex,
                               AtomicReference<StreamObjectHeaderStart> streamObjectHeader) {
        int headerType = byteArray[startIndex] & 0x03;
        if (headerType == StreamObjectHeaderStart.StreamObjectHeaderStart16bit) {
            streamObjectHeader.set(new StreamObjectHeaderStart16bit());
        } else {
            if (headerType == StreamObjectHeaderStart.StreamObjectHeaderStart32bit) {
                streamObjectHeader.set(new StreamObjectHeaderStart32bit());
            } else {
                streamObjectHeader = null;
                return 0;
            }
        }

        try {
            return streamObjectHeader.get().DeserializeFromByteArray(byteArray, startIndex);
        } catch (Exception e) {
            streamObjectHeader = null;
            return 0;
        }
    }
}