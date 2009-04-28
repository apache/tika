/**
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
package org.apache.tika.mime;

// Commons Logging imports
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

// DOM imports
import org.w3c.dom.Attr;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;
import org.xml.sax.InputSource;

// JDK imports
import java.io.InputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * A reader for XML files compliant with the freedesktop MIME-info DTD.
 * 
 * <pre>
 *  &lt;!DOCTYPE mime-info [
 *    &lt;!ELEMENT mime-info (mime-type)+&gt;
 *    &lt;!ATTLIST mime-info xmlns CDATA #FIXED &quot;http://www.freedesktop.org/standards/shared-mime-info&quot;&gt;
 * 
 *    &lt;!ELEMENT mime-type (comment|acronym|expanded-acronym|glob|magic|root-XML|alias|sub-class-of)*&gt;
 *    &lt;!ATTLIST mime-type type CDATA #REQUIRED&gt;
 * 
 *    &lt;!-- a comment describing a document with the respective MIME type. Example: &quot;WMV video&quot; --&gt;
 *    &lt;!ELEMENT comment (#PCDATA)&gt;
 *    &lt;!ATTLIST comment xml:lang CDATA #IMPLIED&gt;
 * 
 *    &lt;!-- a comment describing a the respective unexpanded MIME type acronym. Example: &quot;WMV&quot; --&gt;
 *    &lt;!ELEMENT acronym (#PCDATA)&gt;
 *    &lt;!ATTLIST acronym xml:lang CDATA #IMPLIED&gt;
 * 
 *    &lt;!-- a comment describing a the respective unexpanded MIME type acronym. Example: &quot;Windows Media Video&quot; --&gt;
 *    &lt;!ELEMENT expanded-acronym (#PCDATA)&gt;
 *    &lt;!ATTLIST expanded-acronym xml:lang CDATA #IMPLIED&gt;
 * 
 *    &lt;!ELEMENT glob EMPTY&gt;
 *    &lt;!ATTLIST glob pattern CDATA #REQUIRED&gt;
 *    &lt;!ATTLIST glob isregex CDATA #IMPLIED&gt;
 * 
 *    &lt;!ELEMENT magic (match)+&gt;
 *    &lt;!ATTLIST magic priority CDATA #IMPLIED&gt;
 * 
 *    &lt;!ELEMENT match (match)*&gt;
 *    &lt;!ATTLIST match offset CDATA #REQUIRED&gt;
 *    &lt;!ATTLIST match type (string|big16|big32|little16|little32|host16|host32|byte) #REQUIRED&gt;
 *    &lt;!ATTLIST match value CDATA #REQUIRED&gt;
 *    &lt;!ATTLIST match mask CDATA #IMPLIED&gt;
 * 
 *    &lt;!ELEMENT root-XML EMPTY&gt;
 *    &lt;!ATTLIST root-XML
 *          namespaceURI CDATA #REQUIRED
 *          localName CDATA #REQUIRED&gt;
 * 
 *    &lt;!ELEMENT alias EMPTY&gt;
 *    &lt;!ATTLIST alias
 *          type CDATA #REQUIRED&gt;
 * 
 *   &lt;!ELEMENT sub-class-of EMPTY&gt;
 *   &lt;!ATTLIST sub-class-of
 *         type CDATA #REQUIRED&gt;
 *  ]&gt;
 * </pre>
 * 
 * 
 * @see http://freedesktop.org/wiki/Standards_2fshared_2dmime_2dinfo_2dspec
 * 
 */
final class MimeTypesReader implements MimeTypesReaderMetKeys {

    /** The logger to use */
    private Log logger = null;

    private final MimeTypes types;

    MimeTypesReader(MimeTypes types) {
        this(types, null);
    }

    MimeTypesReader(MimeTypes types, Log logger) {
        this.types = types;
        if (logger == null) {
            this.logger = LogFactory.getLog(this.getClass());
        } else {
            this.logger = logger;
        }
    }

    void read(String filepath) {
        read(MimeTypesReader.class.getClassLoader().getResourceAsStream(filepath));
    }

