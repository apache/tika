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
package org.apache.tika.parser.crypto;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CloseShieldInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.EmptyParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.CMSSignedDataParser;
import org.bouncycastle.cms.CMSTypedStream;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Basic parser for PKCS7 data.
 */
public class Pkcs7Parser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = -7310531559075115044L;

    private static final MediaType PKCS7_MIME =
            MediaType.application("pkcs7-mime");

    private static final MediaType PKCS7_SIGNATURE =
            MediaType.application("pkcs7-signature");

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return MediaType.set(PKCS7_MIME, PKCS7_SIGNATURE);
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        try {
            CMSSignedDataParser parser =
                    new CMSSignedDataParser(new CloseShieldInputStream(stream));
            try {
                CMSTypedStream content = parser.getSignedContent();     
                if (content == null) {
                  throw new TikaException("cannot parse detached pkcs7 signature (no signed data to parse)");
                }
                InputStream input = content.getContentStream();
                try {
                    Parser delegate =
                            context.get(Parser.class, EmptyParser.INSTANCE);
                    delegate.parse(input, handler, metadata, context);
                } finally {
                    input.close();
                }
            } finally {
                parser.close();
            }
        } catch (CMSException e) {
            throw new TikaException("Unable to parse pkcs7 signed data", e);
        }
    }

}
