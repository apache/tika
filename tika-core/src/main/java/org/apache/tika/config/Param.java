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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.multiple.AbstractMultipleParser;
import org.apache.tika.utils.XMLReaderUtils;


/**
 * This is a serializable model class for parameters from configuration file.
 *
 * @param <T> value type. Should be serializable to string and have a constructor
 *            with string param
 * @since Apache Tika 1.14
 */
public class Param<T> implements Serializable {

    private static final String LIST = "list";
    private static final String MAP = "map";
    private static final String CLASS = "class";
    private static final Map<Class<?>, String> map = new HashMap<>();
    private static final Map<String, Class<?>> reverseMap = new HashMap<>();
    private static final Map<String, Class<?>> wellKnownMap = new HashMap<>();

    static {
        map.put(Boolean.class, "bool");
        map.put(String.class, "string");
        map.put(Byte.class, "byte");
        map.put(Short.class, "short");
        map.put(Integer.class, "int");
        map.put(Long.class, "long");
        map.put(BigInteger.class, "bigint");
        map.put(Float.class, "float");
        map.put(Double.class, "double");
        map.put(File.class, "file");
        map.put(URI.class, "uri");
        map.put(URL.class, "url");
        map.put(ArrayList.class, LIST);
        map.put(Map.class, MAP);
        for (Map.Entry<Class<?>, String> entry : map.entrySet()) {
            reverseMap.put(entry.getValue(), entry.getKey());
        }
        wellKnownMap.put("metadataPolicy", AbstractMultipleParser.MetadataPolicy.class);
    }

    //one of these two is used for serialization
    private final List<String> valueStrings = new ArrayList<>();
    private final Map<String, String> valueMap = new LinkedHashMap<>();

    private Class<T> type;
    private String name;
    private T actualValue;

    public Param() {
    }

    public Param(String name, Class<T> type, T value) {
        this.name = name;
        this.type = type;
        this.actualValue = value;
        if (List.class.isAssignableFrom(value.getClass())) {
            this.valueStrings.addAll((List) value);
        } else if (Map.class.isAssignableFrom(value.getClass())) {
            valueMap.putAll((Map)value);
        } else {
            this.valueStrings.add(value.toString());
        }
        if (this.type == null) {
            this.type = (Class<T>) wellKnownMap.get(name);
        }
    }

    public Param(String name, T value) {
        this(name, (Class<T>) value.getClass(), value);
    }

    public static <T> Param<T> load(InputStream stream)
            throws SAXException, IOException, TikaException {

        DocumentBuilder db = XMLReaderUtils.getDocumentBuilder();
        Document document = db.parse(stream);

        return load(document.getFirstChild());
    }

