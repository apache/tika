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
package org.apache.tika.parser.image.xmp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Iterator;
import java.util.List;

import org.apache.jempbox.xmp.XMPMetadata;
import org.apache.jempbox.xmp.XMPSchemaDublinCore;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.xml.sax.InputSource;

public class JempboxExtractor {

    private XMPPacketScanner scanner = new XMPPacketScanner();
    
    private Metadata metadata;
    
    // The XMP spec says it must be unicode, but for most file formats it specifies "must be encoded in UTF-8"
    private static final String DEFAULT_XMP_CHARSET = "UTF-8";

    public JempboxExtractor(Metadata metadata) {
        this.metadata = metadata;
    }

    public void parse(InputStream file) throws IOException, TikaException {
        ByteArrayOutputStream xmpraw = new ByteArrayOutputStream();
        if (!scanner.parse(file, xmpraw)) {
            return;
        }

        Reader decoded = new InputStreamReader(
                new ByteArrayInputStream(xmpraw.toByteArray()),
                DEFAULT_XMP_CHARSET);
        try {
            XMPMetadata xmp = XMPMetadata.load(new InputSource(decoded));
            XMPSchemaDublinCore dc = xmp.getDublinCoreSchema();
            if (dc != null) {
                if (dc.getTitle() != null) {
                    metadata.set(TikaCoreProperties.TITLE, dc.getTitle());
                }
                if (dc.getDescription() != null) {
                    metadata.set(TikaCoreProperties.DESCRIPTION, dc.getDescription());
                }
                if (dc.getCreators() != null && dc.getCreators().size() > 0) {
                    metadata.set(TikaCoreProperties.CREATOR, joinCreators(dc.getCreators()));
                }
                if (dc.getSubjects() != null && dc.getSubjects().size() > 0) {
                    Iterator<String> keywords = dc.getSubjects().iterator();
                    while (keywords.hasNext()) {
                        metadata.add(TikaCoreProperties.KEYWORDS, keywords.next());
                    }
                    // TODO should we set KEYWORDS too?
                    // All tested photo managers set the same in Iptc.Application2.Keywords and Xmp.dc.subject
                }
            }
        } catch (IOException e) {
            // Could not parse embedded XMP metadata. That's not a serious
            // problem, so we'll just ignore the issue for now.
            // TODO: Make error handling like this configurable.
        }
    }

    protected String joinCreators(List<String> creators) {
        if (creators == null || creators.size() == 0) {
            return "";
        }
        if (creators.size() == 1) {
            return creators.get(0);
        }
        StringBuffer c = new StringBuffer();
        for (String s : creators) {
            c.append(", ").append(s);
        }
        return c.substring(2);
    }
}
