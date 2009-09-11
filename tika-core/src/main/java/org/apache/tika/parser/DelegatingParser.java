/*
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
import java.util.Collections;
import java.util.Map;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Base class for parser implementations that want to delegate parts of the
 * task of parsing an input document to another parser. The delegate parser
 * is looked up from the parsing context.
 * <p>
 * This class uses the following parsing context:
 * <dl>
 *   <dt>org.apache.tika.parser.Parser</dt>
 *   <dd>
 *     The delegate parser ({@link Parser} instance).
 *   </dd>
 * </dl>
 *
 * @since Apache Tika 0.4, major changes in Tika 0.5
 */
public class DelegatingParser implements Parser {

    /**
     * Looks up the delegate parser from the parsing context and
     * delegates the parse operation to it. If a delegate parser is not
     * found, then an empty XHTML document is returned.
     * <p>
     * Subclasses should override this method to parse the top level
     * structure of the given document stream. Parsed sub-streams can
     * be passed to this base class method to be parsed by the configured
     * delegate parser.
     */
    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, Map<String, Object> context)
            throws SAXException, IOException, TikaException {
        Object parser = context.get(Parser.class.getName());
        if (parser instanceof Parser) {
            ((Parser) parser).parse(stream, handler, metadata, context);
        } else {
            new EmptyParser().parse(stream, handler, metadata, context);
        }
    }

    /**
     * @deprecated This method will be removed in Apache Tika 1.0.
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        Map<String, Object> context = Collections.emptyMap();
        parse(stream, handler, metadata, context);
    }

}
