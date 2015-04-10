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
package org.apache.tika.parser.rtf;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TaggedInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * RTF parser
 */
public class RTFParser extends AbstractParser {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = -4165069489372320313L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("rtf"));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * maximum number of bytes per embedded object/pict (default: 20MB)
     */
    private static int EMB_OBJ_MAX_BYTES = 20 * 1024 * 1024; //20MB

    /**
     * Bytes for embedded objects are currently cached in memory.
     * If something goes wrong during the parsing of an embedded object,
     * it is possible that a read length may be crazily too long
     * and cause a heap crash.
     *
     * @param max maximum number of bytes to allow for embedded objects.  If
     *            the embedded object has more than this number of bytes, skip it.
     */
    public static void setMaxBytesForEmbeddedObject(int max) {
        EMB_OBJ_MAX_BYTES = max;
    }

    /**
     * See {@link #setMaxBytesForEmbeddedObject(int)}.
     *
     * @return maximum number of bytes allowed for an embedded object.
     */
    public static int getMaxBytesForEmbeddedObject() {
        return EMB_OBJ_MAX_BYTES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
        throws IOException, SAXException, TikaException {
        TaggedInputStream tagged = new TaggedInputStream(stream);
        try {
            XHTMLContentHandler xhtmlHandler = new XHTMLContentHandler(handler, metadata);
            RTFEmbObjHandler embObjHandler = new RTFEmbObjHandler(xhtmlHandler, metadata, context);
            final TextExtractor ert = new TextExtractor(xhtmlHandler, metadata, embObjHandler);
            ert.extract(stream);
            metadata.add(Metadata.CONTENT_TYPE, "application/rtf");
        } catch (IOException e) {
            tagged.throwIfCauseOf(e);
            throw new TikaException("Error parsing an RTF document", e);
        }
    }
}
