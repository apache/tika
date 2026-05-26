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
package org.apache.tika.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.config.loader.ComponentRegistry;
import org.apache.tika.config.loader.KebabCaseConverter;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Converts legacy XML Tika configuration files to the new JSON format.
 * <p>
 * Currently supports converting the "parsers" section of tika-config.xml files
 * for parsers in the tika-parsers-standard module.
 * <p>
 * Supports parameter types: bool, int, long, double, float, string, list, and map.
 * <p>
 * <strong>Special Case:</strong> TesseractOCR's {@code otherTesseractSettings} list
 * (containing space-delimited key-value pairs) is automatically converted to the
 * {@code otherTesseractConfig} map format expected by the JSON configuration.
 * <p>
 * Example usage:
 * <pre>
 * XmlToJsonConfigConverter.convert(
 *     Paths.get("tika-config.xml"),
 *     Paths.get("tika-config.json")
 * );
 * </pre>
 *
 * <p>XML Format (with various parameter types):
 * <pre>
 * &lt;properties&gt;
 *   &lt;parsers&gt;
 *     &lt;parser class="org.apache.tika.parser.pdf.PDFParser"&gt;
 *       &lt;params&gt;
 *         &lt;param name="sortByPosition" type="bool"&gt;true&lt;/param&gt;
 *         &lt;param name="maxPages" type="int"&gt;1000&lt;/param&gt;
 *       &lt;/params&gt;
 *     &lt;/parser&gt;
 *     &lt;parser class="org.apache.tika.parser.ocr.TesseractOCRParser"&gt;
 *       &lt;params&gt;
 *         &lt;!-- Special case: space-delimited key-value pairs --&gt;
 *         &lt;param name="otherTesseractSettings" type="list"&gt;
 *           &lt;string&gt;textord_initialx_ile 0.75&lt;/string&gt;
 *           &lt;string&gt;textord_noise_hfract 0.15625&lt;/string&gt;
 *         &lt;/param&gt;
 *         &lt;param name="envVars" type="map"&gt;
 *           &lt;TESSDATA_PREFIX&gt;/usr/share/tesseract&lt;/TESSDATA_PREFIX&gt;
 *         &lt;/param&gt;
 *       &lt;/params&gt;
 *     &lt;/parser&gt;
 *     &lt;parser class="org.apache.tika.parser.DefaultParser"&gt;
 *       &lt;parser-exclude class="org.apache.tika.parser.pdf.PDFParser"/&gt;
 *     &lt;/parser&gt;
 *   &lt;/parsers&gt;
 * &lt;/properties&gt;
 * </pre>
 *
 * <p>JSON Format:
 * <pre>
 * {
 *   "parsers": [
 *     {
 *       "pdf-parser": {
 *         "sortByPosition": true,
 *         "maxPages": 1000
 *       }
 *     },
 *     {
 *       "tesseract-ocr-parser": {
 *         "otherTesseractConfig": {
 *           "textord_initialx_ile": "0.75",
 *           "textord_noise_hfract": "0.15625"
 *         },
 *         "envVars": {
 *           "TESSDATA_PREFIX": "/usr/share/tesseract"
 *         }
 *       }
 *     },
 *     {
 *       "default-parser": {
 *         "exclude": ["pdf-parser"]
 *       }
 *     }
 *   ]
 * }
 * </pre>
 */
public class XmlToJsonConfigConverter {

    private static final Logger LOG = LoggerFactory.getLogger(XmlToJsonConfigConverter.class);

    // Use a plain ObjectMapper for clean JSON output without @class annotations
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private XmlToJsonConfigConverter() {
        // Utility class
    }

    /**
     * Converts an XML Tika configuration file to JSON format.
     *
     * @param xmlPath path to the XML configuration file
     * @param jsonPath path where the JSON output should be written
     * @throws TikaConfigException if conversion fails
     * @throws IOException if file I/O fails
     */
    public static void convert(Path xmlPath, Path jsonPath) throws TikaConfigException, IOException {
        try (InputStream in = Files.newInputStream(xmlPath);
             OutputStream out = Files.newOutputStream(jsonPath)) {
            convert(in, out);
        }
    }

