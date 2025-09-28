package org.apache.tika.pipes.core.emitter;

public class TikaEmitterException extends RuntimeException {
    public TikaEmitterException(String message) {
        super(message);
    }

    public TikaEmitterException(String message, Throwable cause) {
        super(message, cause);
    }
}
