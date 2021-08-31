package org.apache.tika.parser.microsoft.fsshttpb;

public class StreamObjectParseErrorException extends RuntimeException {


    /**
     * Gets or sets index of object.
     */
    public int Index;

    /**
     * Gets or sets stream object type name.
     */
    public String StreamObjectTypeName;

    /**
     * Initializes a new instance of the StreamObjectParseErrorException class
     *
     * @param index                Specify the index of object
     * @param streamObjectTypeName Specify the stream type name
     * @param innerException       Specify the inner exception
     */
    public StreamObjectParseErrorException(int index, String streamObjectTypeName, Exception innerException) {
        super(innerException);
        this.Index = index;
        this.StreamObjectTypeName = streamObjectTypeName;
    }

    /**
     * Initializes a new instance of the StreamObjectParseErrorException class
     *
     * @param index                Specify the index of object
     * @param streamObjectTypeName Specify the stream type name
     * @param message              Specify the exception message
     * @param innerException       Specify the inner exception
     */
    public StreamObjectParseErrorException(int index, String streamObjectTypeName, String message,
                                           Exception innerException) {
        super(message, innerException);
        this.Index = index;
        this.StreamObjectTypeName = streamObjectTypeName;
    }
}