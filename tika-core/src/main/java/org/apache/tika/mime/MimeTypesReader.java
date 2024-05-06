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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.XMLReaderUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *    &lt;!ATTLIST mime-info xmlns CDATA #FIXED
 *    &quot;http://www.freedesktop.org/standards/shared-mime-info&quot;&gt;
 *
 *    &lt;!ELEMENT mime-type
 *     (comment|acronym|expanded-acronym|glob|magic|root-XML|alias|sub-class-of)*&gt;
 *    &lt;!ATTLIST mime-type type CDATA #REQUIRED&gt;
 *
 *    &lt;!-- a comment describing a document with the respective MIME type. Example:
 *    &quot;WMV video&quot; --&gt;
 *    &lt;!ELEMENT _comment (#PCDATA)&gt;
 *    &lt;!ATTLIST _comment xml:lang CDATA #IMPLIED&gt;
 *
 *    &lt;!-- a comment describing a the respective unexpanded MIME type acronym. Example:
 *    &quot;WMV&quot; --&gt;
 *    &lt;!ELEMENT acronym (#PCDATA)&gt;
 *    &lt;!ATTLIST acronym xml:lang CDATA #IMPLIED&gt;
 *
 *    &lt;!-- a comment describing a the respective unexpanded MIME type acronym. Example:
 *    &quot;Windows Media Video&quot; --&gt;
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
 *    &lt;!ATTLIST match type
 *        (string|big16|big32|little16|little32|host16|host32|byte) #REQUIRED&gt;
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
 * <p>In addition to the standard fields, this will also read two Tika specific fields: - link - uti
 *
 * @see <a
 *     href="https://freedesktop.org/wiki/Specifications/shared-mime-info-spec/">https://freedesktop.org/wiki/Specifications/shared-mime-info-spec/</a>
 */
public class MimeTypesReader extends DefaultHandler implements MimeTypesReaderMetKeys {
    private static final ReentrantReadWriteLock READ_WRITE_LOCK = new ReentrantReadWriteLock();

    /** Parser pool size */
    private static int POOL_SIZE = 10;

    private static ArrayBlockingQueue<SAXParser> SAX_PARSERS = new ArrayBlockingQueue<>(POOL_SIZE);
    static Logger LOG = LoggerFactory.getLogger(MimeTypesReader.class);

    static {
        try {
            setPoolSize(POOL_SIZE);
        } catch (TikaException e) {
            throw new RuntimeException("problem initializing SAXParser pool", e);
        }
    }

    protected final MimeTypes types;

    /** Current type */
    protected MimeType type = null;

    protected int priority;

    protected StringBuilder characters = null;
    private ClauseRecord current = new ClauseRecord(null);

    protected MimeTypesReader(MimeTypes types) {
        this.types = types;
    }

    /**
     * Acquire a SAXParser from the pool; create one if it doesn't exist. Make sure to {@link
     * #releaseParser(SAXParser)} in a <code>finally</code> block every time you call this.
     *
     * @return a SAXParser
     * @throws TikaException
     */
    private static SAXParser acquireSAXParser() throws TikaException {
        while (true) {
            SAXParser parser = null;
            try {
                READ_WRITE_LOCK.readLock().lock();
                parser = SAX_PARSERS.poll(10, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new TikaException("interrupted while waiting for SAXParser", e);
            } finally {
                READ_WRITE_LOCK.readLock().unlock();
            }
            if (parser != null) {
                return parser;
            }
        }
    }

    /**
     * Return parser to the pool for reuse
     *
     * @param parser parser to return
     */
    private static void releaseParser(SAXParser parser) {
        try {
            parser.reset();
        } catch (UnsupportedOperationException e) {
            // ignore
        }
        try {
            READ_WRITE_LOCK.readLock().lock();
            // if there are extra parsers (e.g. after a reset of the pool to a smaller size),
            // this parser will not be added and will then be gc'd
            SAX_PARSERS.offer(parser);
        } finally {
            READ_WRITE_LOCK.readLock().unlock();
        }
    }

    /**
     * Set the pool size for cached XML parsers.
     *
     * @param poolSize
     */
    public static void setPoolSize(int poolSize) throws TikaException {
        try {
            // stop the world with a write lock
            // parsers that are currently in use will be offered, but not
            // accepted and will be gc'd
            READ_WRITE_LOCK.writeLock().lock();
            SAX_PARSERS = new ArrayBlockingQueue<>(poolSize);
            for (int i = 0; i < poolSize; i++) {
                SAX_PARSERS.offer(newSAXParser());
            }
            POOL_SIZE = poolSize;
        } finally {
            READ_WRITE_LOCK.writeLock().unlock();
        }
    }

    private static SAXParser newSAXParser() throws TikaException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(false);
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException | SAXException e) {
            LOG.warn(
                    "can't set secure processing feature on: "
                            + factory.getClass()
                            + ". User assumes responsibility for consequences.");
        }
        try {
            return factory.newSAXParser();
        } catch (ParserConfigurationException | SAXException e) {
            throw new TikaException("Can't create new sax parser", e);
        }
    }

    public void read(InputStream stream) throws IOException, MimeTypeException {
        SAXParser parser = null;
        try {

            parser = acquireSAXParser();
            parser.parse(stream, this);
        } catch (TikaException e) {
            throw new MimeTypeException("Unable to create an XML parser", e);
        } catch (SAXException e) {
            throw new MimeTypeException("Invalid type configuration", e);
        } finally {
            if (parser != null) {
                releaseParser(parser);
            }
        }
    }

    public void read(Document document) throws MimeTypeException {
        try {
            Transformer transformer = XMLReaderUtils.getTransformer();
            transformer.transform(new DOMSource(document), new SAXResult(this));
        } catch (TransformerException | TikaException e) {
            throw new MimeTypeException("Failed to parse type registry", e);
        }
    }

    @Override
    public InputSource resolveEntity(String publicId, String systemId) {
        return new InputSource(new UnsynchronizedByteArrayInputStream(new byte[0]));
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {
        if (type == null) {
            if (MIME_TYPE_TAG.equals(qName)) {
                String name = attributes.getValue(MIME_TYPE_TYPE_ATTR);
                String interpretedAttr = attributes.getValue(INTERPRETED_ATTR);
                boolean interpreted = "true".equals(interpretedAttr);
                try {
                    type = types.forName(name);
                    type.setInterpreted(interpreted);
                } catch (MimeTypeException e) {
                    handleMimeError(name, e, qName, attributes);
                }
            }
        } else if (ALIAS_TAG.equals(qName)) {
            String alias = attributes.getValue(ALIAS_TYPE_ATTR);
            types.addAlias(type, MediaType.parse(alias));
        } else if (SUB_CLASS_OF_TAG.equals(qName)) {
            String parent = attributes.getValue(SUB_CLASS_TYPE_ATTR);
            types.setSuperType(type, MediaType.parse(parent));
        } else if (ACRONYM_TAG.equals(qName)
                || COMMENT_TAG.equals(qName)
                || TIKA_LINK_TAG.equals(qName)
                || TIKA_UTI_TAG.equals(qName)) {
            characters = new StringBuilder();
        } else if (GLOB_TAG.equals(qName)) {
            String pattern = attributes.getValue(PATTERN_ATTR);
            String isRegex = attributes.getValue(ISREGEX_ATTR);
            if (pattern != null) {
                try {
                    types.addPattern(type, pattern, Boolean.parseBoolean(isRegex));
                } catch (MimeTypeException e) {
                    handleGlobError(type, pattern, e, qName, attributes);
                }
            }
        } else if (ROOT_XML_TAG.equals(qName)) {
            String namespace = attributes.getValue(NS_URI_ATTR);
            String name = attributes.getValue(LOCAL_NAME_ATTR);
            type.addRootXML(namespace, name);
        } else if (MATCH_TAG.equals(qName)) {
            if (attributes.getValue(MATCH_MINSHOULDMATCH_ATTR) != null) {
                current =
                        new ClauseRecord(
                                new MinShouldMatchVal(
                                        Integer.parseInt(
                                                attributes.getValue(MATCH_MINSHOULDMATCH_ATTR))));
            } else {
                String kind = attributes.getValue(MATCH_TYPE_ATTR);
                String offset = attributes.getValue(MATCH_OFFSET_ATTR);
                String value = attributes.getValue(MATCH_VALUE_ATTR);
                String mask = attributes.getValue(MATCH_MASK_ATTR);
                if (kind == null) {
                    kind = "string";
                }
                current =
                        new ClauseRecord(new MagicMatch(type.getType(), kind, offset, value, mask));
            }
        } else if (MAGIC_TAG.equals(qName)) {
            String value = attributes.getValue(MAGIC_PRIORITY_ATTR);
            if (value != null && value.length() > 0) {
                priority = Integer.parseInt(value);
            } else {
                priority = 50;
            }
            current = new ClauseRecord(null);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
        if (type != null) {
            if (MIME_TYPE_TAG.equals(qName)) {
                type = null;
            } else if (COMMENT_TAG.equals(qName)) {
                type.setDescription(characters.toString().trim());
                characters = null;
            } else if (ACRONYM_TAG.equals(qName)) {
                type.setAcronym(characters.toString().trim());
                characters = null;
            } else if (TIKA_UTI_TAG.equals(qName)) {
                type.setUniformTypeIdentifier(characters.toString().trim());
                characters = null;
            } else if (TIKA_LINK_TAG.equals(qName)) {
                try {
                    type.addLink(new URI(characters.toString().trim()));
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("unable to parse link: " + characters, e);
                }
                characters = null;
            } else if (MATCH_TAG.equals(qName)) {
                current.stop();
            } else if (MAGIC_TAG.equals(qName)) {
                for (Clause clause : current.getClauses()) {
                    type.addMagic(new Magic(type, priority, clause));
                }
                current = null;
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) {
        if (characters != null) {
            characters.append(ch, start, length);
        }
    }

    protected void handleMimeError(
            String input, MimeTypeException ex, String qName, Attributes attributes)
            throws SAXException {
        throw new SAXException(ex);
    }

    protected void handleGlobError(
            MimeType type,
            String pattern,
            MimeTypeException ex,
            String qName,
            Attributes attributes)
            throws SAXException {
        throw new SAXException(ex);
    }

    /**
     * Shim class used during building of actual classes. This temporarily holds the value of the
     * minShouldMatchClause so that the actual MinShouldMatchClause can have a cleaner/immutable
     * initialization.
     */
    private static class MinShouldMatchVal implements Clause {

        private final int val;

        MinShouldMatchVal(int val) {
            this.val = val;
        }

        int getVal() {
            return val;
        }

        @Override
        public boolean eval(byte[] data) {
            throw new IllegalStateException(
                    "This should never be used " + "on this placeholder class");
        }

        @Override
        public int size() {
            return 0;
        }
    }

    private class ClauseRecord {

        private final ClauseRecord parent;

        private Clause clause;

        private List<Clause> subclauses = null;

        public ClauseRecord(Clause clause) {
            this.parent = current;
            this.clause = clause;
        }

        public void stop() {
            if (clause instanceof MinShouldMatchVal) {
                clause =
                        new MinShouldMatchClause(((MinShouldMatchVal) clause).getVal(), subclauses);
            } else if (subclauses != null) {
                Clause subclause;
                if (subclauses.size() == 1) {
                    subclause = subclauses.get(0);
                } else {
                    subclause = new OrClause(subclauses);
                }
                clause = new AndClause(clause, subclause);
            }

            if (parent.subclauses == null) {
                parent.subclauses = Collections.singletonList(clause);
            } else {
                if (parent.subclauses.size() == 1) {
                    parent.subclauses = new ArrayList<>(parent.subclauses);
                }
                parent.subclauses.add(clause);
            }

            current = current.parent;
        }

        public List<Clause> getClauses() {
            return subclauses;
        }
    }
}
