/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Decorator base class for the {@link Parser} interface. This class
 * simply delegates all parsing calls to an underlying decorated parser
 * instance. Subclasses can provide extra decoration by overriding the
 * parse method.
 */
public class ParserDecorator implements Parser {

    /**
     * The decorated parser instance.
     */
    private final Parser parser;

    /**
     * Creates a decorator for the given parser.
     *
     * @param parser the parser instance to be decorated
     */
    public ParserDecorator(Parser parser) {
        this.parser = parser;
    }

    /**
     * Delegates the method call to the decorated parser. Subclasses should
     * override this method (and use <code>super.getSupportedTypes()</code>
     * to invoke the decorated parser) to implement extra decoration.
     */
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return parser.getSupportedTypes(context);
    }

    /**
     * Delegates the method call to the decorated parser. Subclasses should
     * override this method (and use <code>super.parse()</code> to invoke
     * the decorated parser) to implement extra decoration.
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        parser.parse(stream, handler, metadata, context);
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

}
