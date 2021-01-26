package org.apache.tika.server.client;

import org.apache.tika.exception.TikaException;

public class TikaClientConfigException extends TikaException {
    public TikaClientConfigException(String msg) {
        super(msg);
    }

    public TikaClientConfigException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
