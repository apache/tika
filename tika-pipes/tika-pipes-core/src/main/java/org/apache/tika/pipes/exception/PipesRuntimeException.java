package org.apache.tika.pipes.exception;

public class PipesRuntimeException extends RuntimeException {
    public PipesRuntimeException() {
    }

    public PipesRuntimeException(String message) {
        super(message);
    }

    public PipesRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public PipesRuntimeException(Throwable cause) {
        super(cause);
    }

    public PipesRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
