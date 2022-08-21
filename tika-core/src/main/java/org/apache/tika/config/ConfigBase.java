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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.XMLReaderUtils;


public abstract class ConfigBase {

    /**
     * Use this to build a single class, where the user specifies the instance class, e.g.
     * PipesIterator
     *
     * @param itemName
     * @param is
     * @throws TikaConfigException
     * @throws IOException
     */
    protected static <T> T buildSingle(String itemName, Class<T> itemClass, InputStream is)
            throws TikaConfigException, IOException {
        Element properties = null;
        try {
            properties = XMLReaderUtils.buildDOM(is).getDocumentElement();
        } catch (SAXException e) {
            throw new IOException(e);
        } catch (TikaException e) {
            throw new TikaConfigException("problem loading xml to dom", e);
        }
        if (!properties.getLocalName().equals("properties")) {
            throw new TikaConfigException("expect properties as root node");
        }
        return buildSingle(itemName, itemClass, properties, null);
    }

    /**
     * Use this to build a single class, where the user specifies the instance class, e.g.
     * PipesIterator
     *
     * @param itemName
     * @param properties -- the properties element in the config
     * @throws TikaConfigException
     * @throws IOException
     */
    protected static <T> T buildSingle(String itemName, Class<T> itemClass, Element properties,
                                       T defaultValue)
            throws TikaConfigException, IOException {

        NodeList children = properties.getChildNodes();
        T toConfigure = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != 1) {
                continue;
            }
            if (itemName.equals(child.getLocalName())) {
                if (toConfigure != null) {
                    throw new TikaConfigException("There can only be one " + itemName +
                            " in a config");
                }
                T item = buildClass(child, itemName, itemClass);
                setParams(item, child, new HashSet<>());
                toConfigure = (T)item;
            }
        }
        if (toConfigure == null) {
            if (defaultValue == null) {
                throw new TikaConfigException("could not find " + itemName);
            }
            return defaultValue;
        }
        return toConfigure;
    }


    /**
     * Use this to build a list of components for a composite item (e.g.
     * CompositeMetadataFilter, FetcherManager), each with their own configurations
     *
     * @param compositeElementName
     * @param itemName
     * @param is
     * @throws TikaConfigException
     * @throws IOException
     */
    protected static <P, T> P buildComposite(String compositeElementName, Class<P> compositeClass,
                                             String itemName, Class<T> itemClass, InputStream is)
            throws TikaConfigException, IOException {
        Element properties = null;
        try {
            properties = XMLReaderUtils.buildDOM(is).getDocumentElement();
        } catch (SAXException e) {
            throw new IOException(e);
        } catch (TikaException e) {
            throw new TikaConfigException("problem loading xml to dom", e);
        }
        return buildComposite(compositeElementName, compositeClass, itemName, itemClass,
                properties);
    }

    protected static <P, T> P buildComposite(String compositeElementName, Class<P> compositeClass,
                String itemName, Class<T> itemClass, Element properties) throws TikaConfigException,
            IOException {

        if (!properties.getLocalName().equals("properties")) {
            throw new TikaConfigException("expect properties as root node");
        }
        NodeList children = properties.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != 1) {
                continue;
            }
            if (compositeElementName.equals(child.getLocalName())) {
                List<T> components = loadComposite(child, itemName, itemClass);
                Constructor constructor = null;
                try {
                    constructor = compositeClass.getConstructor(List.class);
                    P composite = (P) constructor.newInstance(components);
                    setParams(composite, child, new HashSet<>(), itemName);
                    return composite;
                } catch (NoSuchMethodException | InvocationTargetException |
                    InstantiationException | IllegalAccessException e) {
                    throw new TikaConfigException("can't build composite class", e);
                }
            }
        }
        throw new TikaConfigException("could not find " + compositeElementName);
    }

    private static <T> List<T> loadComposite(Node composite, String itemName,
                                             Class<? extends T> itemClass)
            throws TikaConfigException {
        NodeList children = composite.getChildNodes();
        List<T> items = new ArrayList<>();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != 1) {
                continue;
            }
            if (itemName.equals(child.getLocalName())) {
                T item = buildClass(child, itemName, itemClass);
                setParams(item, child, new HashSet<>());
                items.add(item);
            }
        }
        return items;
    }

    private static <T> T buildClass(Node node, String elementName, Class itemClass)
            throws TikaConfigException {
        String className = itemClass.getName();
        Node classNameNode = node.getAttributes().getNamedItem("class");

        if (classNameNode != null) {
            className = classNameNode.getTextContent();
        }
        try {
            Class clazz = Class.forName(className);
            if (!itemClass.isAssignableFrom(clazz)) {
                throw new TikaConfigException(
                    elementName + " with class name " + className + " must be of type '" +
                        itemClass.getName() + "'");
            }
            return (T) clazz.newInstance();
        } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
            throw new TikaConfigException("problem loading " + elementName, e);
        }
    }

    private static void setParams(Object object, Node targetNode, Set<String> settings)
            throws TikaConfigException {
        setParams(object, targetNode, settings, null);
    }

    private static void setParams(Object object, Node targetNode, Set<String> settings,
                                  String exceptNodeName) throws TikaConfigException {
        NodeList children = targetNode.getChildNodes();
        NodeList params = null;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if ("params".equals(child.getLocalName())) {
                params = child.getChildNodes();
            } else if (child.getNodeType() == 1 && ! child.getLocalName().equals(exceptNodeName)) {
                String itemName = child.getLocalName();
                String setter = "set" + itemName.substring(0, 1).toUpperCase(Locale.US) +
                        itemName.substring(1);
                Class itemClass = null;
                Method setterMethod = null;
                for (Method method : object.getClass().getMethods()) {
                    if (setter.equals(method.getName())) {
                        Class<?>[] classes = method.getParameterTypes();
                        if (classes.length == 1) {
                            itemClass = classes[0];
                            setterMethod = method;
                            break;
                        }
                    }
                }
                if (itemClass == null) {
                    throw new TikaConfigException("Couldn't find setter '" +
                            setter + "' for " + itemName);
                }
                Object item = buildClass(child, itemName, itemClass);
                setParams(itemClass.cast(item), child, new HashSet<>());
                try {
                    setterMethod.invoke(object, item);
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new TikaConfigException("problem creating " + itemName, e);
                }
            }
        }
        if (params != null) {
            for (int i = 0; i < params.getLength(); i++) {
                Node param = params.item(i);
                if (param.getNodeType() != 1) {
                    continue;
                }
                String localName = param.getLocalName();
                if (localName == null || localName.equals(exceptNodeName)) {
                    continue;
                }
                String txt = param.getTextContent();
                if (hasChildNodes(param)) {
                    if (isMap(param)) {
                        tryToSetMap(object, param);
                    } else {
                        tryToSetList(object, param);
                    }
                } else {
                    tryToSet(object, localName, txt);
                }

                if (txt != null) {
                    settings.add(localName);
                }
            }
        }
        if (object instanceof Initializable) {
            ((Initializable)object).initialize(Collections.EMPTY_MAP);
            ((Initializable) object).checkInitialization(InitializableProblemHandler.THROW);
        }
    }

    private static boolean hasChildNodes(Node param) {
        if (!param.hasChildNodes()) {
            return false;
        }

        NodeList nodeList = param.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node item = nodeList.item(i);
            if (item.getNodeType() == 1) {
                return true;
            }
        }
        return false;
    }

    private static void tryToSetList(Object object, Node param) throws TikaConfigException {
        String name = param.getLocalName();
        List<String> strings = new ArrayList<>();
        NodeList nodeList = param.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n = nodeList.item(i);
            if (n.getNodeType() == 1) {
                String txt = n.getTextContent();
                if (txt != null) {
                    strings.add(txt);
                }
            }
        }
        String setter = "set" + name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1);
        try {
            Method m = object.getClass().getMethod(setter, List.class);
            m.invoke(object, strings);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new TikaConfigException("can't set " + name, e);
        }
    }

    private static void tryToSetMap(Object object, Node param) throws TikaConfigException {
        String name = param.getLocalName();
        //only supports string, string at this point
        //use LinkedHashMap to keep insertion order!
        Map<String, String> map = new LinkedHashMap<>();
        NodeList nodeList = param.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n = nodeList.item(i);
            if (n.getNodeType() == 1) {
                NamedNodeMap m = n.getAttributes();
                String key = null;
                String value = null;
                if (m.getNamedItem("from") != null) {
                    key = m.getNamedItem("from").getTextContent();
                } else if (m.getNamedItem("key") != null) {
                    key = m.getNamedItem("key").getTextContent();
                } else if (m.getNamedItem("k") != null) {
                    key = m.getNamedItem("k").getTextContent();
                }

                if (m.getNamedItem("to") != null) {
                    value = m.getNamedItem("to").getTextContent();
                } else if (m.getNamedItem("value") != null) {
                    value = m.getNamedItem("value").getTextContent();
                } else if (m.getNamedItem("v") != null) {
                    value = m.getNamedItem("v").getTextContent();
                }
                if (key == null) {
                    throw new TikaConfigException("must specify a 'key' or 'from' value in a map " +
                            "object : " + param);
                }
                if (value == null) {
                    throw new TikaConfigException("must specify a 'value' or 'to' value in a " +
                            "map object : " + param);
                }
                map.put(key, value);
            }

        }
        String setter = "set" + name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1);
        try {
            Method m = object.getClass().getMethod(setter, Map.class);
            m.invoke(object, map);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new TikaConfigException("can't set " + name, e);
        }
    }

    private static boolean isMap(Node param) {
        NodeList nodeList = param.getChildNodes();
        for (int i = 0; i < nodeList.getLength(); i++) {
            Node n = nodeList.item(i);
            if (n.getNodeType() == 1) {
                if (n.hasAttributes()) {
                    if (n.getAttributes().getNamedItem("from") != null &&
                            n.getAttributes().getNamedItem("to") != null) {
                        return true;
                    } else if (n.getAttributes().getNamedItem("k") != null &&
                            n.getAttributes().getNamedItem("v") != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void tryToSet(Object object, String name, String value)
            throws TikaConfigException {
        String setter = "set" + name.substring(0, 1).toUpperCase(Locale.US) + name.substring(1);
        Class[] types =
                new Class[]{String.class, boolean.class, long.class, int.class, double.class,
                    float.class};
        for (Class t : types) {
            try {
                Method m = object.getClass().getMethod(setter, t);

                if (t == int.class) {
                    try {
                        m.invoke(object, Integer.parseInt(value));
                        return;
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new TikaConfigException("bad parameter " + setter, e);
                    }
                } else if (t == long.class) {
                    try {
                        m.invoke(object, Long.parseLong(value));
                        return;
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new TikaConfigException("bad parameter " + setter, e);
                    }
                } else if (t == boolean.class) {
                    try {
                        m.invoke(object, Boolean.parseBoolean(value));
                        return;
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new TikaConfigException("bad parameter " + setter, e);
                    }
                } else if (t == float.class) {
                    try {
                        m.invoke(object, Float.parseFloat(value));
                        return;
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new TikaConfigException("bad parameter " + setter, e);
                    }
                } else if (t == double.class) {
                    try {
                        m.invoke(object, Double.parseDouble(value));
                        return;
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new TikaConfigException("bad parameter " + setter, e);
                    }
                } else {
                    try {
                        m.invoke(object, value);
                        return;
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new TikaConfigException("bad parameter " + setter, e);
                    }
                }
            } catch (NoSuchMethodException e) {
                //swallow
            }
        }
        throw new TikaConfigException(
            "Couldn't find setter: " + setter + " for object " + object.getClass());
    }


    /**
     * This should be overridden to do something with the settings
     * after loading the object.
     *
     * @param settings
     */
    protected void handleSettings(Set<String> settings) {
        //no-op
    }

    /**
     * Use this to configure a subclass of ConfigBase, a single known object.
     *
     * @param nodeName
     * @param is
     * @return
     * @throws TikaConfigException
     * @throws IOException
     */
    protected Set<String> configure(String nodeName, InputStream is)
            throws TikaConfigException, IOException {
        Set<String> settings = new HashSet<>();

        Node properties = null;
        try {
            properties = XMLReaderUtils.buildDOM(is).getDocumentElement();
        } catch (SAXException e) {
            throw new IOException(e);
        } catch (TikaException e) {
            throw new TikaConfigException("problem loading xml to dom", e);
        }
        if (!properties.getLocalName().equals("properties")) {
            throw new TikaConfigException("expect properties as root node");
        }
        NodeList children = properties.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (nodeName.equals(child.getLocalName())) {
                setParams(this, child, settings);
            }
        }

        return settings;
    }
}
