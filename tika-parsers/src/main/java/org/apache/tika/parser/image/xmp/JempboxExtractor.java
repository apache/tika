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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Calendar;
import java.util.List;

import org.apache.jempbox.xmp.ResourceEvent;
import org.apache.jempbox.xmp.ResourceRef;
import org.apache.jempbox.xmp.XMPMetadata;
import org.apache.jempbox.xmp.XMPSchemaDublinCore;
import org.apache.jempbox.xmp.XMPSchemaMediaManagement;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.XMPMM;
import org.apache.tika.utils.DateUtils;
import org.xml.sax.InputSource;

public class JempboxExtractor {

    // The XMP spec says it must be unicode, but for most file formats it specifies "must be encoded in UTF-8"
    private static final String DEFAULT_XMP_CHARSET = UTF_8.name();
    private XMPPacketScanner scanner = new XMPPacketScanner();
    private Metadata metadata;

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
        XMPMetadata xmp = null;
        try {
            xmp = XMPMetadata.load(new InputSource(decoded));
        } catch (IOException e) {
            //
        }

        if (xmp == null) {
            return;
        }
        XMPSchemaDublinCore dc = null;
        try {
            dc = xmp.getDublinCoreSchema();
        } catch (IOException e) {
        }

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
                for (String keyword : dc.getSubjects()) {
                    metadata.add(TikaCoreProperties.KEYWORDS, keyword);
                }
                // TODO should we set KEYWORDS too?
                // All tested photo managers set the same in Iptc.Application2.Keywords and Xmp.dc.subject
            }
        }
        extractXMPMM(xmp, metadata);
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

    /**
     * Extracts Media Management metadata from XMP.
     *
     * Silently swallows exceptions.
     * @param xmp
     * @param metadata
     */
    public static void extractXMPMM(XMPMetadata xmp, Metadata metadata) {
        XMPSchemaMediaManagement mmSchema = null;
        try {
            mmSchema = xmp.getMediaManagementSchema();
        } catch (IOException e) {
            //swallow
            return;
        }
        if (mmSchema != null) {
            addMetadata(metadata, XMPMM.DOCUMENTID, mmSchema.getDocumentID());
            //not currently supported by JempBox...
//          metadata.set(XMPMM.INSTANCEID, mmSchema.getInstanceID());

            ResourceRef derivedFrom = mmSchema.getDerivedFrom();
            if (derivedFrom != null) {
                try {
                    addMetadata(metadata, XMPMM.DERIVED_FROM_DOCUMENTID, derivedFrom.getDocumentID());
                } catch (NullPointerException e) {}

                try {
                    addMetadata(metadata, XMPMM.DERIVED_FROM_INSTANCEID, derivedFrom.getInstanceID());
                } catch (NullPointerException e) {}

                //TODO: not yet supported by XMPBox...extract OriginalDocumentID
                //in DerivedFrom section
            }
            if (mmSchema.getHistory() != null) {
                for (ResourceEvent stevt : mmSchema.getHistory()) {
                    String instanceId = null;
                    String action = null;
                    Calendar when = null;
                    String softwareAgent = null;
                    try {
                        instanceId = stevt.getInstanceID();
                        action = stevt.getAction();
                        when = stevt.getWhen();
                        softwareAgent = stevt.getSoftwareAgent();

                        //instanceid can throw npe; getWhen can throw IOException
                    } catch (NullPointerException|IOException e) {
                       //swallow
                    }
                    if (instanceId != null && instanceId.trim().length() > 0) {
                        //for absent data elements, pass in empty strings so
                        //that parallel arrays will have matching offsets
                        //for absent data

                        action = (action == null) ? "" : action;
                        String dateString = (when == null) ? "" : DateUtils.formatDate(when);
                        softwareAgent = (softwareAgent == null) ? "" : softwareAgent;

                        metadata.add(XMPMM.HISTORY_EVENT_INSTANCEID, instanceId);
                        metadata.add(XMPMM.HISTORY_ACTION, action);
                        metadata.add(XMPMM.HISTORY_WHEN, dateString);
                        metadata.add(XMPMM.HISTORY_SOFTWARE_AGENT, softwareAgent);
                    }
                }
            }
        }
    }

    private static void addMetadata(Metadata m, Property p, String value) {
        if (value != null) {
            m.add(p, value);
        }
    }
}