    void read(InputStream stream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory
                    .newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(stream));
            read(document);
        } catch (Exception e) {
            if (logger.isWarnEnabled()) {
                logger.warn(e.toString() + " while loading mime-types");
            }
        }
    }

    void read(Document document) {
        Element element = document.getDocumentElement();
        if (element != null && element.getTagName().equals(MIME_INFO_TAG)) {
            NodeList nodes = element.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element child = (Element) node;
                    if (child.getTagName().equals(MIME_TYPE_TAG)) {
                        readMimeType(child);
                    }
                }
            }
        } else {
            logger.warn("Not a <"+MIME_INFO_TAG+"/> configuration document");
        }
    }

    /** Read Element named mime-type. */
    private void readMimeType(Element element) {
        String name = element.getAttribute(MIME_TYPE_TYPE_ATTR);
        try {
            MimeType type = types.forName(name);

            NodeList nodes = element.getChildNodes();
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element nodeElement = (Element) node;
                    if (nodeElement.getTagName().equals(COMMENT_TAG)) {
                        type.setDescription(
                                nodeElement.getFirstChild().getNodeValue());
                    } else if (nodeElement.getTagName().equals(GLOB_TAG)) {
                        boolean useRegex = Boolean.valueOf(nodeElement.getAttribute(ISREGEX_ATTR));
                        types.addPattern(type, nodeElement.getAttribute(PATTERN_ATTR), useRegex);
                    } else if (nodeElement.getTagName().equals(MAGIC_TAG)) {
                        readMagic(nodeElement, type);
                    } else if (nodeElement.getTagName().equals(ALIAS_TAG)) {
                        String alias = nodeElement.getAttribute(ALIAS_TYPE_ATTR);
                        try {
                            type.addAlias(alias);
                        } catch (MimeTypeException e) {
                            logger.warn("Invalid media type alias: " + alias, e);
                        }
                    } else if (nodeElement.getTagName().equals(ROOT_XML_TAG)) {
                        readRootXML(nodeElement, type);
                    } else if (nodeElement.getTagName().equals(SUB_CLASS_OF_TAG)) {
                        String parent = nodeElement.getAttribute(SUB_CLASS_TYPE_ATTR);
                        try {
                            type.setSuperType(types.forName(parent));
                        } catch (MimeTypeException e) {
                            logger.warn("Invalid parent type: " + parent, e);
                        }
                    }
                }
            }

            types.add(type);
        } catch (MimeTypeException e) {
            logger.warn("Invalid media type configuration entry: " + name, e);
        }
    }

    /** Read Element named magic. */
    private void readMagic(Element element, MimeType mimeType) {
        Magic magic = null;
        try {
            magic = new Magic(Integer
                    .parseInt(element.getAttribute(MAGIC_PRIORITY_ATTR)));
        } catch (Exception e) {
            magic = new Magic();
        }
        magic.setType(mimeType);
        magic.setClause(readMatches(element));
        mimeType.addMagic(magic);
    }

    private Clause readMatches(Element element) {
        Clause sub = null;
        Clause prev = Clause.FALSE;
        Clause clause = null;
        NodeList nodes = element.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element nodeElement = (Element) node;
                if (nodeElement.getTagName().equals(MATCH_TAG)) {
                    sub = readMatches(nodeElement);
                    try {
                        if (sub != null) {
                            clause = new MagicClause(Operator.AND,
                                    readMatch(nodeElement), sub);
                        } else {
                            clause = readMatch(nodeElement);
                        }
                        clause = new MagicClause(Operator.OR, prev, clause);
                        prev = clause;
                    } catch (MimeTypeException mte) {
                        logger.warn(mte + " while reading magic-match ["
                                + nodeElement + "], Ignoring!");
                    }
                }
            }
        }
        return clause;
    }

    /** Read Element named match. */
    private MagicMatch readMatch(Element element) throws MimeTypeException {

        String offset = null;
        String value = null;
        String mask = null;
        String type = null;

        NamedNodeMap attrs = element.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Attr attr = (Attr) attrs.item(i);
            if (attr.getName().equals(MATCH_OFFSET_ATTR)) {
                offset = attr.getValue();
            } else if (attr.getName().equals(MATCH_TYPE_ATTR)) {
                type = attr.getValue();
            } else if (attr.getName().equals(MATCH_VALUE_ATTR)) {
                value = attr.getValue();
            } else if (attr.getName().equals(MATCH_MASK_ATTR)) {
                mask = attr.getValue();
            }
        }
        // Parse OffSet
        String[] offsets = offset.split(":");
        int offStart = 0;
        int offEnd = 0;
        try {
            offStart = Integer.parseInt(offsets[0]);
        } catch (Exception e) {
            // WARN log + avoid loading
        }
        try {
            offEnd = Integer.parseInt(offsets[1]);
        } catch (Exception e) {
            // WARN log
        }
        offEnd = Math.max(offStart, offEnd);

        return new MagicMatch(offStart, offEnd, type, mask, value);
    }

    /** Read Element named root-XML. */
    private void readRootXML(Element element, MimeType mimeType) {
        mimeType.addRootXML(element.getAttribute(NS_URI_ATTR), element
                .getAttribute(LOCAL_NAME_ATTR));
    }

}