    /**
     * Converts an XML Tika configuration stream to JSON format.
     *
     * @param xmlInput input stream containing XML configuration
     * @param jsonOutput output stream where JSON will be written
     * @throws TikaConfigException if conversion fails
     * @throws IOException if stream I/O fails
     */
    public static void convert(InputStream xmlInput, OutputStream jsonOutput)
            throws TikaConfigException, IOException {
        convert(xmlInput, jsonOutput, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Converts an XML Tika configuration stream to JSON format.
     *
     * @param xmlInput input stream containing XML configuration
     * @param jsonOutput output stream where JSON will be written
     * @param classLoader class loader to use for component registry
     * @throws TikaConfigException if conversion fails
     * @throws IOException if stream I/O fails
     */
    public static void convert(InputStream xmlInput, OutputStream jsonOutput, ClassLoader classLoader)
            throws TikaConfigException, IOException {
        try {
            // Load component registry to properly map class names to component names
            ComponentRegistry parserRegistry = new ComponentRegistry("parsers", classLoader);

            Document doc = XMLReaderUtils.buildDOM(xmlInput);
            Map<String, Object> jsonConfig = convertDocument(doc, parserRegistry);

            try (Writer writer = new OutputStreamWriter(jsonOutput, StandardCharsets.UTF_8)) {
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(writer, jsonConfig);
            }
        } catch (Exception e) {
            throw new TikaConfigException("Failed to convert XML config to JSON", e);
        }
    }

    /**
     * Converts the entire XML configuration document to a JSON-compatible map.
     */
    private static Map<String, Object> convertDocument(Document doc, ComponentRegistry parserRegistry)
            throws TikaConfigException {
        Map<String, Object> result = new LinkedHashMap<>();

        Element root = doc.getDocumentElement();
        if (!"properties".equals(root.getNodeName())) {
            throw new TikaConfigException(
                    "Invalid XML config: root element must be <properties>, found: " +
                    root.getNodeName());
        }

        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) child;
            String sectionName = element.getNodeName();

            if ("parsers".equals(sectionName)) {
                result.put("parsers", convertParsersSection(element, parserRegistry));
            }
            // Future: add support for detectors, translators, etc.
        }

        return result;
    }

    /**
     * Converts the &lt;parsers&gt; section to JSON array format.
     */
    private static List<Map<String, Object>> convertParsersSection(Element parsersElement,
                                                                     ComponentRegistry parserRegistry)
            throws TikaConfigException {
        List<Map<String, Object>> parsersList = new ArrayList<>();

        NodeList parserNodes = parsersElement.getElementsByTagName("parser");
        for (int i = 0; i < parserNodes.getLength(); i++) {
            Element parserElement = (Element) parserNodes.item(i);
            Map<String, Object> parserEntry = convertParserElement(parserElement, parserRegistry);
            if (parserEntry != null && !parserEntry.isEmpty()) {
                parsersList.add(parserEntry);
            }
        }

        // Check for redundant exclusions and inform users
        checkForRedundantExclusions(parsersList);

        return parsersList;
    }

    /**
     * Checks if parsers are excluded from default-parser but also configured separately,
     * which is redundant. Logs INFO messages to help users understand they can remove
     * the exclusion since configured parsers automatically override the default.
     */
    private static void checkForRedundantExclusions(List<Map<String, Object>> parsersList) {
        // Find exclusions from default-parser
        Set<String> excludedParsers = new HashSet<>();
        for (Map<String, Object> parserEntry : parsersList) {
            if (parserEntry.containsKey("default-parser")) {
                Map<?, ?> config = (Map<?, ?>) parserEntry.get("default-parser");
                if (config.containsKey("exclude")) {
                    @SuppressWarnings("unchecked")
                    List<String> excludes = (List<String>) config.get("exclude");
                    excludedParsers.addAll(excludes);
                }
            }
        }

        // Find configured parsers
        Set<String> configuredParsers = new HashSet<>();
        for (Map<String, Object> parserEntry : parsersList) {
            for (String parserName : parserEntry.keySet()) {
                if (!"default-parser".equals(parserName)) {
                    configuredParsers.add(parserName);
                }
            }
        }

        // Check for overlap and log informational messages
        Set<String> redundantExclusions = new HashSet<>(excludedParsers);
        redundantExclusions.retainAll(configuredParsers);

        if (!redundantExclusions.isEmpty()) {
            LOG.info("=".repeat(80));
            LOG.info("CONFIGURATION OPTIMIZATION NOTICE");
            LOG.info("=".repeat(80));
            LOG.info("");
            LOG.info("The following parsers are excluded from default-parser but also configured separately:");
            for (String parserName : redundantExclusions) {
                LOG.info("  - {}", parserName);
            }
            LOG.info("");
            LOG.info("This exclusion is redundant. When you configure a parser with specific settings,");
            LOG.info("the loader excludes loading that parser from SPI. You can remove these");
            LOG.info("exclusions from your default-parser configuration.");
            LOG.info("");
            LOG.info("Example - Instead of:");
            LOG.info("  {");
            LOG.info("    \"default-parser\": {");
            LOG.info("      \"exclude\": [\"pdf-parser\"]");
            LOG.info("    }");
            LOG.info("  },");
            LOG.info("  {");
            LOG.info("    \"pdf-parser\": {");
            LOG.info("      \"sortByPosition\": true");
            LOG.info("    }");
            LOG.info("  }");
            LOG.info("");
            LOG.info("Simply use:");
            LOG.info("  {");
            LOG.info("    \"default-parser\": {},");
            LOG.info("    \"pdf-parser\": {");
            LOG.info("      \"sortByPosition\": true");
            LOG.info("    }");
            LOG.info("  }");
            LOG.info("");
            LOG.info("=".repeat(80));
        }
    }

