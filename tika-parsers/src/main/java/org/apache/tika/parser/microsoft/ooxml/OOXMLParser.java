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
package org.apache.tika.parser.microsoft.ooxml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Office Open XML (OOXML) parser.
 * 
 */
public class OOXMLParser implements Parser {

    private static final Set<MediaType> SUPPORTED_TYPES =
        Collections.unmodifiableSet(new HashSet<MediaType>(Arrays.asList(
                MediaType.application("x-tika-ooxml"),
                MediaType.application("vnd.openxmlformats-officedocument.presentationml.presentation"),
                MediaType.application("vnd.ms-powerpoint.presentation.macroenabled.12"),
                MediaType.application("vnd.openxmlformats-officedocument.presentationml.template"),
                MediaType.application("vnd.openxmlformats-officedocument.presentationml.slideshow"),
                MediaType.application("vnd.ms-powerpoint.slideshow.macroenabled.12"),
                MediaType.application("vnd.ms-powerpoint.addin.macroenabled.12"),
                MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                MediaType.application("vnd.ms-excel.sheet.macroenabled.12"),
                MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.template"),
                MediaType.application("vnd.ms-excel.template.macroenabled.12"),
                MediaType.application("vnd.ms-excel.addin.macroenabled.12"),
                MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document"),
                MediaType.application("vnd.ms-word.document.macroenabled.12"),
                MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.template"),
                MediaType.application("vnd.ms-word.template.macroenabled.12"))));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    public void parse(
            InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        Locale locale = context.get(Locale.class, Locale.getDefault());
        OOXMLExtractorFactory.parse(stream, handler, metadata, locale);
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
