package org.apache.tika.example;
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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.language.translate.DefaultTranslator;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;


/**
 * This class shows how to dump a TikaConfig object to a configuration file.
 * This allows users to easily dump the default TikaConfig as a base from which
 * to start if they want to modify the default configuration file.
 * <p>
 * For those who want to modify the mimes file, take a look at
 * tika-core/src/main/resources/org/apache/tika/mime/tika-mimetypes.xml
 * for inspiration.  Consider adding org/apache/tika/mime/custom-mimetypes.xml
 * for your custom mime types.
 */
public class DumpTikaConfigExample {

    /**
     *
     * @param config config file to dump
     * @param writer writer to which to write
     * @throws Exception
     */
    public void dump(TikaConfig config, Writer writer, String encoding) throws Exception {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        // root elements
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("properties");

        doc.appendChild(rootElement);
        addMimeComment(rootElement, doc);
        addTranslator(rootElement, doc, config);
        addDetectors(rootElement, doc, config);
        addParsers(rootElement, doc, config);


        //now write
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, encoding);
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(writer);

        transformer.transform(source, result);
    }

    private void addTranslator(Element rootElement, Document doc, TikaConfig config) {
        //TikaConfig only reads the first translator from the list,
        //but it looks like it expects a list
        Translator translator = config.getTranslator();
        if (translator instanceof DefaultTranslator) {
            Node mimeComment = doc.createComment(
                    "for example: "+
                            "<translator class=\"org.apache.tika.language.translate.GoogleTranslator\"/>");
            rootElement.appendChild(mimeComment);
        } else {
            Element translatorElement = doc.createElement("translator");
            translatorElement.setAttribute("class", translator.getClass().getCanonicalName());
            rootElement.appendChild(translatorElement);
        }
    }

    private void addMimeComment(Element rootElement, Document doc) {
        Node mimeComment = doc.createComment(
                "for example: <mimeTypeRepository resource=\"/org/apache/tika/mime/tika-mimetypes.xml\"/>");
        rootElement.appendChild(mimeComment);
    }

    private void addDetectors(Element rootElement, Document doc, TikaConfig config) throws Exception {
        Detector detector = config.getDetector();
        Element detectorsElement = doc.createElement("detectors");

        if (detector instanceof DefaultDetector) {
            List<Detector> children = ((DefaultDetector)detector).getDetectors();
            for (Detector d : children) {
                Element detectorElement = doc.createElement("detector");
                detectorElement.setAttribute("class", d.getClass().getCanonicalName());
                detectorsElement.appendChild(detectorElement);
            }
        }
        rootElement.appendChild(detectorsElement);
    }

    private void addParsers(Element rootElement, Document doc, TikaConfig config) throws Exception {
        Map<String, Parser> parsers = getConcreteParsers(config.getParser());

        Element parsersElement = doc.createElement("parsers");
        rootElement.appendChild(parsersElement);

        ParseContext context = new ParseContext();
        for (Map.Entry<String, Parser> e : parsers.entrySet()) {
            Element parserElement = doc.createElement("parser");
            Parser child = e.getValue();
            String className = e.getKey();
            parserElement.setAttribute("class", className);
            Set<MediaType> types = new TreeSet<MediaType>();
            types.addAll(child.getSupportedTypes(context));
            for (MediaType type : types){
                Element mimeElement = doc.createElement("mime");
                mimeElement.appendChild(doc.createTextNode(type.toString()));
                parserElement.appendChild(mimeElement);
            }
            parsersElement.appendChild(parserElement);
        }
        rootElement.appendChild(parsersElement);

    }

    private Map<String, Parser> getConcreteParsers(Parser parentParser)throws TikaException, IOException  {
        Map<String, Parser> parsers = new TreeMap<String, Parser>();
        if (parentParser instanceof CompositeParser) {
            addParsers((CompositeParser)parentParser, parsers);
        } else {
            addParser(parentParser, parsers);
        }
        return parsers;
    }

    private void addParsers(CompositeParser p, Map<String, Parser> parsers) {
        for (Parser child : p.getParsers().values()) {
            if (child instanceof CompositeParser) {
                addParsers((CompositeParser)child, parsers);
            } else {
                addParser(child, parsers);
            }
        }
    }

    private void addParser(Parser p, Map<String, Parser> parsers) {
        parsers.put(p.getClass().getCanonicalName(), p);
    }

    /**
     *
     * @param args outputFile, outputEncoding, if args is empty, this prints to console
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        Charset encoding = IOUtils.UTF_8;
        Writer writer = null;
        if (args.length > 0) {
            writer = new OutputStreamWriter(new FileOutputStream(new File(args[0])), encoding);
        } else {
            writer = new StringWriter();
        }

        if (args.length > 1) {
            encoding = Charset.forName(args[1]);
        }
        DumpTikaConfigExample ex = new DumpTikaConfigExample();
        ex.dump(TikaConfig.getDefaultConfig(), writer, encoding.name());

        writer.flush();

        if (writer instanceof StringWriter) {
            System.out.println(writer.toString());
        }
        writer.close();
    }
}
