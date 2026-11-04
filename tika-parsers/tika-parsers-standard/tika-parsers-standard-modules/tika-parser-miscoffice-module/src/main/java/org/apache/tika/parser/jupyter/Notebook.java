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
package org.apache.tika.parser.jupyter;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.StreamReadConstraints;

/**
 * The parts of an nbformat 4 notebook the parser uses: cell sources, the text
 * and image outputs, the {@code nbsphinx-thumbnail} markers and the kernel. Other
 * output data (HTML, widgets, JSON) is skipped while reading, so the model holds
 * little beyond the images.
 *
 * @see <a href="https://nbformat.readthedocs.io/en/latest/format_description.html">The
 * notebook file format</a>
 */
final class Notebook {

    static final String THUMBNAIL_MARKER = "nbsphinx-thumbnail";

    /** Output data kept: the image types a thumbnail can be, and plain text. */
    static final Set<String> KEPT_DATA = Set.of("image/svg+xml", "image/png", "image/jpeg",
            "text/plain");

    private final List<Cell> cells = new ArrayList<>();
    private String kernel;
    private String kernelLanguage;
    private String language;
    private Integer nbformat;
    private Integer nbformatMinor;

    /**
     * How a cell asks to be the thumbnail. {@code outputIndex} is null when the
     * marker names none; {@code invalid} is set when the metadata marker is not an
     * object or carries keys other than {@code output-index} and {@code tooltip}.
     */
    record Marker(Integer outputIndex, boolean invalid) {
    }

    record Output(String type, Map<String, String> data, String text) {
    }

    record Cell(String type, String source, Marker marker, List<Output> outputs) {
    }

    List<Cell> cells() {
        return cells;
    }

    String kernel() {
        return kernel;
    }

    String language() {
        return language != null ? language : kernelLanguage;
    }

    String nbformat() {
        if (nbformat == null) {
            return null;
        }
        return nbformatMinor == null ? nbformat.toString() : nbformat + "." + nbformatMinor;
    }

