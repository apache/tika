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
package org.apache.tika.async.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.utils.XMLReaderUtils;

public class TikaConfigAsyncWriterTest {


    @Test
    public void testBasic(@TempDir Path dir) throws Exception {
        Path p = Paths.get(TikaConfigAsyncWriter.class.getResource("/configs/TIKA-4508-parsers.xml").toURI());
        SimpleAsyncConfig simpleAsyncConfig = new SimpleAsyncConfig("input", "output", 4,
                10000L, "-Xmx1g", null, p.toAbsolutePath().toString(), null,
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT, false);
        Path target = dir.resolve("combined.xml");
        TikaConfigAsyncWriter writer = new TikaConfigAsyncWriter(simpleAsyncConfig);
        writer.write(target);

        Set<String> expected = Set.of("service-loader", "parsers", "pipesIterator", "async");
        Set<String> properties = loadProperties(target);
        assertEquals(expected, properties);
    }

    @Test
    public void testDontOverwriteEmitters(@TempDir Path dir) throws Exception {
        Path p = Paths.get(TikaConfigAsyncWriter.class.getResource("/configs/TIKA-4508-emitters.xml").toURI());
        SimpleAsyncConfig simpleAsyncConfig = new SimpleAsyncConfig("input", "output", 4,
                10000L, "-Xmx1g", null, p.toAbsolutePath().toString(), null,
                BasicContentHandlerFactory.HANDLER_TYPE.TEXT, false);
        Path target = dir.resolve("combined.xml");
        TikaConfigAsyncWriter writer = new TikaConfigAsyncWriter(simpleAsyncConfig);
        writer.write(target);

        Set<String> expected = Set.of("parsers", "pipesIterator", "emitters", "async");
        Set<String> properties = loadProperties(target);
        assertEquals(expected, properties);

        Document doc = XMLReaderUtils.buildDOM(target);
        Element emitters = TikaConfigAsyncWriter.findChild("emitters", doc.getDocumentElement());
        assertNotNull(emitters);
        int found = 0;
        for (int i = 0; i < emitters.getChildNodes().getLength(); i++) {
            Node n = emitters.getChildNodes().item(i);
            if ("emitter".equals(n.getLocalName())) {
                Node clazzNode = n.getAttributes().getNamedItem("class");
                if (clazzNode != null) {
                    String clazz = clazzNode.getNodeValue();
                    if (clazz != null && clazz.startsWith("com.custom.")) {
                        found++;
                    }
                }
            }
        }
        assertEquals(2, found);

    }


    private Set<String> loadProperties(Path path) throws TikaException, IOException, SAXException {
        Document document = XMLReaderUtils.buildDOM(path);
        Element properties = document.getDocumentElement();
        assertEquals("properties", properties.getLocalName());
        Set<String> children = new HashSet<>();
        for (int i = 0; i < properties.getChildNodes().getLength(); i++) {
            Node n = properties.getChildNodes().item(i);
            if (n.getLocalName() != null) {
                children.add(n.getLocalName());
            }
        }
        return children;
    }
}
