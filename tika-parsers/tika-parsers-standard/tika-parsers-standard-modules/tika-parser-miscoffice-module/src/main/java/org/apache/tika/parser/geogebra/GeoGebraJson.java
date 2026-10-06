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
package org.apache.tika.parser.geogebra;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/**
 * Streaming readers for the two bits of JSON in a GeoGebra file. Both read
 * with jackson-core only; the parsers do not need a tree.
 */
final class GeoGebraJson {

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /**
     * The containers an element id sits in: {@code {"chapters":[{"pages":
     * [{"elements":[{"id":...}]}]}]}}. A container opened as an array element
     * or at the root has no field name.
     */
    private static final List<String> ELEMENT_PATH =
            Arrays.asList(null, "chapters", null, "pages", null, "elements", null);

    private GeoGebraJson() {
    }

    /**
     * Returns the {@code id}s of the elements of a {@code structure.json},
     * in document order.
     *
     * @throws IOException if the stream is not well-formed JSON
     */
    static List<String> elementIds(InputStream is) throws IOException {
        List<String> ids = new ArrayList<>();
        try (JsonParser parser = JSON_FACTORY.createParser(is)) {
            Deque<String> path = new ArrayDeque<>();
            String fieldName = null;
            for (JsonToken t = parser.nextToken(); t != null; t = parser.nextToken()) {
                if (t == JsonToken.FIELD_NAME) {
                    fieldName = parser.currentName();
                } else if (t.isStructStart()) {
                    path.addLast(fieldName == null ? "" : fieldName);
                    fieldName = null;
                } else if (t.isStructEnd()) {
                    path.removeLast();
                } else {
                    if ("id".equals(fieldName) && t.isScalarValue() && atElement(path)) {
                        ids.add(parser.getText());
                    }
                    fieldName = null;
                }
            }
        }
        return ids;
    }

    private static boolean atElement(Deque<String> path) {
        if (path.size() != ELEMENT_PATH.size()) {
            return false;
        }
        int i = 0;
        for (String name : path) {
            String expected = ELEMENT_PATH.get(i++);
            if (!(expected == null ? "" : expected).equals(name)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the values of every {@code text} field, at any depth, in
     * document order: the text runs of a {@code content} value such as
     * {@code [{"text":"Hello\n"}]}. A {@code text} field whose value is null,
     * an object or an array contributes nothing and is not descended into.
     *
     * @throws IOException if the string is not well-formed JSON
     */
    static List<String> textValues(String json) throws IOException {
        List<String> texts = new ArrayList<>();
        try (JsonParser parser = JSON_FACTORY.createParser(json)) {
            for (JsonToken t = parser.nextToken(); t != null; t = parser.nextToken()) {
                if (t == JsonToken.FIELD_NAME && "text".equals(parser.currentName())) {
                    t = parser.nextToken();
                    if (t == null) {
                        break;
                    }
                    if (t == JsonToken.VALUE_NULL) {
                        continue;
                    }
                    if (t.isScalarValue()) {
                        texts.add(parser.getText());
                    } else {
                        parser.skipChildren();
                    }
                }
            }
        }
        return texts;
    }
}
