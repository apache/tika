package org.apache.tika.parser.microsoft.fsshttpb;

public class StreamObjectParseErrorException extends RuntimeException {


    /// <summary>
    /// Gets or sets index of object.
    /// </summary>
    public int Index;

    /// <summary>
    /// Gets or sets stream object type name.
    /// </summary>
    public String StreamObjectTypeName;

    /// <summary>
    /// Initializes a new instance of the StreamObjectParseErrorException class
    /// </summary>
    /// <param name="index">Specify the index of object</param>
    /// <param name="streamObjectTypeName">Specify the stream type name</param>
    /// <param name="innerException">Specify the inner exception</param>
    public StreamObjectParseErrorException(int index, String streamObjectTypeName, Exception innerException) {
        super(innerException);
        this.Index = index;
        this.StreamObjectTypeName = streamObjectTypeName;
    }

    /// <summary>
    /// Initializes a new instance of the StreamObjectParseErrorException class
    /// </summary>
    /// <param name="index">Specify the index of object</param>
    /// <param name="streamObjectTypeName">Specify the stream type name</param>
    /// <param name="message">Specify the exception message</param>
    /// <param name="innerException">Specify the inner exception</param>
    public StreamObjectParseErrorException(int index, String streamObjectTypeName, String message,
                                           Exception innerException) {
        super(message, innerException);
        this.Index = index;
        this.StreamObjectTypeName = streamObjectTypeName;
    }
}