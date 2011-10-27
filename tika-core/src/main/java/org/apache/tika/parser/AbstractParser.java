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

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Abstract base class for new parsers. This method implements the old
 * deprecated parse method so subclasses won't have to.
 *
 * @since Apache Tika 0.10
 */
public abstract class AbstractParser implements Parser {

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = 7186985395903074255L;

    /**
     * Calls the
     * {@link Parser#parse(InputStream, ContentHandler, Metadata, ParseContext)}
     * method with an empty {@link ParseContext}. This method exists as a
     * leftover from Tika 0.x when the three-argument parse() method still
     * existed in the {@link Parser} interface. No new code should call this
     * method anymore, it's only here for backwards compatibility.
     *
     * @deprecated use the {@link Parser#parse(InputStream, ContentHandler, Metadata, ParseContext)} method instead
     */
    public void parse(
            InputStream stream, ContentHandler handler, Metadata metadata)
            throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

}
