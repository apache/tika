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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.StringUtils;
import org.apache.tika.utils.XMLReaderUtils;

class TikaConfigAsyncWriter {


    private static final Logger LOG = LoggerFactory.getLogger(TikaAsyncCLI.class);

    protected static final String FETCHER_NAME = "file-system-fetcher";
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

        writePipesIterator(document, properties, baseInput);
        writeEmitters(document, properties, baseOutput);
        writeAsync(document, properties, output);
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

    private void writePipesIterator(Document document, Element properties, Path baseInput) {
        Element pipesIterator = findChild("pipesIterator", properties);
        if (pipesIterator != null) {
            LOG.info("pipesIterator already exists in tika-config. Not overwriting with commandline");
            return;
        }
        if (! StringUtils.isBlank(simpleAsyncConfig.getFileList())) {
            writeFileListIterator(document, properties, baseInput);
        } else {
            writeFileSystemIterator(document, properties, baseInput);
        }
    }

    private void writeFileSystemIterator(Document document, Element properties, Path baseInput) {
        Element pipesIterator = createAndGetElement(document, properties, "pipesIterator",
                "class", "org.apache.tika.pipes.pipesiterator.fs.FileSystemPipesIterator");
        appendTextElement(document, pipesIterator, "basePath", baseInput.toAbsolutePath().toString());
        appendTextElement(document, pipesIterator, "emitterName", EMITTER_NAME);
    }

    private void writeFileListIterator(Document document, Element properties, Path baseInput) {
        Element pipesIterator = createAndGetElement(document, properties, "pipesIterator",
                "class", "org.apache.tika.pipes.pipesiterator.filelist.FileListPipesIterator");
        appendTextElement(document, pipesIterator, "emitterName", EMITTER_NAME);
        appendTextElement(document, pipesIterator, "fileList",
                baseInput.toAbsolutePath().toString());
        appendTextElement(document, pipesIterator, "hasHeader", "false");
    }

    private void writeEmitters(Document document, Element properties, Path baseOutput) {
        Element emitters = findChild("emitters", properties);
        if (emitters != null) {
            LOG.info("emitters already exist in tika-config. Not overwriting with commandline");
            return;
        }

        emitters = createAndGetElement(document, properties, "emitters");
        Element emitter = createAndGetElement( document, emitters, "emitter",
                "class", "org.apache.tika.pipes.emitter.fs.FileSystemEmitter");
        appendTextElement(document, emitter, "name", EMITTER_NAME);
        appendTextElement(document, emitter, "basePath", baseOutput.toAbsolutePath().toString());
    }

    private void writeAsync(Document document, Element properties, Path thisTikaConfig) {
        Element async = findChild("async", properties);
        if (async != null) {
            LOG.info("async already exists in tika-config. Not overwriting with commandline");
            return;
        }

        async = createAndGetElement(document, properties, "async");
        Element pipesIterator = findChild("pipesIterator", properties);
        if (pipesIterator != null) {
            LOG.info("pipesIterator already exists in tika-config. Not overwriting with commandline");
        }

        properties.appendChild(async);
        if (simpleAsyncConfig.getNumClients() != null) {
            appendTextElement(document, async, "numClients", Integer.toString(simpleAsyncConfig.getNumClients()));
        }
        if (simpleAsyncConfig.getXmx() != null) {
            Element forkedJvmArgs = createAndGetElement(document, async, "forkedJvmArgs");
            appendTextElement(document, forkedJvmArgs, "arg", "-Xmx" + simpleAsyncConfig.getXmx());
        }
        if (simpleAsyncConfig.getTimeoutMs() != null) {
            appendTextElement(document, async, "timeoutMillis", Long.toString(simpleAsyncConfig.getTimeoutMs()));
        }
        appendTextElement(document, async, "tikaConfig", thisTikaConfig.toAbsolutePath().toString());

        appendTextElement(document, async, "maxForEmitBatchBytes", "0");
    }

    private static  void appendTextElement(Document document, Element parent, String itemName, String text, String... attrs) {
        Element el = createAndGetElement(document, parent, itemName, attrs);
        el.setTextContent(text);
    }

    private static Element createAndGetElement(Document document, Element parent, String elementName, String... attrs) {
        Element el = document.createElement(elementName);
        parent.appendChild(el);
        for (int i = 0; i < attrs.length; i += 2) {
            el.setAttribute(attrs[i], attrs[i + 1]);
        }
        return el;
    }

    static Element findChild(String childElementName, Element root) {
        NodeList nodeList = root.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node child = nodeList.item(i);
            if (childElementName.equals(child.getLocalName())) {
                return (Element)child;
            }
        }
        return null;
    }

}
