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
package org.apache.tika.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.StringReader;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;

/**
 * The XML security contract of {@link XMLReaderUtils}: no external resource is ever
 * fetched, and entity expansion is bounded. Runs once per JAXP provider Tika verifies;
 * the tika-core pom runs it again with standalone Xerces on the classpath and
 * {@code tika.test.xml.provider=xerces}. A wrong provider fails the run rather than
 * silently testing the wrong parser.
 */
public class XmlSecurityContractTest {

    private static final String PROVIDER_PROPERTY = "tika.test.xml.provider";
    private static final String SECRET = "SECRET_CONTENT_7f3a";
    // more than any bounded parse should hand a handler
    private static final long MAX_EXPANDED_CHARS = 50_000_000L;

    private static final String UNROUTABLE = "http://127.234.172.38:7845/bar";
    private static final String CLOSED_PORT = "http://127.0.0.1:9/bar";

    private static final String BILLION_LAUGHS = "<?xml version=\"1.0\"?>\n<!DOCTYPE lolz [\n" +
            " <!ENTITY lol \"lol\">\n <!ELEMENT lolz (#PCDATA)>\n" +
            " <!ENTITY lol1 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">\n" +
            " <!ENTITY lol2 \"&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;\">\n" +
            " <!ENTITY lol3 \"&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;\">\n" +
            " <!ENTITY lol4 \"&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;\">\n" +
            " <!ENTITY lol5 \"&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;\">\n" +
            " <!ENTITY lol6 \"&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;\">\n" +
            " <!ENTITY lol7 \"&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;\">\n" +
            " <!ENTITY lol8 \"&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;\">\n" +
            " <!ENTITY lol9 \"&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;\">\n" +
            "]>\n<lolz>&lol9;</lolz>";

    // one 1 MB entity referenced 100k times: a size bomb rather than a depth bomb
    private static final String SIZE_BOMB = "<?xml version=\"1.0\"?>\n<!DOCTYPE kaboom [\n" +
            "  <!ENTITY a \"" + "a".repeat(1_000_000) + "\">]><kaboom>" +
            "&a;".repeat(100_000) + "</kaboom>";

    private static final String PROVIDER = System.getProperty(PROVIDER_PROPERTY, "jdk");

    @TempDir
    static Path dir;
    static String secretUri;
    static String leakDtdUri;

    @BeforeAll
    public static void setUp() throws IOException {
        String expectedPackage;
        switch (PROVIDER) {
            case "jdk":
                expectedPackage = "com.sun.org.apache.xerces.internal.jaxp.";
                break;
            case "xerces":
                expectedPackage = "org.apache.xerces.jaxp.";
                break;
            default:
                throw new IllegalArgumentException(PROVIDER_PROPERTY + "=" + PROVIDER);
        }
        // the library wraps these; check the lookup it performs, not the wrapper
        String sax = SAXParserFactory.newInstance().getClass().getName();
        String dom = DocumentBuilderFactory.newInstance().getClass().getName();
        assertTrue(sax.startsWith(expectedPackage), PROVIDER + " expected, SAX provider is " + sax);
        assertTrue(dom.startsWith(expectedPackage), PROVIDER + " expected, DOM provider is " + dom);

        Path secret = dir.resolve("secret.txt");
        Files.writeString(secret, SECRET, StandardCharsets.UTF_8);
        secretUri = secret.toUri().toString();
        Path leakDtd = dir.resolve("leak.dtd");
        Files.writeString(leakDtd, "<!ENTITY leak \"" + SECRET + "\">", StandardCharsets.UTF_8);
        leakDtdUri = leakDtd.toUri().toString();
    }

    private static List<String> xxePayloads() {
        List<String> xmls = new ArrayList<>();
        // external general entity: local file, unroutable host, closed port
        xmls.add("<!DOCTYPE r [<!ENTITY x SYSTEM \"" + secretUri + "\">]><r>&x;</r>");
        xmls.add("<!DOCTYPE r [<!ENTITY x SYSTEM \"" + UNROUTABLE + "\">]><r>&x;</r>");
        xmls.add("<!DOCTYPE r [<!ENTITY x SYSTEM \"" + CLOSED_PORT + "\">]><r>&x;</r>");
        // external DTD subset declaring the entity
        xmls.add("<!DOCTYPE r SYSTEM \"" + leakDtdUri + "\"><r>&leak;</r>");
        xmls.add("<?xml version=\"1.0\" standalone=\"no\"?><!DOCTYPE r SYSTEM \"tutorials.dtd\"><r/>");
        xmls.add("<?xml version=\"1.0\" standalone=\"no\"?><!DOCTYPE r SYSTEM \"" + UNROUTABLE +
                "\"><r/>");
        // external parameter entity pulling in the declaration
        xmls.add("<!DOCTYPE r [<!ENTITY % p SYSTEM \"" + leakDtdUri + "\">%p;]><r>&leak;</r>");
        xmls.add("<!DOCTYPE r [<!ENTITY % p SYSTEM \"file:///usr/local/app/schema.dtd\">%p;]><r/>");
        return xmls;
    }

    @ParameterizedTest
    @ValueSource(strings = {"sax", "dom", "transformer", "saxTransformer"})
    public void testNoExternalAccess(String path) throws Exception {
        for (String xml : xxePayloads()) {
            String text;
            try {
                text = parse(path, xml);
            } catch (Exception e) {
                assertNotAFetch(xml, e);
                continue;
            }
            assertFalse(text.contains(SECRET), path + " leaked external content for " + xml);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"sax", "dom"})
    @Timeout(120)
    public void testExpansionCountBounded(String path) throws Exception {
        assertRejected(path, BILLION_LAUGHS);
    }

