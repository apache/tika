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

package org.apache.tika.example;

import java.util.Collections;
import java.util.Set;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.xml.ElementMetadataHandler;
import org.apache.tika.parser.xml.XMLParser;
import org.apache.tika.sax.TeeContentHandler;
import org.xml.sax.ContentHandler;

public class PrescriptionParser extends XMLParser {
    private static final long serialVersionUID = 7690682277511967388L;

    @Override
    protected ContentHandler getContentHandler(ContentHandler handler,
                                               Metadata metadata, ParseContext context) {
        String xpd = "http://example.com/2011/xpd";

        ContentHandler doctor = new ElementMetadataHandler(xpd, "doctor",
                metadata, "xpd:doctor");
        ContentHandler patient = new ElementMetadataHandler(xpd, "patient",
                metadata, "xpd:patient");

        return new TeeContentHandler(super.getContentHandler(handler, metadata,
                context), doctor, patient);
    }

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return Collections.singleton(MediaType.application("x-prescription+xml"));
    }
}
