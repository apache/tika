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
package org.apache.tika.sax;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Content handler decorator that simplifies the task of producing XMP output.
 *
 * @since Apache Tika 1.0
 */
public class XMPContentHandler extends SafeContentHandler {

    /**
     * The RDF namespace URI
     */
    public static final String RDF =
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#";

    /**
     * The XMP namespace URI
     */
    public static final String XMP =
            "http://ns.adobe.com/xap/1.0/";

    private static final Attributes EMPTY_ATTRIBUTES = new AttributesImpl();

    public XMPContentHandler(ContentHandler handler) {
        super(handler);
    }

    /**
     * Starts an XMP document by setting up the namespace mappings and
     * writing out the following header:
     * <pre>
     * &lt;rdf:RDF&gt;
     * </pre>
     */
    @Override
    public void startDocument() throws SAXException {
        super.startDocument();

        startPrefixMapping("rdf", RDF);
        startPrefixMapping("xmp", XMP);

        startElement(RDF, "RDF", "rdf:RDF", EMPTY_ATTRIBUTES);
    }

    /**
     * Ends the XMP document by writing the following footer and
     * clearing the namespace mappings:
     * <pre>
     * &lt;/rdf:RDF&gt;
     * </pre>
     */
    @Override
    public void endDocument() throws SAXException {
        endElement(RDF, "RDF", "rdf:RDF");

        endPrefixMapping("xmp");
        endPrefixMapping("rdf");

        super.endDocument();
    }

    //------------------------------------------< public convenience methods >

    private String prefix = null;

    private String uri = null;

    public void startDescription(String about, String prefix, String uri)
            throws SAXException {
        this.prefix = prefix;
        this.uri = uri;

        startPrefixMapping(prefix, uri);
        AttributesImpl attributes = new AttributesImpl();
        attributes.addAttribute(RDF, "about", "rdf:about", "CDATA", about);
        startElement(RDF, "Description", "rdf:Description", attributes);
    }

    public void endDescription() throws SAXException {
        endElement(RDF, "Description", "rdf:Description");
        endPrefixMapping(prefix);

        this.uri = null;
        this.prefix = null;
    }

    public void property(String name, String value) throws SAXException {
        String qname = prefix + ":" + name;
        startElement(uri, name, qname, EMPTY_ATTRIBUTES);
        characters(value.toCharArray(), 0, value.length());
        endElement(uri, name, qname);
    }

    public void metadata(Metadata metadata) throws SAXException {
        description(metadata, "xmp", XMP);
        description(metadata, "dc", "http://purl.org/dc/elements/1.1/");
        description(metadata, "xmpTPg", "http://ns.adobe.com/xap/1.0/t/pg/");
        description(metadata, "xmpRigths", "http://ns.adobe.com/xap/1.0/rights/");
        description(metadata, "xmpMM", "http://ns.adobe.com/xap/1.0/mm/");
        description(metadata, "xmpidq", "http://ns.adobe.com/xmp/identifier/qual/1.0/");
        description(metadata, "xmpBJ", "http://ns.adobe.com/xap/1.0/bj/");
        description(metadata, "xmpDM", "http://ns.adobe.com/xmp/1.0/DynamicMedia/");
        description(metadata, "pdf", "http://ns.adobe.com/pdf/1.3/");
        description(metadata, "photoshop", "s http://ns.adobe.com/photoshop/1.0/");
        description(metadata, "crs", "http://ns.adobe.com/camera-raw-settings/1.0/");
        description(metadata, "tiff", "http://ns.adobe.com/tiff/1.0/");
        description(metadata, "exif", "http://ns.adobe.com/exif/1.0/");
        description(metadata, "aux", "http://ns.adobe.com/exif/1.0/aux/");
    }

    private void description(Metadata metadata, String prefix, String uri)
            throws SAXException {
        int count = 0;
        for (Property property : Property.getProperties(prefix)) {
            String value = metadata.get(property);
            if (value != null) {
                if (count++ == 0) {
                    startDescription("", prefix, uri);
                }
                property(property.getName().substring(prefix.length() + 1), value);
            }
        }

        if (count > 0) {
            endDescription();
        }
    }

}