    /**
     * Converts a single &lt;parser&gt; element to a JSON map entry.
     *
     * @return map with single entry: { "parser-name": { config... } }
     */
    private static Map<String, Object> convertParserElement(Element parserElement,
                                                             ComponentRegistry parserRegistry)
            throws TikaConfigException {
        String className = parserElement.getAttribute("class");
        if (className == null || className.isEmpty()) {
            throw new TikaConfigException("Parser element missing 'class' attribute");
        }

        // Convert class name to component name using the registry
        String componentName = classNameToComponentName(className, parserRegistry);

        Map<String, Object> config = new LinkedHashMap<>();
        List<String> excludes = null;

        // Process child elements
        NodeList children = parserElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            Element element = (Element) child;
            String tagName = element.getNodeName();

            if ("params".equals(tagName)) {
                // Process <params> section
                Map<String, Object> params = convertParamsElement(element);
                config.putAll(params);
            } else if ("parser-exclude".equals(tagName)) {
                // Process <parser-exclude> elements -> excludes array
                if (excludes == null) {
                    excludes = new ArrayList<>();
                }
                String excludeClass = element.getAttribute("class");
                if (excludeClass != null && !excludeClass.isEmpty()) {
                    excludes.add(classNameToComponentName(excludeClass, parserRegistry));
                }
            }
        }

        if (excludes != null && !excludes.isEmpty()) {
            config.put("exclude", excludes);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put(componentName, config);
        return result;
    }

    /**
     * Converts a &lt;params&gt; element to a map of parameter names to values.
     */
    private static Map<String, Object> convertParamsElement(Element paramsElement) {
        Map<String, Object> params = new LinkedHashMap<>();

        NodeList paramNodes = paramsElement.getElementsByTagName("param");
        for (int i = 0; i < paramNodes.getLength(); i++) {
            Element paramElement = (Element) paramNodes.item(i);
            String name = paramElement.getAttribute("name");
            String type = paramElement.getAttribute("type");

            if (name != null && !name.isEmpty()) {
                // Special case: otherTesseractSettings is a list of space-delimited key-value pairs
                // that needs to be converted to otherTesseractConfig map
                if ("otherTesseractSettings".equals(name) && "list".equals(type)) {
                    Map<String, String> configMap = convertTesseractSettingsList(paramElement);
                    params.put("otherTesseractConfig", configMap);
                } else {
                    Object value = convertParamValue(paramElement, type);
                    params.put(name, value);
                }
            }
        }

        return params;
    }

    /**
     * Special handler for TesseractOCR's otherTesseractSettings list.
     * <p>
     * Converts a list of space-delimited key-value pairs into a map.
     * <p>
     * XML Format:
     * <pre>
     * &lt;param name="otherTesseractSettings" type="list"&gt;
     *   &lt;string&gt;textord_initialx_ile 0.75&lt;/string&gt;
     *   &lt;string&gt;textord_noise_hfract 0.15625&lt;/string&gt;
     * &lt;/param&gt;
     * </pre>
     * <p>
     * JSON Output (as otherTesseractConfig):
     * <pre>
     * "otherTesseractConfig": {
     *   "textord_initialx_ile": "0.75",
     *   "textord_noise_hfract": "0.15625"
     * }
     * </pre>
     */
    private static Map<String, String> convertTesseractSettingsList(Element paramElement) {
        Map<String, String> configMap = new LinkedHashMap<>();
        NodeList stringNodes = paramElement.getElementsByTagName("string");

        for (int i = 0; i < stringNodes.getLength(); i++) {
            Node stringNode = stringNodes.item(i);
            if (stringNode.getNodeType() == Node.ELEMENT_NODE &&
                stringNode.getParentNode().equals(paramElement)) {
                String setting = stringNode.getTextContent().trim();
                // Parse space-delimited key-value pair
                int spaceIndex = setting.indexOf(' ');
                if (spaceIndex > 0) {
                    String key = setting.substring(0, spaceIndex).trim();
                    String value = setting.substring(spaceIndex + 1).trim();
                    configMap.put(key, value);
                } else {
                    LOG.warn("Ignoring malformed Tesseract setting (expected 'key value'): {}", setting);
                }
            }
        }

        return configMap;
    }

