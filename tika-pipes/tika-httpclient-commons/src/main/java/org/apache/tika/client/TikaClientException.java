package org.apache.tika.client;


import org.apache.tika.exception.TikaException;

public class TikaClientException extends TikaException {
    public TikaClientException(String msg) {
        super(msg);
    }

    public TikaClientException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
