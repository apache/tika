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
package org.apache.tika.parser.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.io.input.CloseShieldInputStream;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.OfflineContentHandler;
import org.apache.tika.utils.XMLReaderUtils;


/**
 * <p>
 * <p>
 * This parser enables profiling of XML.  It captures the root entity as well as
 * entity uris/namespaces and entity local names in parallel arrays.
 * </p>
 * <p>
 * <p>
 * This parser is not part of the default set of parsers and must be "turned on"
 * via a tika config:
 * <p>
 * &lt;properties&gt;
 * &lt;parsers&gt;
 * &lt;parser class="org.apache.tika.parser.DefaultParser"/&gt;
 * &lt;parser class="org.apache.tika.parser.xml.XMLProfiler"/&gt;
 * &lt;/parsers&gt;
 * &lt;/properties&gt;
 * </p>
 * <p>
 * This was initially designed to profile xmp and xfa in PDFs.  Further
 * work would need to be done to extract other types of xml and/or
 * xmp in other file formats.  Please open a ticket.
 * </p>
 */
public class XMLProfiler extends AbstractParser {


    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(MediaType.application("xml"),
                    //https://wwwimages2.adobe.com/content/dam/acom/en/devnet/xmp/pdfs/XMP%20SDK%20Release%20cc-2016-08/XMPSpecificationPart3.pdf
                    //"If a MIME type is needed, use application/rdf+xml."
                    MediaType.application("rdf+xml"),//xmp
                    //xfa: https://en.wikipedia.org/wiki/XFA
                    MediaType.application("vnd.adobe.xdp+xml"))));
    public static final Property ROOT_ENTITY = Property.internalText("xmlprofiler:root_entity");
    public static final Property ENTITY_URIS = Property.internalTextBag("xmlprofiler:entity_uris");
    public static final Property ENTITY_LOCAL_NAMES =
            Property.internalTextBag("xmlprofiler:entity_local_names");

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        XMLReaderUtils.parseSAX(new CloseShieldInputStream(stream),
                new OfflineContentHandler(new XMLProfileHandler(metadata)), context);
    }

    private static class XMLProfileHandler extends DefaultHandler {
        private final Metadata metadata;

        int starts = 0;

        Map<String, Set> entities = new TreeMap<>();

        public XMLProfileHandler(Metadata metadata) {
            this.metadata = metadata;
        }

        static String joinWith(String delimiter, Collection<String> strings) {
            StringBuilder sb = new StringBuilder();
            int i = 0;
            for (String s : strings) {
                if (i > 0) {
                    sb.append(delimiter);
                }
                sb.append(s);
                i++;
            }
            return sb.toString();
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts)
                throws SAXException {
            if (starts == 0) {
                metadata.set(ROOT_ENTITY, qName);
            }
            Set<String> localNames = entities.computeIfAbsent(uri, k -> new TreeSet<>());
            localNames.add(localName);
            starts++;
        }

        @Override
        public void endDocument() throws SAXException {
            String[] uris = new String[entities.size()];
            String[] localNames = new String[entities.size()];
            int i = 0;
            for (Map.Entry<String, Set> e : entities.entrySet()) {
                uris[i] = e.getKey();
                localNames[i] = joinWith(" ", e.getValue());
                i++;
            }
            metadata.set(ENTITY_URIS, uris);
            metadata.set(ENTITY_LOCAL_NAMES, localNames);
        }
    }
}
