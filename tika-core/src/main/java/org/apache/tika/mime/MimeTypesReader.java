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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

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
 * @see http://freedesktop.org/wiki/Standards_2fshared_2dmime_2dinfo_2dspec
 */
class MimeTypesReader extends DefaultHandler implements MimeTypesReaderMetKeys {

    private final MimeTypes types;

    /** Current type */
    private MimeType type = null;

    private int priority;

    private Clause clause = null;

    private List<Clause> clauses = new LinkedList<Clause>();

    private final LinkedList<List<Clause>> clauseStack =
            new LinkedList<List<Clause>>();

    private final StringBuilder characters = new StringBuilder();

    MimeTypesReader(MimeTypes types) {
        this.types = types;
    }

    void read(InputStream stream) throws IOException, MimeTypeException {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(false);
            SAXParser parser = factory.newSAXParser();
            parser.parse(stream, this);
        } catch (ParserConfigurationException e) {
            throw new MimeTypeException("Unable to create an XML parser", e);
        } catch (SAXException e) {
            throw new MimeTypeException("Invalid type configuration", e);
        }
    }

    void read(Document document) throws MimeTypeException {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            Transformer transformer = factory.newTransformer();
            transformer.transform(new DOMSource(document), new SAXResult(this));
        } catch (TransformerException e) {
            throw new MimeTypeException("Failed to parse type registry", e);
        }
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        return new InputSource(new ByteArrayInputStream(new byte[0]));
    }

    @Override
    public void startElement(
            String uri, String localName, String qName,
            Attributes attributes) throws SAXException {
        if (type == null) {
            if (MIME_TYPE_TAG.equals(qName)) {
                String name = attributes.getValue(MIME_TYPE_TYPE_ATTR);
                try {
                    type = types.forName(name);
                } catch (MimeTypeException e) {
                    throw new SAXException(e);
                }
            }
        } else if (ALIAS_TAG.equals(qName)) {
            String alias = attributes.getValue(ALIAS_TYPE_ATTR);
            types.addAlias(type, MediaType.parse(alias));
        } else if (SUB_CLASS_OF_TAG.equals(qName)) {
            String parent = attributes.getValue(SUB_CLASS_TYPE_ATTR);
            types.setSuperType(type, MediaType.parse(parent));
        } else if (GLOB_TAG.equals(qName)) {
            String pattern = attributes.getValue(PATTERN_ATTR);
            String isRegex = attributes.getValue(ISREGEX_ATTR);
            if (pattern != null) {
                try {
                    types.addPattern(type, pattern, Boolean.valueOf(isRegex));
                } catch (MimeTypeException e) {
                    throw new SAXException(e);
                }
            }
        } else if (ROOT_XML_TAG.equals(qName)) {
            String namespace = attributes.getValue(NS_URI_ATTR);
            String name = attributes.getValue(LOCAL_NAME_ATTR);
            type.addRootXML(namespace, name);
        } else if (MATCH_TAG.equals(qName)) {
            String kind = attributes.getValue(MATCH_TYPE_ATTR);
            String offset = attributes.getValue(MATCH_OFFSET_ATTR);
            String value = attributes.getValue(MATCH_VALUE_ATTR);
            String mask = attributes.getValue(MATCH_MASK_ATTR);
            if (kind == null) {
                kind = "string";
            }
            clause = new MagicMatch(type.getType(), kind, offset, value, mask);
            clauseStack.addLast(clauses);
            clauses = null;
        } else if (MAGIC_TAG.equals(qName)) {
            String value = attributes.getValue(MAGIC_PRIORITY_ATTR);
            if (value != null && value.length() > 0) {
                priority = Integer.parseInt(value);
            } else {
                priority = 50;
            }
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (type != null) {
            if (MIME_TYPE_TAG.equals(qName)) {
                type = null;
            } else if (COMMENT_TAG.equals(qName)) {
                type.setDescription(characters.toString().trim());
            } else if (MATCH_TAG.equals(qName)) {
                if (clauses != null) {
                    Clause subclause;
                    if (clauses.size() == 1) {
                        subclause = clauses.get(0);
                    } else {
                        subclause = new OrClause(clauses);
                    }
                    clause = new AndClause(clause, subclause);
                }
                clauses = clauseStack.removeLast();
                if (clauses == null) {
                    clauses = Collections.singletonList(clause);
                } else {
                    if (clauses.size() == 1) {
                        clauses = new ArrayList<Clause>(clauses);
                    }
                    clauses.add(clause);
                }
            } else if (MAGIC_TAG.equals(qName)) {
                if (clauses != null) {
                    for (Clause clause : clauses) {
                        type.addMagic(new Magic(type, priority, clause));
                    }
                    clauses = null;
                }
            }
        }
        characters.setLength(0);
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        characters.append(ch, start, length);
    }

}
