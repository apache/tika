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
import java.util.Set;

import org.apache.tika.config.LoadErrorHandler;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This parser is a proxy for another detector this allows modules to use
 * parsers from other modules as optional dependencies since not including the
 * classes simply does nothing rather than throwing a ClassNotFoundException.
 *
 * @since Apache Tika 2.0
 */
public class ParserProxy extends AbstractParser {

    private static final long serialVersionUID = -4838436708916910179L;
    private Parser parser;

    public ParserProxy(String parserClassName, ClassLoader loader) {
        
        this(parserClassName, loader, Boolean.getBoolean("org.apache.tika.service.proxy.error.warn") 
                ? LoadErrorHandler.WARN:LoadErrorHandler.IGNORE);
    }

    public ParserProxy(String parserClassName, ClassLoader loader, LoadErrorHandler handler) {
        try {
            this.parser = (Parser) Class.forName(parserClassName, true, loader).newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            handler.handleLoadError(parserClassName, e);
        }

    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        if (parser == null) {
            return Collections.emptySet();
        }
        return parser.getSupportedTypes(context);
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        if (parser != null) {
            parser.parse(stream, handler, metadata, context);
        }
        // Otherwise do nothing
    }
}
