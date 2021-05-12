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
package org.apache.tika.parser.xliff;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.sax.XHTMLContentHandler;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Parser for XLIFF 1.2 files.
 */
public class XLIFF12Parser extends AbstractParser {

    /**
     * Serial Version UID.
     */
    private static final long serialVersionUID = 1490085649251663857L;

    /**
     * Pre-Xliff 2.0 mime type.
     */
    private static final MediaType XLF_CONTENT_TYPE = MediaType.application("x-xliff+xml");

    /**
     * Supported types set.
     */
    private static final Set<MediaType> SUPPORTED_TYPES = Collections.singleton(XLF_CONTENT_TYPE);

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        metadata.set(Metadata.CONTENT_TYPE, XLF_CONTENT_TYPE.toString());

        final XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

        XMLReaderUtils.parseSAX(new CloseShieldInputStream(stream),
                new OfflineContentHandler(new XLIFF12ContentHandler(xhtml, metadata)), context);

    }

}
