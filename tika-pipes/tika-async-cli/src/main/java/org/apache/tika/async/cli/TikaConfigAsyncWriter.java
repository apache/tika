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

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.XMLReaderUtils;

class TikaConfigAsyncWriter {


    private static final Logger LOG = LoggerFactory.getLogger(TikaAsyncCLI.class);

    protected static final String FETCHER_NAME = "fsf";
    protected static final String EMITTER_NAME = "fse";

    private final SimpleAsyncConfig simpleAsyncConfig;

    TikaConfigAsyncWriter(SimpleAsyncConfig simpleAsyncConfig) {
        this.simpleAsyncConfig = simpleAsyncConfig;
    }

    void write(Path output) throws IOException {
        try {
            _write(output);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    void _write(Path output) throws ParserConfigurationException, TransformerException, IOException, TikaException, SAXException {
        Document document = null;
        Element properties = null;
        if (simpleAsyncConfig.getTikaConfig() != null) {
            document = XMLReaderUtils.buildDOM(Paths.get(simpleAsyncConfig.getTikaConfig()));
            properties = document.getDocumentElement();
            if (! "properties".equals(properties.getLocalName())) {
                throw new TikaConfigException("Document element must be '<properties>' in " +
                        simpleAsyncConfig.getTikaConfig());
            }
        } else {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            document = dbf.newDocumentBuilder().newDocument();
            properties = document.createElement("properties");
            document.appendChild(properties);
        }
        Path baseInput = Paths.get(simpleAsyncConfig.getInputDir());
        Path baseOutput = Paths.get(simpleAsyncConfig.getOutputDir());
        if (Files.isRegularFile(baseInput)) {
            baseInput = baseInput.toAbsolutePath().getParent();
            if (baseInput == null) {
                throw new IllegalArgumentException("input file must be at least one directory below root");
            }
        }

        Transformer transformer = TransformerFactory
                .newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            StreamResult result = new StreamResult(writer);
            DOMSource source = new DOMSource(document);
            transformer.transform(source, result);
        }

    }
}