    public static <T> Param<T> load(Node node) throws TikaConfigException {

        Node nameAttr = node.getAttributes().getNamedItem("name");
        Node typeAttr = node.getAttributes().getNamedItem("type");
        Node valueAttr = node.getAttributes().getNamedItem("value");
        Node classAttr = node.getAttributes().getNamedItem("class");
        Class clazz = null;
        if (classAttr != null) {
            try {
                clazz = Class.forName(classAttr.getTextContent());
            } catch (ClassNotFoundException e) {
                throw new TikaConfigException("can't find class: " + classAttr.getTextContent(), e);
            }
        }
        Node value = node.getFirstChild();
        if (value instanceof NodeList && valueAttr != null) {
            throw new TikaConfigException("can't specify a value attr _and_ a node list");
        }
        if (valueAttr != null && (value == null || value.getTextContent() == null)) {
            value = valueAttr;
        }

        Param<T> ret = new Param<>();
        ret.name = nameAttr.getTextContent();
        if (typeAttr != null) {
            String type = typeAttr.getTextContent();
            if ("class".equals(type)) {
                if (classAttr == null) {
                    throw new TikaConfigException("must specify a class attribute if " +
                            "type=\"class\"");
                }
                ret.setType(clazz);
            } else {
                ret.setTypeString(typeAttr.getTextContent());
            }
        } else {
            ret.type = (Class<T>) wellKnownMap.get(ret.name);
            if (ret.type == null) {
                ret.type = clazz;
            }
            if (ret.type == null) {
                throw new TikaConfigException("Must specify a \"type\" in: " + node.getLocalName());
            }
        }

        if (clazz != null) {
            loadObject(ret, node, clazz);
        } else if (List.class.isAssignableFrom(ret.type)) {
            loadList(ret, node);
        } else if (Map.class.isAssignableFrom(ret.type)) {
            loadMap(ret, node);
        } else {
            //allow the empty string
            String textContent = "";
            if (value != null) {
                textContent = value.getTextContent();
            }
            ret.actualValue = getTypedValue(ret.type, textContent);
            ret.valueStrings.add(textContent);
        }
        return ret;
    }
    private static <T> void loadObject(Param<T> ret, Node root, Class clazz) throws TikaConfigException {

        try {
            ret.actualValue = (T)clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new TikaConfigException("can't build class: " + clazz, e);
        }

        NodeList nodeList = root.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n = nodeList.item(i);
            if ("params".equals(n.getLocalName())) {
                NodeList params = n.getChildNodes();
                for (int j = 0; j < params.getLength(); j++) {
                    if ("param".equals(params.item(j).getLocalName())) {
                        Param param = load(params.item(j));

                        Method method = null;
                        String methodName = "set" +
                                param.getName().substring(0,1).toUpperCase(Locale.US) +
                                param.getName().substring(1);
                        try {
                            method = ret.actualValue.getClass().getMethod(methodName,
                                    param.getType());
                        } catch (NoSuchMethodException e) {
                            throw new TikaConfigException("can't find method: " + methodName, e);
                        }
                        try {
                            method.invoke(ret.actualValue, param.getValue());
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new TikaConfigException("can't set param value: " + param.getName(), e);
                        }
                    }
                }
            }
        }
    }

    private static <T> void loadMap(Param<T> ret, Node root) throws TikaConfigException {
        Node child = root.getFirstChild();
        ret.actualValue = (T) new HashMap<>();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                String key = child.getLocalName();
                String value = child.getTextContent();
                if (((Map)ret.actualValue).containsKey(key)) {
                    throw new TikaConfigException("Duplicate keys are not allowed: " + key);
                }
                ((Map)ret.actualValue).put(key, value);
                ret.valueMap.put(key, value);
            }
            child = child.getNextSibling();
        }
    }

    private static <T> void loadList(Param<T> ret, Node root) {
        Node child = root.getFirstChild();
        ret.actualValue = (T) new ArrayList<>();
        while (child != null) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Class type = classFromType(child.getLocalName());
                ((List) ret.actualValue).add(getTypedValue(type, child.getTextContent()));
                ret.valueStrings.add(child.getTextContent());
            }
            child = child.getNextSibling();
        }
    }

    private static <T> Class<T> classFromType(String type) {
        if (reverseMap.containsKey(type)) {
            return (Class<T>) reverseMap.get(type);
        } else {
            try {
                return (Class<T>) Class.forName(type);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static <T> T getTypedValue(Class<T> type, String value) {
        try {
            if (type.isEnum()) {
                Object val = Enum.valueOf((Class) type, value);
                return (T) val;
            }

            Constructor<T> constructor = type.getConstructor(String.class);
            constructor.setAccessible(true);
            return constructor.newInstance(value);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(type + " doesnt have a constructor that takes String arg",
                    e);
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Class<T> getType() {
        return type;
    }

    public void setType(Class<T> type) {
        this.type = type;
    }

    public String getTypeString() {
        if (type == null) {
            return null;
        }
        if (List.class.isAssignableFrom(type)) {
            return LIST;
        }
        if (map.containsKey(type)) {
            return map.get(type);
        }
        return type.getName();
    }

    public void setTypeString(String type) {
        if (type == null || type.isEmpty()) {
            return;
        }

        this.type = classFromType(type);
        this.actualValue = null;
    }

    public T getValue() {
        return actualValue;
    }

    @Override
    public String toString() {
        return "Param{" + "name='" + name + '\'' + ", valueStrings='" + valueStrings + '\'' +
                ", actualValue=" + actualValue + '}';
    }

    public void save(OutputStream stream) throws TransformerException, TikaException {


        DocumentBuilder builder = XMLReaderUtils.getDocumentBuilder();
        Document doc = builder.newDocument();
        Element paramEl = doc.createElement("param");
        doc.appendChild(paramEl);

        save(doc, paramEl);

        Transformer transformer = XMLReaderUtils.getTransformer();
        transformer.transform(new DOMSource(paramEl), new StreamResult(stream));
    }

    public void save(Document doc, Node node) {

        if (!(node instanceof Element)) {
            throw new IllegalArgumentException("Not an Element : " + node);
        }

        Element el = (Element) node;

        el.setAttribute("name", getName());
        el.setAttribute("type", getTypeString());
        if (List.class.isAssignableFrom(actualValue.getClass())) {
            for (int i = 0; i < valueStrings.size(); i++) {
                String val = valueStrings.get(i);
                String typeString = map.get(((List) actualValue).get(i).getClass());
                Node item = doc.createElement(typeString);
                item.setTextContent(val);
                el.appendChild(item);
            }
        } else if (Map.class.isAssignableFrom(actualValue.getClass())) {
            for (Object key : ((Map)actualValue).keySet()) {
                String keyString = (String) key;
                String valueString = (String)((Map)actualValue).get(keyString);
                Node item = doc.createElement(keyString);
                item.setTextContent(valueString);
                el.appendChild(item);
            }
        } else {
            el.setTextContent(valueStrings.get(0));
        }
    }

}