    /**
     * Converts a parameter value from XML element to the appropriate type.
     * <p>
     * Supports primitive types (bool, int, long, double), as well as collections:
     * <ul>
     *   <li>list - converts child &lt;string&gt; elements to a JSON array</li>
     *   <li>map - converts child elements (where element name is key) to a JSON object</li>
     * </ul>
     */
    private static Object convertParamValue(Element paramElement, String type) {
        if (type == null || type.isEmpty()) {
            // No type specified, return text content as string
            return paramElement.getTextContent().trim();
        }

        String typeKey = type.toLowerCase(Locale.ROOT);

        // Handle collection types that need child element processing
        if ("list".equals(typeKey)) {
            return convertListParam(paramElement);
        } else if ("map".equals(typeKey)) {
            return convertMapParam(paramElement);
        }

        // Handle primitive types using text content
        String valueStr = paramElement.getTextContent().trim();

        if (valueStr.isEmpty()) {
            return valueStr;
        }

        switch (typeKey) {
            case "bool":
            case "boolean":
                return Boolean.parseBoolean(valueStr);
            case "int":
            case "integer":
                try {
                    return Integer.parseInt(valueStr);
                } catch (NumberFormatException e) {
                    return valueStr;
                }
            case "long":
                try {
                    return Long.parseLong(valueStr);
                } catch (NumberFormatException e) {
                    return valueStr;
                }
            case "double":
            case "float":
                try {
                    return Double.parseDouble(valueStr);
                } catch (NumberFormatException e) {
                    return valueStr;
                }
            default:
                // Unknown type, return as string
                return valueStr;
        }
    }

    /**
     * Converts a list parameter by extracting &lt;string&gt; child elements.
     * <p>
     * XML Format:
     * <pre>
     * &lt;param name="languages" type="list"&gt;
     *   &lt;string&gt;en&lt;/string&gt;
     *   &lt;string&gt;fr&lt;/string&gt;
     * &lt;/param&gt;
     * </pre>
     * <p>
     * JSON Output: ["en", "fr"]
     */
    private static List<String> convertListParam(Element paramElement) {
        List<String> list = new ArrayList<>();
        NodeList stringNodes = paramElement.getElementsByTagName("string");

        for (int i = 0; i < stringNodes.getLength(); i++) {
            Node stringNode = stringNodes.item(i);
            if (stringNode.getNodeType() == Node.ELEMENT_NODE) {
                // Only include direct children, not nested strings
                if (stringNode.getParentNode().equals(paramElement)) {
                    list.add(stringNode.getTextContent().trim());
                }
            }
        }

        return list;
    }

    /**
     * Converts a map parameter by using child element names as keys and text content as values.
     * <p>
     * XML Format:
     * <pre>
     * &lt;param name="captureMap" type="map"&gt;
     *   &lt;title&gt;^Title: ([^\r\n]+)&lt;/title&gt;
     *   &lt;author&gt;^Author: ([^\r\n]+)&lt;/author&gt;
     * &lt;/param&gt;
     * </pre>
     * <p>
     * JSON Output: {"title": "^Title: ([^\\r\\n]+)", "author": "^Author: ([^\\r\\n]+)"}
     */
    private static Map<String, String> convertMapParam(Element paramElement) {
        Map<String, String> map = new LinkedHashMap<>();
        NodeList children = paramElement.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) child;
                String key = childElement.getNodeName();
                String value = childElement.getTextContent().trim();
                map.put(key, value);
            }
        }

        return map;
    }

    /**
     * Converts a full Java class name to a component name.
     * <p>
     * Uses the ComponentRegistry to perform a reverse lookup, respecting
     * custom component names from {@code @TikaComponent} annotations.
     * Falls back to kebab-case conversion if the class is not in the registry.
     * <p>
     * Examples:
     * <ul>
     *   <li>org.apache.tika.parser.pdf.PDFParser → pdf-parser</li>
     *   <li>org.apache.tika.parser.DefaultParser → default-parser</li>
     *   <li>org.apache.tika.parser.html.JSoupParser → jsoup-parser (from @TikaComponent annotation)</li>
     * </ul>
     */
    private static String classNameToComponentName(String fullClassName, ComponentRegistry registry) {
        try {
            // Try to load the class and find it in the registry
            Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(fullClassName);

            // Reverse lookup: find the component name for this class
            for (Map.Entry<String, ComponentInfo> entry : registry.getAllComponents().entrySet()) {
                if (entry.getValue().componentClass().equals(clazz)) {
                    return entry.getKey();
                }
            }
        } catch (ClassNotFoundException e) {
            // Class not found or not in registry - fall through to kebab-case conversion
        }

        // Fallback: use kebab-case conversion
        String simpleClassName = fullClassName;
        int lastDot = fullClassName.lastIndexOf('.');
        if (lastDot >= 0) {
            simpleClassName = fullClassName.substring(lastDot + 1);
        }

        return KebabCaseConverter.toKebabCase(simpleClassName);
    }
}
