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
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.NoSuchElementException;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.ToTextContentHandler;

/**
 * Class to test that XMLReaderUtils defends against xxe and billion laughs.
 * <p>
 * Different versions and different implementations vary. This is not a fully comprehensive set of tests.
 * <p>
 * Please add more.
 * <p>
 * See also the tests with woodstox in tika-woodstox-tests.
 */
public class XMLReaderUtilsTest {

    private static final Locale defaultLocale = Locale.getDefault();
    static {
        //tests on content of Exception msgs require specifying locale.
        //even this, though is not sufficient for the billion laughs tests ?!
        Locale.setDefault(Locale.US);
    }
    private static final String EXTERNAL_DTD_SIMPLE_FILE = "<?xml version=\"1.0\" standalone=\"no\"?><!DOCTYPE foo SYSTEM \"tutorials.dtd\"><foo/>";
    private static final String EXTERNAL_DTD_SIMPLE_URL = "<?xml version=\"1.0\" standalone=\"no\"?><!DOCTYPE foo SYSTEM \"http://127.234.172.38:7845/bar\"><foo/>";
    private static final String EXTERNAL_ENTITY =  "<!DOCTYPE foo [" + " <!ENTITY bar SYSTEM \"http://127.234.172.38:7845/bar\">" +
            " ]><foo>&bar;</foo>";
    private static final String EXTERNAL_LOCAL_DTD = "<!DOCTYPE foo [" +
            "<!ENTITY % local_dtd SYSTEM \"file:///usr/local/app/schema.dtd\">" +
            "%local_dtd;]><foo/>";

    private static final String BILLION_LAUGHS_CLASSICAL = "<?xml version=\"1.0\"?>\n" + "<!DOCTYPE lolz [\n" + " <!ENTITY lol \"lol\">\n" + " <!ELEMENT lolz (#PCDATA)>\n" +
            " <!ENTITY lol1 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">\n" + " <!ENTITY lol2 \"&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;&lol1;\">\n" +
            " <!ENTITY lol3 \"&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;\">\n" +
            " <!ENTITY lol4 \"&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;&lol3;\">\n" +
            " <!ENTITY lol5 \"&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;&lol4;\">\n" +
            " <!ENTITY lol6 \"&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;&lol5;\">\n" +
            " <!ENTITY lol7 \"&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;&lol6;\">\n" +
            " <!ENTITY lol8 \"&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;&lol7;\">\n" +
            " <!ENTITY lol9 \"&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;&lol8;\">\n" + "]>\n" + "<lolz>&lol9;</lolz>";

    private static String BILLION_LAUGHS_VARIANT;

