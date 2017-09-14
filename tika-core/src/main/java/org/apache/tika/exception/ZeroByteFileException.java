package org.apache.tika.exception;

/**
 * Exception thrown by the AutoDetectParser when a file contains zero-bytes.
 */
public class ZeroByteFileException extends TikaException {

    public ZeroByteFileException(String msg) {
        super(msg);
    }
}
