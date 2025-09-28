package org.apache.tika.pipes.core.exception;

public class TikaPipesException extends RuntimeException {
    public TikaPipesException() {
    }

    public TikaPipesException(String message) {
        super(message);
    }

    public TikaPipesException(String message, Throwable cause) {
        super(message, cause);
    }

    public TikaPipesException(Throwable cause) {
        super(cause);
    }

    public TikaPipesException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
