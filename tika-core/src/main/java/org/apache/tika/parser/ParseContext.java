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
package org.apache.tika.parser;

import java.io.InputStream;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.transform.Transformer;

import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.TikaException;
import org.apache.tika.utils.XMLReaderUtils;

/**
 * Parse context. Used to pass context information to Tika parsers.
 *
 * @see <a href="https://issues.apache.org/jira/browse/TIKA-275">TIKA-275</a>
 * @since Apache Tika 0.5
 */
public class ParseContext implements Serializable {

    public static ParseContext EMPTY = new NoOpParseContext();

    /**
     * Serial version UID.
     */
    private static final long serialVersionUID = -5921436862145826534L;

    /**
     * Map of objects in this context
     */
    private final Map<String, Object> context = new HashMap<>();

    /**
     * Adds the given value to the context as an implementation of the given
     * interface.
     *
     * @param key   the interface implemented by the given value
     * @param value the value to be added, or <code>null</code> to remove
     */
    public <T> void set(Class<T> key, T value) {
        if (value != null) {
            context.put(key.getName(), value);
        } else {
            context.remove(key.getName());
        }
    }

    /**
     * Returns the object in this context that implements the given interface.
     *
     * @param key the interface implemented by the requested object
     * @return the object that implements the given interface,
     * or <code>null</code> if not found
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> key) {
        return (T) context.get(key.getName());
    }

    /**
     * Returns the object in this context that implements the given interface,
     * or the given default value if such an object is not found.
     *
     * @param key          the interface implemented by the requested object
     * @param defaultValue value to return if the requested object is not found
     * @return the object that implements the given interface,
     * or the given default value if not found
     */
    public <T> T get(Class<T> key, T defaultValue) {
        T value = get(key);
        if (value != null) {
            return value;
        } else {
            return defaultValue;
        }
    }

    public boolean isEmpty() {
        return context.size() == 0;
    }

    private static class NoOpParseContext extends ParseContext {
        @Override
        public <T> void set(Class<T> key, T value) {
        }

        @Override
        public <T> T get(Class<T> key) {
            return null;
        }

        @Override
        public <T> T get(Class<T> key, T defaultValue) {
            return defaultValue;
        }
    }
}