    @ParameterizedTest
    @ValueSource(strings = {"sax", "dom"})
    @Timeout(120)
    public void testEntitySizeBounded(String path) throws Exception {
        // standalone Xerces 2.12 caps the expansion count but not the expanded size: the
        // size bomb runs to completion there. Known gap; Tika verifies the JDK only.
        assumeFalse("xerces".equals(PROVIDER), "standalone Xerces has no entity size limit");
        assertRejected(path, SIZE_BOMB);
    }

    // the caller's resolver is never consulted on the parseSAX path, so a resolver that
    // answers with a bare system id (which a raw parser would fetch) changes nothing
    @Test
    public void testCallerResolverShadowed() throws Exception {
        DefaultHandler systemIdOnly = new DefaultHandler() {
            @Override
            public InputSource resolveEntity(String publicId, String systemId) {
                return new InputSource(systemId);
            }
        };
        for (String xml : xxePayloads()) {
            try {
                XMLReaderUtils.parseSAX(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        systemIdOnly, new ParseContext());
            } catch (Exception e) {
                assertNotAFetch(xml, e);
            }
        }
    }

    // a parser supplied through the ParseContext never brings its own resolver along:
    // these are raw, unsecured JDK parsers with a resolver that fetches
    @ParameterizedTest
    @ValueSource(strings = {"sax", "dom"})
    public void testSuppliedParserStillOffline(String path) throws Exception {
        ParseContext context = new ParseContext();
        if ("sax".equals(path)) {
            context.set(SAXParser.class, SAXParserFactory.newInstance().newSAXParser());
        } else {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            builder.setEntityResolver((publicId, systemId) -> new InputSource(systemId));
            context.set(DocumentBuilder.class, builder);
        }
        for (String xml : xxePayloads()) {
            byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
            String text;
            try {
                if ("sax".equals(path)) {
                    BoundedTextHandler handler = new BoundedTextHandler();
                    XMLReaderUtils.parseSAX(new ByteArrayInputStream(bytes), handler, context);
                    text = handler.text();
                } else {
                    text = XMLReaderUtils.buildDOM(new ByteArrayInputStream(bytes), context)
                            .getDocumentElement().getTextContent();
                }
            } catch (Exception e) {
                assertNotAFetch(xml, e);
                continue;
            }
            assertFalse(text.contains(SECRET), path + " leaked external content for " + xml);
        }
    }

    @Test
    @Timeout(120)
    public void testLimitSurvivesPoolReuse() throws Exception {
        for (int i = 0; i < XMLReaderUtils.getPoolSize() * 2 + 1; i++) {
            assertRejected("sax", BILLION_LAUGHS);
        }
    }

    private void assertRejected(String path, String xml) throws Exception {
        String text;
        try {
            text = parse(path, xml);
        } catch (SAXException | TikaException e) {
            assertTrue(isLimitMessage(e), path + " failed without a limit message: " + e);
            return;
        }
        // DOM with expandEntityReferences=false may hand back an empty tree instead
        assertEquals(0, text.trim().length(), path + " expanded the bomb");
    }

    private static boolean isLimitMessage(Exception e) {
        Throwable t = e;
        while (t != null) {
            String msg = t.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase(Locale.ROOT);
                if (m.contains("jaxp0001000") || m.contains("entity expansion") ||
                        m.contains("entity expansions") || m.contains("entitysizelimit") ||
                        (m.contains("entit") && m.contains("limit"))) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private static void assertNotAFetch(String xml, Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof ConnectException || t instanceof UnknownHostException ||
                    t instanceof NoRouteToHostException || t instanceof FileNotFoundException) {
                fail("parser tried to fetch an external resource for " + xml, e);
            }
            String msg = t.getMessage();
            if (msg != null && (msg.contains("Connection refused") || msg.contains("No such file") ||
                    msg.contains("Exception scanning External"))) {
                fail("parser tried to fetch an external resource for " + xml, e);
            }
            t = t.getCause();
        }
    }

    private static String parse(String path, String xml) throws Exception {
        switch (path) {
            case "sax": {
                BoundedTextHandler handler = new BoundedTextHandler();
                XMLReaderUtils.parseSAX(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        handler, new ParseContext());
                return handler.text();
            }
            case "dom": {
                Document doc = XMLReaderUtils.buildDOM(
                        new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        new ParseContext());
                return doc.getDocumentElement().getTextContent();
            }
            case "transformer":
            case "saxTransformer": {
                TransformerFactory factory = "sax".equals(path.substring(0, 3)) ?
                        XMLReaderUtils.getSAXTransformerFactory() :
                        XMLReaderUtils.getTransformerFactory();
                Transformer transformer = factory.newTransformer();
                DOMResult result = new DOMResult();
                transformer.transform(new StreamSource(new StringReader(xml)), result);
                return ((Document) result.getNode()).getDocumentElement().getTextContent();
            }
            default:
                throw new IllegalArgumentException(path);
        }
    }

    // keeps a prefix for assertions, counts everything, and stops a runaway expansion
    private static class BoundedTextHandler extends DefaultHandler {
        private final StringBuilder prefix = new StringBuilder();
        private long count = 0;

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            count += length;
            if (count > MAX_EXPANDED_CHARS) {
                throw new SAXException("harness: more than " + MAX_EXPANDED_CHARS +
                        " characters expanded");
            }
            if (prefix.length() < 10_000) {
                prefix.append(ch, start, Math.min(length, 10_000 - prefix.length()));
            }
        }

        String text() {
            return prefix.toString();
        }
    }
}
