package org.apache.tika.parser.microsoft.fsshttpb;

public abstract class StreamObjectHeaderEnd extends BasicObject {
    /**
     * Gets or sets the type of the stream object.
     * value 1 for 8-bit stream object header start,
     * value 3 for 16-bit stream object header start.
     */
    StreamObjectTypeHeaderEnd type;
}