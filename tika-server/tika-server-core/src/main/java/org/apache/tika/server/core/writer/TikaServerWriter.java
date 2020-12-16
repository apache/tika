package org.apache.tika.server.core.writer;

import javax.ws.rs.ext.MessageBodyWriter;

/**
 * stub interface to allow for SPI loading from other modules
 * without opening up service loading to any generic MessageBodyWriter
 */
public interface TikaServerWriter<T> extends MessageBodyWriter<T> {
}