    static Notebook read(InputStream stream, int maxDocumentLength, int maxStringLength)
            throws IOException {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxDocumentLength(maxDocumentLength)
                        .maxStringLength(maxStringLength)
                        .build())
                .build();
        Notebook notebook = new Notebook();
        try (JsonParser p = factory.createParser(stream)) {
            expect(p.nextToken(), JsonToken.START_OBJECT, p);
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                String name = p.currentName();
                JsonToken value = p.nextToken();
                switch (name) {
                    case "cells" -> notebook.readCells(p, value);
                    case "metadata" -> notebook.readMetadata(p, value);
                    case "nbformat" -> notebook.nbformat = intValue(p, value);
                    case "nbformat_minor" -> notebook.nbformatMinor = intValue(p, value);
                    default -> p.skipChildren();
                }
            }
        }
        return notebook;
    }

    private void readCells(JsonParser p, JsonToken value) throws IOException {
        if (value != JsonToken.START_ARRAY) {
            p.skipChildren();
            return;
        }
        while (p.nextToken() == JsonToken.START_OBJECT) {
            String type = null;
            String source = null;
            Marker marker = null;
            List<Output> outputs = List.of();
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                String name = p.currentName();
                JsonToken token = p.nextToken();
                switch (name) {
                    case "cell_type" -> type = textValue(p, token);
                    case "source" -> source = textValue(p, token);
                    case "metadata" -> marker = readMarker(p, token);
                    case "outputs" -> outputs = readOutputs(p, token);
                    default -> p.skipChildren();
                }
            }
            cells.add(new Cell(type, source, marker, outputs));
        }
    }

    /**
     * The marker as nbsphinx reads it: the {@code nbsphinx-thumbnail} key in the
     * cell metadata wins over the {@code nbsphinx-thumbnail} tag.
     */
    private static Marker readMarker(JsonParser p, JsonToken value) throws IOException {
        if (value != JsonToken.START_OBJECT) {
            p.skipChildren();
            return null;
        }
        Marker fromMetadata = null;
        boolean tagged = false;
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String name = p.currentName();
            JsonToken token = p.nextToken();
            if (THUMBNAIL_MARKER.equals(name)) {
                fromMetadata = readMarkerObject(p, token);
            } else if ("tags".equals(name) && token == JsonToken.START_ARRAY) {
                while (p.nextToken() != JsonToken.END_ARRAY) {
                    if (p.currentToken() == JsonToken.VALUE_STRING
                            && THUMBNAIL_MARKER.equals(p.getText())) {
                        tagged = true;
                    }
                    p.skipChildren();
                }
            } else {
                p.skipChildren();
            }
        }
        if (fromMetadata != null) {
            return fromMetadata;
        }
        return tagged ? new Marker(null, false) : null;
    }

    private static Marker readMarkerObject(JsonParser p, JsonToken value) throws IOException {
        if (value != JsonToken.START_OBJECT) {
            p.skipChildren();
            return new Marker(null, true);
        }
        Integer outputIndex = null;
        boolean invalid = false;
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String name = p.currentName();
            JsonToken token = p.nextToken();
            if ("output-index".equals(name)) {
                // anything but an integer is an index that names no output
                outputIndex = token == JsonToken.VALUE_NULL ? null
                        : token == JsonToken.VALUE_NUMBER_INT ? p.getIntValue() : -1;
            } else if (!"tooltip".equals(name)) {
                invalid = true;
            }
            p.skipChildren();
        }
        return new Marker(outputIndex, invalid);
    }

    private static List<Output> readOutputs(JsonParser p, JsonToken value) throws IOException {
        List<Output> outputs = new ArrayList<>();
        if (value != JsonToken.START_ARRAY) {
            p.skipChildren();
            return outputs;
        }
        while (p.nextToken() != JsonToken.END_ARRAY) {
            if (p.currentToken() != JsonToken.START_OBJECT) {
                p.skipChildren();
                outputs.add(new Output(null, Map.of(), null));
                continue;
            }
            String type = null;
            String text = null;
            Map<String, String> data = new LinkedHashMap<>();
            while (p.nextToken() == JsonToken.FIELD_NAME) {
                String name = p.currentName();
                JsonToken token = p.nextToken();
                switch (name) {
                    case "output_type" -> type = textValue(p, token);
                    case "text" -> text = textValue(p, token);
                    case "ename", "evalue" -> {
                        String part = textValue(p, token);
                        if (part != null) {
                            text = text == null ? part : text + ": " + part;
                        }
                    }
                    case "data" -> readData(p, token, data);
                    default -> p.skipChildren();
                }
            }
            outputs.add(new Output(type, data, text));
        }
        return outputs;
    }

    private static void readData(JsonParser p, JsonToken value, Map<String, String> data)
            throws IOException {
        if (value != JsonToken.START_OBJECT) {
            p.skipChildren();
            return;
        }
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String mimeType = p.currentName();
            JsonToken token = p.nextToken();
            if (KEPT_DATA.contains(mimeType)) {
                String text = textValue(p, token);
                if (text != null) {
                    data.put(mimeType, text);
                }
            } else {
                p.skipChildren();
            }
        }
    }

    private void readMetadata(JsonParser p, JsonToken value) throws IOException {
        if (value != JsonToken.START_OBJECT) {
            p.skipChildren();
            return;
        }
        while (p.nextToken() == JsonToken.FIELD_NAME) {
            String name = p.currentName();
            JsonToken token = p.nextToken();
            if ("kernelspec".equals(name) && token == JsonToken.START_OBJECT) {
                while (p.nextToken() == JsonToken.FIELD_NAME) {
                    String key = p.currentName();
                    JsonToken t = p.nextToken();
                    switch (key) {
                        case "name" -> kernel = textValue(p, t);
                        case "language" -> kernelLanguage = textValue(p, t);
                        default -> p.skipChildren();
                    }
                }
            } else if ("language_info".equals(name) && token == JsonToken.START_OBJECT) {
                while (p.nextToken() == JsonToken.FIELD_NAME) {
                    String key = p.currentName();
                    JsonToken t = p.nextToken();
                    if ("name".equals(key)) {
                        language = textValue(p, t);
                    } else {
                        p.skipChildren();
                    }
                }
            } else {
                p.skipChildren();
            }
        }
    }

    /** nbformat stores multi-line text as a string or as a list of lines. */
    private static String textValue(JsonParser p, JsonToken value) throws IOException {
        if (value == JsonToken.VALUE_STRING) {
            return p.getText();
        }
        if (value == JsonToken.START_ARRAY) {
            StringBuilder sb = new StringBuilder();
            while (p.nextToken() != JsonToken.END_ARRAY) {
                if (p.currentToken() == JsonToken.VALUE_STRING) {
                    sb.append(p.getText());
                } else {
                    p.skipChildren();
                }
            }
            return sb.toString();
        }
        p.skipChildren();
        return null;
    }

    private static Integer intValue(JsonParser p, JsonToken value) throws IOException {
        if (value == JsonToken.VALUE_NUMBER_INT) {
            return p.getIntValue();
        }
        p.skipChildren();
        return null;
    }

    private static void expect(JsonToken actual, JsonToken expected, JsonParser p)
            throws IOException {
        if (actual != expected) {
            throw new JsonParseException(p,
                    "a notebook is a JSON object, found " + actual);
        }
    }
}
