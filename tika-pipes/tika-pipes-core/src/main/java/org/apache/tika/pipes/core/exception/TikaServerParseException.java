package org.apache.tika.pipes.core.exception;

public class TikaServerParseException extends RuntimeException {
    public TikaServerParseException() {
    }

    public TikaServerParseException(String message) {
        super(message);
    }

    public TikaServerParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public TikaServerParseException(Throwable cause) {
        super(cause);
    }

    public TikaServerParseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
