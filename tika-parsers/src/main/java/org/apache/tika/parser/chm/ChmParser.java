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
package org.apache.tika.parser.chm;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class ChmParser extends AbstractParser {

    /** Serial version UID */
    private static final long serialVersionUID = 5938777307516469802L;

    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("chm"));

    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }


    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {
        CHMDocumentInformation chmInfo = CHMDocumentInformation.load(stream);
        metadata.set(Metadata.CONTENT_TYPE, "chm");
        extractMetadata(chmInfo, metadata);
        CHM2XHTML.process(chmInfo, handler);
    }

    private void extractMetadata(CHMDocumentInformation chmInfo,
            Metadata metadata) throws TikaException, IOException {
        chmInfo.getCHMDocInformation(metadata);
    }
}
