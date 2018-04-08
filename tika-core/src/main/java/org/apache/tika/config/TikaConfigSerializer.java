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
package org.apache.tika.config;

import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.DefaultDetector;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.language.translate.DefaultTranslator;
import org.apache.tika.language.translate.Translator;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.CompositeParser;
import org.apache.tika.parser.DefaultParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.multiple.AbstractMultipleParser;
import org.apache.tika.utils.XMLReaderUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class TikaConfigSerializer {

    public enum Mode {
        /** Minimal version of the config, defaults where possible */
        MINIMAL, 
        /** Current config, roughly as loaded */
        CURRENT, 
        /** Static version of the config, with explicit lists of parsers/decorators/etc */
        STATIC,
        /** 
         * Static version of the config, with explicit lists of decorators etc,
         * and all parsers given with their detected supported mime types */
        STATIC_FULL;
    }

    /**
     *
     * @param config config to serialize
     * @param mode serialization mode
     * @param writer writer
     * @param charset charset
     * @throws Exception
     */
    public static void serialize(TikaConfig config, Mode mode, Writer writer, Charset charset)
            throws Exception {
        DocumentBuilder docBuilder = XMLReaderUtils.getDocumentBuilder();

        // root elements
        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("properties");

        doc.appendChild(rootElement);
        addMimeComment(mode, rootElement, doc);
        addServiceLoader(mode, rootElement, doc, config);
        addExecutorService(mode, rootElement, doc, config);
        addEncodingDetectors(mode, rootElement, doc, config);
        addTranslator(mode, rootElement, doc, config);
        addDetectors(mode, rootElement, doc, config);
        addParsers(mode, rootElement, doc, config);
        // TODO Service Loader section

        // now write
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, charset.name());
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(writer);

        transformer.transform(source, result);
    }

    private static void addExecutorService(Mode mode, Element rootElement, Document doc, TikaConfig config) {
        ExecutorService executor = config.getExecutorService();
        
        // TODO Implement the reverse of ExecutorServiceXmlLoader
        // TODO Make it possible to detect if we have the default executor
        // TODO Make it possible to get the current values from ConfigurableThreadPoolExecutor
    }

    private static void addServiceLoader(Mode mode, Element rootElement, Document doc, TikaConfig config) {
        ServiceLoader loader = config.getServiceLoader();

        if (mode == Mode.MINIMAL) {
            // Is this the default?
            if (loader.isDynamic() && loader.getLoadErrorHandler() == LoadErrorHandler.IGNORE) {
                // Default config, no need to output anything
                return;
            }
        }

        Element dslEl = doc.createElement("service-loader");
        dslEl.setAttribute("dynamic", Boolean.toString(loader.isDynamic()));
        dslEl.setAttribute("loadErrorHandler", loader.getLoadErrorHandler().toString());
        rootElement.appendChild(dslEl);
    }

    private static void addTranslator(Mode mode, Element rootElement, Document doc, TikaConfig config) {
        // Unlike the other entries, TikaConfig only wants one of
        //  these, and no outer <translators> list
        Translator translator = config.getTranslator();
        if (mode == Mode.MINIMAL && translator instanceof DefaultTranslator) {
            Node mimeComment = doc.createComment(
                    "for example: <translator class=\"org.apache.tika.language.translate.GoogleTranslator\"/>");
            rootElement.appendChild(mimeComment);
        } else {
            if (translator instanceof DefaultTranslator && 
                    (mode == Mode.STATIC || mode == Mode.STATIC_FULL)) {
                translator = ((DefaultTranslator)translator).getTranslator();
            }
            if (translator != null) {
                Element translatorElement = doc.createElement("translator");
                translatorElement.setAttribute("class", translator.getClass().getCanonicalName());
                rootElement.appendChild(translatorElement);
            } else {
                rootElement.appendChild(doc.createComment("No translators available"));
            }
        }
    }

    private static void addMimeComment(Mode mode, Element rootElement, Document doc) {
        Node mimeComment = doc.createComment(
                "for example: <mimeTypeRepository resource=\"/org/apache/tika/mime/tika-mimetypes.xml\"/>");
        rootElement.appendChild(mimeComment);
    }

    private static void addEncodingDetectors(Mode mode, Element rootElement, Document doc, TikaConfig config) throws Exception {
        EncodingDetector encDetector = config.getEncodingDetector();

        if (mode == Mode.MINIMAL && encDetector instanceof DefaultEncodingDetector) {
            // Don't output anything, all using defaults
            Node detComment = doc.createComment(
                    "for example: <encodingDetectors><encodingDetector class=\""
                    + "org.apache.tika.detect.DefaultEncodingDetector\"></encodingDetectors>");
            rootElement.appendChild(detComment);
            return;
        }

        Element encDetectorsElement = doc.createElement("encodingDetectors");
        if (mode == Mode.CURRENT && encDetector instanceof DefaultEncodingDetector ||
                ! (encDetector instanceof CompositeEncodingDetector)) {
            Element encDetectorElement = doc.createElement("encodingDetector");
            encDetectorElement.setAttribute("class", encDetector.getClass().getCanonicalName());
            encDetectorsElement.appendChild(encDetectorElement);
        } else {
            List<EncodingDetector> children = ((CompositeEncodingDetector)encDetector).getDetectors();
            for (EncodingDetector d : children) {
                Element encDetectorElement = doc.createElement("encodingDetector");
                encDetectorElement.setAttribute("class", d.getClass().getCanonicalName());
                encDetectorsElement.appendChild(encDetectorElement);
            }
        }
        rootElement.appendChild(encDetectorsElement);
    }

    private static void addDetectors(Mode mode, Element rootElement, Document doc, TikaConfig config) throws Exception {
        Detector detector = config.getDetector();

        if (mode == Mode.MINIMAL && detector instanceof DefaultDetector) {
            // Don't output anything, all using defaults
            Node detComment = doc.createComment(
                    "for example: <detectors><detector class=\"org.apache.tika.detector.MimeTypes\"></detectors>");
            rootElement.appendChild(detComment);
            return;
        }

        Element detectorsElement = doc.createElement("detectors");
        if (mode == Mode.CURRENT && detector instanceof DefaultDetector ||
                ! (detector instanceof CompositeDetector)) {
            Element detectorElement = doc.createElement("detector");
            detectorElement.setAttribute("class", detector.getClass().getCanonicalName());
            detectorsElement.appendChild(detectorElement);
        } else {
            List<Detector> children = ((CompositeDetector)detector).getDetectors();
            for (Detector d : children) {
                Element detectorElement = doc.createElement("detector");
                detectorElement.setAttribute("class", d.getClass().getCanonicalName());
                detectorsElement.appendChild(detectorElement);
            }
        }
        rootElement.appendChild(detectorsElement);
    }

    private static void addParsers(Mode mode, Element rootElement, Document doc, TikaConfig config) throws Exception {
        Parser parser = config.getParser();
        if (mode == Mode.MINIMAL && parser instanceof DefaultParser) {
            // Don't output anything, all using defaults
            return;
        } else if (mode == Mode.MINIMAL) {
            mode = Mode.CURRENT;
        }

        Element parsersElement = doc.createElement("parsers");
        rootElement.appendChild(parsersElement);

        addParser(mode, parsersElement, doc, parser);
    }

    private static void addParser(Mode mode, Element rootElement, Document doc, Parser parser) throws Exception {
        // If the parser is decorated, is it a kind where we output the parser inside?
        ParserDecorator decoration = null;
        if (parser instanceof ParserDecorator) {
            if (parser.getClass().getName().startsWith(ParserDecorator.class.getName()+"$")) {
                decoration = ((ParserDecorator)parser);
                parser = decoration.getWrappedParser();
            }
        }

        boolean outputParser = true;
        List<Parser> children = Collections.emptyList();
        if (mode == Mode.CURRENT && parser instanceof DefaultParser) {
            // Only output the parser, not the children
        } else if (parser instanceof CompositeParser) {
            children = ((CompositeParser)parser).getAllComponentParsers();
            // Special case for a naked composite
            if (parser.getClass().equals(CompositeParser.class)) {
                outputParser = false;
            }
            // Special case for making Default to static
            if (parser instanceof DefaultParser &&
                    (mode == Mode.STATIC || mode == Mode.STATIC_FULL)) {
                outputParser = false;
            }
        } else if (parser instanceof AbstractMultipleParser) {
            // Always output the parsers that go into the multiple
            children = ((AbstractMultipleParser)parser).getAllParsers();
        }

        if (outputParser) {
            rootElement = addParser(mode, rootElement, doc, parser, decoration);
        }
        for (Parser childParser : children) {
            addParser(mode, rootElement, doc, childParser);
        }
        // TODO Parser Exclusions
    }

    private static Element addParser(Mode mode, Element rootElement, Document doc, Parser parser, ParserDecorator decorator) throws Exception {
        ParseContext context = new ParseContext();

        Set<MediaType> addedTypes = new TreeSet<>();
        Set<MediaType> excludedTypes = new TreeSet<>();
        if (decorator != null) {
            Set<MediaType> types = new TreeSet<>();
            types.addAll(decorator.getSupportedTypes(context));
            addedTypes.addAll(types);

            for (MediaType type : parser.getSupportedTypes(context)) {
                if (! types.contains(type)) {
                    excludedTypes.add(type);
                }
                addedTypes.remove(type);
            }
        } else if (mode == Mode.STATIC_FULL) {
            addedTypes.addAll(parser.getSupportedTypes(context));
        }

        String className = parser.getClass().getCanonicalName();
        Element parserElement = doc.createElement("parser");
        parserElement.setAttribute("class", className);
        rootElement.appendChild(parserElement);

        // TODO Output configurable parameters in a genric way, see TIKA-1508
        if (parser instanceof AbstractMultipleParser) {
            Element paramsElement = doc.createElement("params");
            Element paramElement = doc.createElement("param");
            paramElement.setAttribute("name", "metadataPolicy");
            paramElement.setAttribute("value", ((AbstractMultipleParser)parser).getMetadataPolicy().toString());
            paramsElement.appendChild(paramElement);
            parserElement.appendChild(paramsElement);
        }
        
        for (MediaType type : addedTypes) {
            Element mimeElement = doc.createElement("mime");
            mimeElement.appendChild(doc.createTextNode(type.toString()));
            parserElement.appendChild(mimeElement);
        }
        for (MediaType type : excludedTypes) {
            Element mimeElement = doc.createElement("mime-exclude");
            mimeElement.appendChild(doc.createTextNode(type.toString()));
            parserElement.appendChild(mimeElement);
        }

        return parserElement;
    }

}
