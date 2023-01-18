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
package org.apache.tika.parser.odf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Parser for ODF <code>content.xml</code> files.
 */
public class OpenDocumentContentParser extends AbstractParser {

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.emptySet(); // not a top-level parser
    }

    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        parseInternal(stream, new XHTMLContentHandler(handler, metadata), metadata, context);
    }

    void parseInternal(InputStream stream, final ContentHandler handler, Metadata metadata,
                       ParseContext context) throws IOException, SAXException, TikaException {

        DefaultHandler dh = new OpenDocumentBodyHandler(handler, context);


        XMLReaderUtils.parseSAX(new CloseShieldInputStream(stream),
                new NSNormalizerContentHandler(dh), context);
    }

}
