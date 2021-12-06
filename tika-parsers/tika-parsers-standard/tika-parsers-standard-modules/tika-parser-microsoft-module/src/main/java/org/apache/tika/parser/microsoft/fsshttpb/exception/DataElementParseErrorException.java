package org.apache.tika.parser.microsoft.fsshttpb.exception;

public class DataElementParseErrorException extends RuntimeException {

    private int index;

    public DataElementParseErrorException(int index, Exception innerException) {
        super(innerException);
        this.index = index;
    }

    public DataElementParseErrorException(int index, String msg, Exception innerException) {
        super(msg, innerException);
        this.index = index;
    }
}
