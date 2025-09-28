package org.apache.tika.pipes.core.iterators;

public class TikaPipeIteratorException extends RuntimeException {
    public TikaPipeIteratorException(String message) {
        super(message);
    }

    public TikaPipeIteratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
