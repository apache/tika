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
package org.apache.tika.mime;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
 *    &lt;!ELEMENT _comment (#PCDATA)&gt;
 *    &lt;!ATTLIST _comment xml:lang CDATA #IMPLIED&gt;
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

    private final MimeTypes types;

    MimeTypesReader(MimeTypes types) {
        this.types = types;
    }

    void read(InputStream stream) throws IOException, MimeTypeException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(stream));
            read(document);
        } catch (ParserConfigurationException e) {
            throw new MimeTypeException("Unable to create an XML parser", e);
        } catch (SAXException e) {
            throw new MimeTypeException("Invalid type configuration", e);
        }
    }

    void read(Document document) throws MimeTypeException {
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
            throw new MimeTypeException(
                    "Not a <" + MIME_INFO_TAG + "/> configuration document: "
                    + element.getTagName());
        }
    }

    /** Read Element named mime-type. */
    private void readMimeType(Element element) throws MimeTypeException {
        String name = element.getAttribute(MIME_TYPE_TYPE_ATTR);
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
                    MediaType aliasType = MediaType.parse(alias);
                    if (aliasType != null) {
                        types.addAlias(type, aliasType);
                    } else {
                        throw new MimeTypeException(
                                "Invalid media type alias: " + alias);
                    }
                } else if (nodeElement.getTagName().equals(ROOT_XML_TAG)) {
                    readRootXML(nodeElement, type);
                } else if (nodeElement.getTagName().equals(SUB_CLASS_OF_TAG)) {
                    String parent = nodeElement.getAttribute(SUB_CLASS_TYPE_ATTR);
                    types.setSuperType(type, MediaType.parse(parent));
                }
            }
        }

        types.add(type);
    }

    /**
     * Read Element named magic. 
     * @throws MimeTypeException if the configuration is invalid
     */
    private void readMagic(Element element, MimeType mimeType)
            throws MimeTypeException {
        int priority = 50;
        String value = element.getAttribute(MAGIC_PRIORITY_ATTR);
        if (value != null && value.length() > 0) {
            priority = Integer.parseInt(value);
        }

        for (Clause clause : readMatches(element, mimeType.getType())) {
            Magic magic = new Magic(mimeType, priority, clause);
            mimeType.addMagic(magic);
        }
    }

    private List<Clause> readMatches(Element element, MediaType mediaType) throws MimeTypeException {
        NodeList nodes = element.getChildNodes();
        int n = nodes.getLength();
        if (n == 0) {
            return Collections.emptyList();
        }

        List<Clause> clauses = new ArrayList<Clause>();
        for (int i = 0; i < n; i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element nodeElement = (Element) node;
                if (nodeElement.getTagName().equals(MATCH_TAG)) {
                    clauses.add(readMatch(nodeElement, mediaType));
                }
            }
        }
        return clauses;
    }

    /** Read Element named match. */
    private Clause readMatch(Element element, MediaType mediaType) throws MimeTypeException {
        Clause clause = getMagicClause(element, mediaType);

        List<Clause> subClauses = readMatches(element, mediaType);
        if (subClauses.size() == 0) {
            return clause;
        } else if (subClauses.size() == 1) {
            return new AndClause(clause, subClauses.get(0));
        } else {
            return new AndClause(clause, new OrClause(subClauses));
        }
    }

    private Clause getMagicClause(Element element, MediaType mediaType)
            throws MimeTypeException {
        String type = "string";
        String offset = null;
        String value = null;
        String mask = null;

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

        return new MagicMatch(mediaType, type, offset, value, mask);
    }

    /** Read Element named root-XML. */
    private void readRootXML(Element element, MimeType mimeType) {
        mimeType.addRootXML(element.getAttribute(NS_URI_ATTR), element
                .getAttribute(LOCAL_NAME_ATTR));
    }

}
