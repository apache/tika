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
package org.apache.tika.parser.geoinfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

/**
 * Apache SIS parses ISO 19139 with its own StAX reader and resolves xlink:href. None of a
 * DOCTYPE, an external entity, an xlink or a schemaLocation may reach the network or a file.
 */
public class GeographicInformationXxeTest extends TikaTest {

    private static final String SECRET = "SECRET_CONTENT_19139";
    private static ServerSocket oracle;
    private static final AtomicInteger CONNECTIONS = new AtomicInteger();
    private static String base;

    @BeforeAll
    public static void startOracle() throws IOException {
        oracle = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread t = new Thread(() -> {
            while (!oracle.isClosed()) {
                try (Socket s = oracle.accept()) {
                    CONNECTIONS.incrementAndGet();
                } catch (IOException e) {
                    return;
                }
            }
        }, "xxe-oracle");
        t.setDaemon(true);
        t.start();
        base = "http://127.0.0.1:" + oracle.getLocalPort() + "/";
    }

    @AfterAll
    public static void stopOracle() throws IOException {
        oracle.close();
    }

    private static String fixture() throws IOException {
        try (InputStream is = GeographicInformationXxeTest.class
                .getResourceAsStream("/test-documents/sampleFile.iso19139")) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String afterDeclaration(String xml, String insert) {
        int end = xml.indexOf("?>") + 2;
        return xml.substring(0, end) + insert + xml.substring(end);
    }

    private static String parse(String xml) throws Exception {
        BodyContentHandler handler = new BodyContentHandler(-1);
        try (TikaInputStream tis = TikaInputStream.get(xml.getBytes(StandardCharsets.UTF_8))) {
            new GeographicInformationParser().parse(tis, handler, new Metadata(), new ParseContext());
        }
        return handler.toString();
    }

    private void assertNoFetch(String name, String xml) {
        int before = CONNECTIONS.get();
        String text = "";
        try {
            text = parse(xml);
        } catch (Exception e) {
            Throwable t = e;
            while (t != null) {
                if (t instanceof FileNotFoundException || t instanceof ConnectException ||
                        (t.getMessage() != null && (t.getMessage().contains("couldnt_possibly_exist") ||
                                t.getMessage().contains("Connection refused")))) {
                    fail(name + ": parser tried to fetch an external resource", e);
                }
                t = t.getCause();
            }
        }
        assertEquals(before, CONNECTIONS.get(), name + ": parser connected to the oracle");
        assertFalse(text.contains(SECRET), name + ": leaked external content");
    }

    @Test
    public void testExternalDtdAndEntity() throws Exception {
        String xml = fixture();
        assertNoFetch("dtd http", afterDeclaration(xml,
                "<!DOCTYPE gmd:MD_Metadata SYSTEM \"" + base + "x.dtd\">"));
        assertNoFetch("dtd file", afterDeclaration(xml,
                "<!DOCTYPE gmd:MD_Metadata SYSTEM \"file:///couldnt_possibly_exist/x.dtd\">"));
        assertNoFetch("entity http", afterDeclaration(xml,
                "<!DOCTYPE gmd:MD_Metadata [<!ENTITY x SYSTEM \"" + base + "e.txt\">]>")
                .replaceFirst("<gco:CharacterString>", "<gco:CharacterString>&x;"));
        assertNoFetch("parameter entity", afterDeclaration(xml,
                "<!DOCTYPE gmd:MD_Metadata [<!ENTITY % p SYSTEM \"" + base + "p.dtd\">%p;]>"));
    }

    @Test
    public void testXlinkAndSchemaLocationNotResolved() throws Exception {
        String xml = fixture();
        assertTrue(xml.contains("<gmd:contact>"), "fixture shape changed");
        assertNoFetch("xlink:href", xml.replaceFirst("<gmd:contact>",
                "<gmd:contact xlink:href=\"" + base + "contact.xml\">"));
        assertNoFetch("xlink:href empty element", xml.replaceFirst("<gmd:parentIdentifier>.*?</gmd:parentIdentifier>",
                "<gmd:parentIdentifier xlink:href=\"" + base + "parent.xml\"/>"));
        assertNoFetch("schemaLocation", xml.replaceFirst("xsi:schemaLocation=\"[^\"]*\"",
                "xsi:schemaLocation=\"http://www.isotc211.org/2005/gmd " + base + "gmd.xsd\""));
    }
}
