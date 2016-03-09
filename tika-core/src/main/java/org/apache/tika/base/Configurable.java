package org.apache.tika.base;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;

/**
 * Defines contract for configurable services
 * @since Apache Tika 1.13
 */
public interface Configurable {

    /**
     * Confure an instance with Tika Context
     * @param context configuration instance in the form of context
     * @throws TikaException when an instance fails to work at the given context
     * @since Apache Tika 1.13
     */
    void configure(ParseContext context) throws TikaException;
}
