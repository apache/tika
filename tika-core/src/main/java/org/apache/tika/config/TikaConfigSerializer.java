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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

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
import org.apache.tika.utils.StringUtils;
import org.apache.tika.utils.XMLReaderUtils;

public class TikaConfigSerializer {

    private static final Logger LOG = LoggerFactory.getLogger(TikaConfigSerializer.class);
    private static Map<Class, String> PRIMITIVES = new HashMap<>();

    static {
        PRIMITIVES.put(Integer.class, "int");
        PRIMITIVES.put(int.class, "int");
        PRIMITIVES.put(String.class, "string");
        PRIMITIVES.put(Boolean.class, "bool");
        PRIMITIVES.put(boolean.class, "bool");
        PRIMITIVES.put(Float.class, "float");
        PRIMITIVES.put(float.class, "float");
        PRIMITIVES.put(Double.class, "double");
        PRIMITIVES.put(double.class, "double");
        PRIMITIVES.put(Long.class, "long");
        PRIMITIVES.put(long.class, "long");
        PRIMITIVES.put(Map.class, "map");
        PRIMITIVES.put(List.class, "list");
    }

    /**
     * @param config  config to serialize
     * @param mode    serialization mode
     * @param writer  writer
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
        Transformer transformer = XMLReaderUtils.getTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.ENCODING, charset.name());
        DOMSource source = new DOMSource(doc);
        StreamResult result = new StreamResult(writer);

        transformer.transform(source, result);
    }

    private static void addExecutorService(Mode mode, Element rootElement, Document doc,
                                           TikaConfig config) {
        ExecutorService executor = config.getExecutorService();

        // TODO Implement the reverse of ExecutorServiceXmlLoader
        // TODO Make it possible to detect if we have the default executor
        // TODO Make it possible to get the current values from ConfigurableThreadPoolExecutor
    }

    private static void addServiceLoader(Mode mode, Element rootElement, Document doc,
                                         TikaConfig config) {
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

    private static void addTranslator(Mode mode, Element rootElement, Document doc,
                                      TikaConfig config) {
        // Unlike the other entries, TikaConfig only wants one of
        //  these, and no outer <translators> list
        Translator translator = config.getTranslator();
        if (mode == Mode.MINIMAL && translator instanceof DefaultTranslator) {
            Node mimeComment = doc.createComment("for example: <translator " +
                    "class=\"org.apache.tika.language.translate.GoogleTranslator\"/>");
            rootElement.appendChild(mimeComment);
        } else {
            if (translator instanceof DefaultTranslator &&
                    (mode == Mode.STATIC || mode == Mode.STATIC_FULL)) {
                translator = ((DefaultTranslator) translator).getTranslator();
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
        Node mimeComment = doc.createComment("for example: <mimeTypeRepository " +
                "resource=\"/org/apache/tika/mime/tika-mimetypes.xml\"/>");
        rootElement.appendChild(mimeComment);
    }

    private static void addEncodingDetectors(Mode mode, Element rootElement, Document doc,
                                             TikaConfig config) throws Exception {
        EncodingDetector encDetector = config.getEncodingDetector();

        if (mode == Mode.MINIMAL && encDetector instanceof DefaultEncodingDetector) {
            // Don't output anything, all using defaults
            Node detComment = doc.createComment(
                    "for example: <encodingDetectors><encodingDetector class=\"" +
                            "org.apache.tika.detect.DefaultEncodingDetector\">" +
                            "</encodingDetectors>");
            rootElement.appendChild(detComment);
            return;
        }

        Element encDetectorsElement = doc.createElement("encodingDetectors");
        if (mode == Mode.CURRENT && encDetector instanceof DefaultEncodingDetector ||
                !(encDetector instanceof CompositeEncodingDetector)) {
            Element encDetectorElement = doc.createElement("encodingDetector");
            encDetectorElement.setAttribute("class", encDetector.getClass().getCanonicalName());
            encDetectorsElement.appendChild(encDetectorElement);
        } else {
            List<EncodingDetector> children =
                    ((CompositeEncodingDetector) encDetector).getDetectors();
            for (EncodingDetector d : children) {
                Element encDetectorElement = doc.createElement("encodingDetector");
                encDetectorElement.setAttribute("class", d.getClass().getCanonicalName());
                serializeParams(doc, encDetectorElement, d);

                encDetectorsElement.appendChild(encDetectorElement);
            }
        }
        rootElement.appendChild(encDetectorsElement);
    }

    private static void addDetectors(Mode mode, Element rootElement, Document doc,
                                     TikaConfig config) throws Exception {
        Detector detector = config.getDetector();

        if (mode == Mode.MINIMAL && detector instanceof DefaultDetector) {
            // Don't output anything, all using defaults
            Node detComment = doc.createComment("for example: <detectors><detector " +
                    "class=\"org.apache.tika.detector.MimeTypes\"></detectors>");
            rootElement.appendChild(detComment);
            return;
        }

        Element detectorsElement = doc.createElement("detectors");
        if (mode == Mode.CURRENT && detector instanceof DefaultDetector ||
                !(detector instanceof CompositeDetector)) {
            Element detectorElement = doc.createElement("detector");
            detectorElement.setAttribute("class", detector.getClass().getCanonicalName());
            detectorsElement.appendChild(detectorElement);
        } else {
            List<Detector> children = ((CompositeDetector) detector).getDetectors();
            for (Detector d : children) {
                Element detectorElement = doc.createElement("detector");
                detectorElement.setAttribute("class", d.getClass().getCanonicalName());
                serializeParams(doc, detectorElement, d);
                detectorsElement.appendChild(detectorElement);
            }
        }
        rootElement.appendChild(detectorsElement);
    }

    private static void addParsers(Mode mode, Element rootElement, Document doc, TikaConfig config)
            throws Exception {
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

    private static void addParser(Mode mode, Element rootElement, Document doc, Parser parser)
            throws Exception {
        // If the parser is decorated, is it a kind where we output the parser inside?
        ParserDecorator decoration = null;
        if (parser instanceof ParserDecorator) {
            if (parser.getClass().getName().startsWith(ParserDecorator.class.getName() + "$")) {
                decoration = ((ParserDecorator) parser);
                parser = decoration.getWrappedParser();
            }
        }

        boolean outputParser = true;
        List<Parser> children = Collections.emptyList();
        if (mode == Mode.CURRENT && parser instanceof DefaultParser) {
            // Only output the parser, not the children
        } else if (parser instanceof CompositeParser) {
            children = ((CompositeParser) parser).getAllComponentParsers();
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
            children = ((AbstractMultipleParser) parser).getAllParsers();
        }

        if (outputParser) {
            rootElement = addParser(mode, rootElement, doc, parser, decoration);
        }
        for (Parser childParser : children) {
            addParser(mode, rootElement, doc, childParser);
        }
        // TODO Parser Exclusions
    }

    private static Element addParser(Mode mode, Element rootElement, Document doc, Parser parser,
                                     ParserDecorator decorator) throws Exception {
        ParseContext context = new ParseContext();

        Set<MediaType> addedTypes = new TreeSet<>();
        Set<MediaType> excludedTypes = new TreeSet<>();
        if (decorator != null) {
            Set<MediaType> types = new TreeSet<>(decorator.getSupportedTypes(context));
            addedTypes.addAll(types);

            for (MediaType type : parser.getSupportedTypes(context)) {
                if (!types.contains(type)) {
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

        serializeParams(doc, parserElement, parser);

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

    public static void serializeParams(Document doc, Element element, Object object) {
        Matcher setterMatcher = Pattern.compile("\\Aset([A-Z].*)").matcher("");
        Matcher getterMatcher = Pattern.compile("\\A(?:get|is)([A-Z].+)\\Z").matcher("");

        //TODO -- check code base for setters with lowercase initial letters?!
        MethodTuples nonPrimitiveSetters = new MethodTuples();
        MethodTuples primitiveSetters = new MethodTuples();
        MethodTuples nonPrimitiveGetters = new MethodTuples();
        MethodTuples primitiveGetters = new MethodTuples();
        for (Method method : object.getClass().getDeclaredMethods()) {
            Class[] parameterTypes = method.getParameterTypes();

            if (setterMatcher.reset(method.getName()).find()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    //we could just call getMethods, but this can be helpful debugging inf
                    LOG.trace("inaccessible setter: {} in {}", method.getName(), object.getClass());
                    continue;
                }
                //require @Field on setters
                if (method.getAnnotation(Field.class) == null) {
                   // LOG.warn("unannotated setter {} in {}", method.getName(), object.getClass());
                    continue;
                }
                if (parameterTypes.length != 1) {
                    //TODO -- check code base for setX() zero parameters that set boolean to true
                    LOG.warn("setter with wrong number of params " + method.getName() + " " + parameterTypes.length);
                    continue;
                }
                String paramName = methodToParamName(setterMatcher.group(1));
                if (PRIMITIVES.containsKey(parameterTypes[0])) {
                    primitiveSetters.add(new MethodTuple(paramName, method, parameterTypes[0]));
                } else {
                    nonPrimitiveSetters.add(new MethodTuple(paramName, method, parameterTypes[0]));
                }
            } else if (getterMatcher.reset(method.getName()).find()) {
                if (parameterTypes.length != 0) {
                    //require 0 parameters for the getter
                    continue;
                }
                String paramName = methodToParamName(getterMatcher.group(1));
                if (PRIMITIVES.containsKey(method.getReturnType())) {
                    primitiveGetters.add(new MethodTuple(paramName, method, method.getReturnType()));
                } else {
                    nonPrimitiveGetters.add(new MethodTuple(paramName, method, method.getReturnType()));
                }

            }
        }

        //TODO -- remove nonprimitive setters/getters that have a string equivalent
        serializePrimitives(doc, element, object, primitiveSetters, primitiveGetters);
        serializeNonPrimitives(doc, element, object, nonPrimitiveSetters, nonPrimitiveGetters);

    }

    private static String methodToParamName(String name) {
        if (StringUtils.isBlank(name)) {
            return name;
        }
        return name.substring(0, 1).toLowerCase(Locale.US) + name.substring(1);

    }

    private static void serializeNonPrimitives(Document doc, Element element,
                                               Object object,
                                               MethodTuples setterTuples,
                                               MethodTuples getterTuples) {

        for (Map.Entry<String, Set<MethodTuple>> e : setterTuples.tuples.entrySet()) {
            Set<MethodTuple> getters = getterTuples.tuples.get(e.getKey());
            processNonPrimitive(e.getKey(), e.getValue(), getters, doc, element, object);
            if (!getterTuples.tuples.containsKey(e.getKey())) {
                LOG.warn("no getter for setter non-primitive: {} in {}", e.getKey(),
                        object.getClass());
                continue;
            }
        }
    }

    private static void processNonPrimitive(String name, Set<MethodTuple> setters,
                                            Set<MethodTuple> getters, Document doc, Element element,
                                            Object object) {
        for (MethodTuple setter : setters) {
            for (MethodTuple getter : getters) {
                if (setter.singleParam.equals(getter.singleParam)) {
                    serializeObject(name, doc, element, setter, getter, object);
                    return;
                }
            }
        }
    }

    private static void serializeObject(String name, Document doc, Element element,
                                        MethodTuple setter,
                                         MethodTuple getter, Object object) {

        Object item = null;
        try {
            item = getter.method.invoke(object);
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.warn("couldn't get " + name + " on " + object.getClass(), e);
            return;
        }
        if (item == null) {
            LOG.warn("Getter {} on {} returned null", getter.name, object.getClass());
        }
        Element entry = doc.createElement(name);
        entry.setAttribute("class", item.getClass().getCanonicalName());
        element.appendChild(entry);
        serializeParams(doc, element, item);
    }

    private static void serializePrimitives(Document doc, Element root,
                                            Object object,
                                            MethodTuples setterTuples, MethodTuples getterTuples) {

        Element paramsElement = null;
        if (object instanceof AbstractMultipleParser) {
            paramsElement = doc.createElement("params");
            Element paramElement = doc.createElement("param");
            paramElement.setAttribute("name", "metadataPolicy");
            paramElement.setAttribute("value",
                    ((AbstractMultipleParser) object).getMetadataPolicy().toString());
            paramsElement.appendChild(paramElement);
            root.appendChild(paramsElement);
        }
        for (Map.Entry<String, Set<MethodTuple>> e : setterTuples.tuples.entrySet()) {
            if (!getterTuples.tuples.containsKey(e.getKey())) {
                LOG.info("no getter for setter: {} in {}", e.getKey(), object.getClass());
                continue;
            }
            Set<MethodTuple> getters = getterTuples.tuples.get(e.getKey());
            Set<MethodTuple> setters = e.getValue();
            MethodTuple getterTuple = null;
            for (MethodTuple getterCandidate : getters) {
                for (MethodTuple setter : setters) {
                    if (getterCandidate.singleParam.equals(setter.singleParam)) {
                        getterTuple = getterCandidate;
                        break;
                    }
                }
            }

            if (getterTuple == null) {
                LOG.debug("Could not find getter to match setter for: {}", e.getKey());
                continue;
            }
            Object value = null;
            try {
                value = getterTuple.method.invoke(object);
            } catch (IllegalAccessException ex) {
                LOG.error("couldn't invoke " + getterTuple, ex);
                continue;
            } catch (InvocationTargetException ex) {
                LOG.error("couldn't invoke " + getterTuple, ex);
                continue;
            }
            if (value == null) {
                LOG.debug("null value: {} in {}", getterTuple.name, object.getClass());
            }
            String valString = (value == null) ? "" : value.toString();
            Element param = doc.createElement("param");
            param.setAttribute("name", getterTuple.name);
            param.setAttribute("type", PRIMITIVES.get(getterTuple.singleParam));
            if (List.class.isAssignableFrom(getterTuple.singleParam)) {
                //this outputs even empty list elements, which I think is good.
                addList(param, doc, getterTuple, (List<String>) value);
            } else if (Map.class.isAssignableFrom(getterTuple.singleParam)) {
                //this outputs even empty lists, which I think is good.
                addMap(param, doc, getterTuple, (Map<String, String>) value);
            } else {
                param.setTextContent(valString);
            }
            if (paramsElement == null) {
                paramsElement = doc.createElement("params");
                root.appendChild(paramsElement);
            }
            paramsElement.appendChild(param);
        }
    }

    private static void addMap(Element param, Document doc, MethodTuple getterTuple,
                               Map<String, String> object) {
        for (Map.Entry<String, String> e : new TreeMap<String, String>(object).entrySet()) {
            Element element = doc.createElement("string");
            element.setAttribute("key", e.getKey());
            element.setAttribute("value", e.getValue());
            param.appendChild(element);
        }

    }

    private static void addList(Element param, Document doc, MethodTuple getterTuple,
                                List<String> list) {
        for (String s : list) {
            Element element = doc.createElement("string");
            element.setTextContent(s);
            param.appendChild(element);
        }
    }

    private static Method findGetter(MethodTuple setter, Object object) {
        Matcher m = Pattern.compile("\\A(?:get|is)([A-Z].+)\\Z").matcher("");
        for (Method method : object.getClass().getMethods()) {
            if (object.getClass().getName().contains("PDF")) {
                System.out.println(method.getName());
            }
            if (m.reset(method.getName()).find()) {
                if (object.getClass().getName().contains("PDF")) {
                    System.out.println("2: " + method.getName());
                }
                String paramName = m.group(1);
                if (setter.name.equals(paramName)) {
                    Class returnType = method.getReturnType();
                    if (setter.singleParam.equals(returnType)) {
                        return method;
                    }
                }
            }
        }
        return null;
    }

    private static MethodTuple pickBestSetter(Set<MethodTuple> tuples) {
        //TODO -- if both string and integer, which one do we pick?
        //stub for now -- just pick the first
        for (MethodTuple t : tuples) {
            return t;
        }
        return null;
    }

    private static class MethodTuples {
        Map<String, Set<MethodTuple>> tuples = new TreeMap<>();

        public void add(MethodTuple tuple) {
            Set<MethodTuple> set = tuples.get(tuple.name);
            if (set == null) {
                set = new HashSet<>();
                tuples.put(tuple.name, set);
            }
            set.add(tuple);
        }

        public int getSize() {
            return tuples.size();
        }
    }
    private static class MethodTuple {
        String name;
        Method method;
        Class singleParam;

        public MethodTuple(String name, Method method, Class singleParam) {
            this.name = name;
            this.method = method;
            this.singleParam = singleParam;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MethodTuple that = (MethodTuple) o;
            return name.equals(that.name) && method.equals(that.method) &&
                    singleParam.equals(that.singleParam);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, method, singleParam);
        }
    }
    public enum Mode {
        /**
         * Minimal version of the config, defaults where possible
         */
        MINIMAL,
        /**
         * Current config, roughly as loaded
         */
        CURRENT,
        /**
         * Static version of the config, with explicit lists of parsers/decorators/etc
         */
        STATIC,
        /**
         * Static version of the config, with explicit lists of decorators etc,
         * and all parsers given with their detected supported mime types
         */
        STATIC_FULL
    }

}
