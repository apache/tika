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
package org.apache.tika.parser.mail;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Uses apache-mime4j to parse emails. Each part is treated with the
 * corresponding parser and displayed within elements.
 * 
 * @author jnioche@digitalpebble.com
 **/
public class RFC822Parser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.parse("message/rfc822"));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        MimeStreamParser parser = new MimeStreamParser();
        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

        MailContentHandler mch = new MailContentHandler(xhtml, metadata);
        parser.setContentHandler(mch);
        parser.setContentDecoding(true);
        try {
            parser.parse(stream);
        } catch (MimeException e) {
            throw new TikaException(e.getMessage());
        }
    }

    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata) throws IOException, SAXException, TikaException {
        parse(stream, handler, metadata, new ParseContext());
    }

}