    static {
        StringBuilder entity = new StringBuilder();
        for (int i = 0; i < 1000000; i++) {
            entity.append("a");
        }
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\"?>\n" + "<!DOCTYPE kaboom [\n" + "  <!ENTITY a \"");
        xml.append(entity.toString());
        xml.append("\">]>" + "<kaboom>");
        for (int i = 0; i < 100000; i++) {
            xml.append("&a;");
        }
        xml.append("</kaboom>");
        BILLION_LAUGHS_VARIANT = xml.toString();
    }

    private static final String[] EXTERNAL_ENTITY_XMLS = new String[]{ EXTERNAL_DTD_SIMPLE_FILE, EXTERNAL_DTD_SIMPLE_URL,
            EXTERNAL_ENTITY, EXTERNAL_LOCAL_DTD };

    private static final String[] BILLION_LAUGHS = new String[]{ BILLION_LAUGHS_CLASSICAL, BILLION_LAUGHS_VARIANT };

    @AfterAll
    public static void tearDown() {
        Locale.setDefault(defaultLocale);
    }

    //make sure that parseSAX actually defends against external entities
    @Test
    public void testSAX() throws Exception {
        for (String xml : EXTERNAL_ENTITY_XMLS) {
            try {
                XMLReaderUtils.parseSAX(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        new ToTextContentHandler(), new ParseContext());
            } catch (ConnectException e) {
                fail("Parser tried to access resource: " + xml, e);
            }
        }
    }

    @Test
    public void testDOM() throws Exception {
        for (String xml : EXTERNAL_ENTITY_XMLS) {
            try {
                XMLReaderUtils.buildDOM(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), new ParseContext());
            } catch (ConnectException e) {
                fail("Parser tried to access resource: " + xml, e);
            }
        }
    }

    @Test
    public void testStax() throws Exception {
        for (String xml : EXTERNAL_ENTITY_XMLS) {
            try {
                XMLInputFactory xmlInputFactory = XMLReaderUtils.getXMLInputFactory(new ParseContext());
                XMLEventReader reader = xmlInputFactory.createXMLEventReader(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
                StringBuilder sb = new StringBuilder();
                while (reader.hasNext()) {
                    sb.append(reader.next());
                }
                if (sb.toString().contains("Exception scanning External")) {
                    fail("tried to read external dtd");
                }
            } catch (XMLStreamException e) {
                fail("StreamException: " + xml, e);
            } catch (NoSuchElementException e) {
                if (e.getMessage() != null) {
                    if (e.getMessage().contains("Connection refused")) {
                        fail("Vulnerable to ssrf via url: " + xml, e);
                    } else if (e.getMessage().contains("No such file")) {
                        fail("Vulnerable to local file read via external entity/dtd: " + xml, e);
                    }
                }
            }
        }
    }

    @Test
    public void testSAXBillionLaughs() throws Exception {
        for (String xml : BILLION_LAUGHS) {
            try {
                XMLReaderUtils.parseSAX(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                        new ToTextContentHandler(), new ParseContext());
            } catch (SAXException e) {
                limitCheck(e);
            }
        }
    }

    @Test
    public void testDOMBillionLaughs() throws Exception {
        //confirm that ExpandEntityReferences has been set to false.

        //some implementations ignore the expandEntityReferences=false, and we are still
        //protected by the "The parser has encountered more than "20" entity expansions" SAXException.
        //We need to check for either: empty content and no exception, or this SAXException
        for (String xml : BILLION_LAUGHS) {
            Document doc = null;
            try {
                doc = XMLReaderUtils.buildDOM(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), new ParseContext());
            } catch (SAXException e) {
                limitCheck(e);
                continue;
            }
            NodeList nodeList = doc.getChildNodes();
            StringBuilder sb = new StringBuilder();
            dumpChildren(nodeList, sb);
            assertEquals(0, sb
                    .toString()
                    .trim()
                    .length(), sb.toString());
        }
    }

    private void dumpChildren(NodeList nodeList, StringBuilder sb) {
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n = nodeList.item(i);
            String txt = n.getTextContent();
            if (txt != null) {
                sb.append(txt);
            }
        }
    }

    @Test
    public void testStaxBillionLaughs() throws Exception {
        /*
            Turning off dtd support of the XMLInputFactory in XMLReaderUtils turns off entity expansions and
            causes a "NoSuchElementException" with the "'lol9' was referenced but not declared" message with this line:
                    tryToSetStaxProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
            If that line doesn't exist, then we get a
            NoSuchElementException with: "The parser has encountered more than "20" entity expansions in this document; this is the limit imposed by the JDK."
         */

        for (String xml : BILLION_LAUGHS) {
            XMLInputFactory xmlInputFactory = XMLReaderUtils.getXMLInputFactory(new ParseContext());
            XMLEventReader reader = xmlInputFactory.createXMLEventReader(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            try {
                while (reader.hasNext()) {
                    reader.next();
                }
            } catch (NoSuchElementException e) {
                //full message on temurin-17: The entity "lol9" was referenced, but not declared.
                String msg = e.getLocalizedMessage();

                if (msg != null) {
                    if (msg.contains("referenced") && msg.contains("not declared")) {
                        continue;
                    } else if (msg.contains("JAXP00010001")) {
                        continue;
                    }
                }
                throw e;

            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testTransformerExternalEntity(boolean saxFactory, @TempDir Path dir) throws Exception {
        Path external = dir.resolve("external.txt");
        Files.writeString(external, "EXTERNAL_CONTENT");
        String xml = "<!DOCTYPE root [<!ENTITY external SYSTEM '" + external.toUri() + "'>]>" +
                "<root>before&external;after</root>";
        TransformerFactory factory = saxFactory ? XMLReaderUtils.getSAXTransformerFactory() : XMLReaderUtils.getTransformerFactory();
        Transformer transformer = factory.newTransformer();
        DOMResult output = new DOMResult();
        transformer.transform(new StreamSource(new StringReader(xml)), output);
        assertEquals("beforeafter", ((Document) output.getNode()).getDocumentElement().getTextContent());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    public void testTransformerDocumentResolver(boolean saxFactory, @TempDir Path dir) throws Exception {
        Path external = dir.resolve("external.xml");
        Files.writeString(external, "<secret>EXTERNAL_CONTENT</secret>");
        String uri = external.toUri().toString();
        String stylesheet = String.format(Locale.ROOT, """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:output method="text"/>
                  <xsl:template match="/">before<xsl:value-of select="document('%s')/secret"/>after</xsl:template>
                </xsl:stylesheet>
                """, uri);
        TransformerFactory factory = saxFactory ? XMLReaderUtils.getSAXTransformerFactory() : XMLReaderUtils.getTransformerFactory();
        Transformer transformer = factory.newTransformer(new StreamSource(new StringReader(stylesheet)));
        StringWriter output = new StringWriter();
        transformer.transform(new StreamSource(new StringReader("<root/>")), new StreamResult(output));
        assertEquals("beforeafter", output.toString());

        transformer.setURIResolver((href, base) -> uri.equals(href) ?
                new StreamSource(new StringReader("<secret>ALLOWED_CONTENT</secret>")) : null);
        output = new StringWriter();
        transformer.transform(new StreamSource(new StringReader("<root/>")), new StreamResult(output));
        assertEquals("beforeALLOWED_CONTENTafter", output.toString());
    }

    @ParameterizedTest
    @CsvSource({"false, include", "true, include", "false, import", "true, import"})
    public void testTransformerStylesheetResolver(boolean saxFactory, String directive, @TempDir Path dir) throws Exception {
        String externalStylesheet = """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:template match="payload" mode="external">EXTERNAL_CONTENT</xsl:template>
                </xsl:stylesheet>
                """;
        Path external = dir.resolve("external.xsl");
        Files.writeString(external, externalStylesheet);
        String uri = external.toUri().toString();
        String stylesheet = String.format(Locale.ROOT, """
                <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                  <xsl:%s href="%s"/>
                  <xsl:output method="text"/>
                  <xsl:template match="/">before<xsl:apply-templates select="root/payload" mode="external"/>after</xsl:template>
                </xsl:stylesheet>
                """, directive, uri);
        TransformerFactory factory = saxFactory ? XMLReaderUtils.getSAXTransformerFactory() : XMLReaderUtils.getTransformerFactory();
        Transformer transformer = factory.newTransformer(new StreamSource(new StringReader(stylesheet)));
        StringWriter output = new StringWriter();
        transformer.transform(new StreamSource(new StringReader("<root><payload/></root>")), new StreamResult(output));
        assertEquals("beforeafter", output.toString());

        factory.setURIResolver((href, base) -> uri.equals(href) ?
                new StreamSource(new StringReader(externalStylesheet.replace("EXTERNAL_CONTENT", "ALLOWED_CONTENT"))) : null);
        transformer = factory.newTransformer(new StreamSource(new StringReader(stylesheet)));
        output = new StringWriter();
        transformer.transform(new StreamSource(new StringReader("<root><payload/></root>")), new StreamResult(output));
        assertEquals("beforeALLOWED_CONTENTafter", output.toString());
    }

    private void limitCheck(SAXException e) throws SAXException {
        String msg = e.getLocalizedMessage();
        if (msg == null) {
            throw e;
        }

        //depending on the flavor/version of the jdk, entity expansions may be triggered
        // OR entitySizeLimit may be triggered
        //See TIKA-4471
        if (msg.contains("JAXP00010001") || //entity expansions
                msg.contains("JAXP00010003") || //max entity size limit
                msg.contains("JAXP00010004") || //TotalEntitySizeLimit
                msg.contains("entity expansions") ||
                e.getMessage().contains("maxGeneralEntitySizeLimit")) {
            return;
        }
        throw e;
    }
}
