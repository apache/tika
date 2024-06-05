package org.apache.tika.serialization;

import org.apache.tika.exception.TikaException;

public class TikaSerializationException extends TikaException {

    public TikaSerializationException(String msg) {
        super(msg);
    }

    public TikaSerializationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
