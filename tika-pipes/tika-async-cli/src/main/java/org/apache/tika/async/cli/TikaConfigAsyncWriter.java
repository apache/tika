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

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.tika.utils.StringUtils;

class TikaConfigAsyncWriter {

    private static final String FETCHER_NAME = "fsf";
    private static final String EMITTER_NAME = "fse";

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

    void _write(Path output) throws ParserConfigurationException, TransformerException, IOException {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document document = dbf.newDocumentBuilder().newDocument();
        Element properties = document.createElement("properties");
        document.appendChild(properties);
        writePipesIterator(document, properties);
        writeFetchers(document, properties);
        writeEmitters(document, properties);
        writeAsync(document, properties);
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

    private void writePipesIterator(Document document, Element properties) {
        if (! StringUtils.isBlank(simpleAsyncConfig.getFileList())) {
            writeFileListIterator(document, properties);
        } else {
            writeFileSystemIterator(document, properties);
        }
    }

    private void writeFileSystemIterator(Document document, Element properties) {
        Element pipesIterator = createAndGetElement(document, properties, "pipesIterator",
                "class", "org.apache.tika.pipes.pipesiterator.fs.FileSystemPipesIterator");
        appendTextElement(document, pipesIterator, "basePath",
                Paths.get(simpleAsyncConfig.getInputDir()).toAbsolutePath().toString());
        appendTextElement(document, pipesIterator, "fetcherName", FETCHER_NAME);
        appendTextElement(document, pipesIterator, "emitterName", EMITTER_NAME);
    }

    private void writeFileListIterator(Document document, Element properties) {
        Element pipesIterator = createAndGetElement(document, properties, "pipesIterator",
                "class", "org.apache.tika.pipes.pipesiterator.filelist.FileListPipesIterator");
        appendTextElement(document, pipesIterator, "fetcherName", FETCHER_NAME);
        appendTextElement(document, pipesIterator, "emitterName", EMITTER_NAME);
        appendTextElement(document, pipesIterator, "fileList",
                Paths.get(simpleAsyncConfig.getFileList()).toAbsolutePath().toString());
        appendTextElement(document, pipesIterator, "hasHeader", "false");
    }

    private void writeEmitters(Document document, Element properties) {
        Element emitters = createAndGetElement(document, properties, "emitters");
        Element emitter = createAndGetElement( document, emitters, "emitter",
                "class", "org.apache.tika.pipes.emitter.fs.FileSystemEmitter");
        appendTextElement(document, emitter, "name", EMITTER_NAME);
        appendTextElement(document, emitter, "basePath",
                Paths.get(simpleAsyncConfig.getOutputDir()).toAbsolutePath().toString());
    }

    private void writeFetchers(Document document, Element properties) {
        Element fetchers = createAndGetElement(document, properties, "fetchers");
        Element fetcher = createAndGetElement(document, fetchers, "fetcher",
                "class", "org.apache.tika.pipes.fetcher.fs.FileSystemFetcher");
        appendTextElement(document, fetcher, "name", FETCHER_NAME);
        if (!StringUtils.isBlank(simpleAsyncConfig.getInputDir())) {
            appendTextElement(document, fetcher, "basePath", Paths
                    .get(simpleAsyncConfig.getInputDir())
                    .toAbsolutePath()
                    .toString());
        } else {
            appendTextElement(document, fetcher, "basePath", "");
        }
    }

    private void writeAsync(Document document, Element properties) {
        Element async = createAndGetElement(document, properties, "async");
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

}
