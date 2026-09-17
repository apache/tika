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
package org.apache.tika.config.loader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.enricher.ContentEnrichers;
import org.apache.tika.parser.inference.Engine;
import org.apache.tika.parser.inference.EngineRegistry;
import org.apache.tika.parser.inference.InferenceBinding;
import org.apache.tika.parser.inference.InferenceDispatcher;
import org.apache.tika.parser.inference.InferenceTask;
import org.apache.tika.parser.inference.InputKind;
import org.apache.tika.parser.inference.Modality;
import org.apache.tika.parser.inference.TextChunker;

/**
 * Loads {@code "inference"}: bindings of a named engine to an input kind and tasks. Every
 * engine and task name is resolved at load, so a misnamed one fails startup. Null when the
 * key is absent.
 */
class InferenceLoader implements ComponentLoader<InferenceDispatcher> {

    static final String KEY = "inference";
    private static final Set<String> KNOWN = Set.of("id", "engine", "input", "tasks",
            "maxChunks", "maxBytes", "enabled", "_mime-include", "_mime-exclude", "chunker",
            "minWidth", "minHeight", "modality");
    private static final Pattern LEGAL_ID = Pattern.compile("[A-Za-z0-9._-]+");
    /** Largest unit a binding takes unless it says otherwise; for MEDIA, one cut segment. */
    static final long DEFAULT_MAX_BYTES = 20L * 1024 * 1024;
    /** Segments a MEDIA binding embeds per document unless it says otherwise: a request may narrow the grid, not widen it past this. */
    static final int DEFAULT_MEDIA_MAX_CHUNKS = 480;
    /** Image types no embedding endpoint takes; a binding's own include list overrides. */
    private static final List<String> NON_RASTER = List.of("image/svg+xml", "image/vnd.dwg",
            "image/vnd.dxf", "image/x-emf", "image/x-wmf", "image/wmf", "image/emf",
            "image/vnd.adobe.photoshop", "image/x-photoshop", "image/vnd.microsoft.icon",
            "image/x-icon");

    @Override
    public InferenceDispatcher load(TikaJsonConfig config, LoaderContext context)
            throws TikaConfigException {
        JsonNode node = config.getRootNode().get(KEY);
        if (node == null) {
            return null;
        }
        if (!node.isArray()) {
            throw new TikaConfigException("\"" + KEY + "\" must be an array of bindings");
        }
        EngineRegistry engines = context.get(EngineRegistry.class);
        List<InferenceDispatcher.Bound> bound = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (JsonNode entry : node) {
            if (!entry.isObject()) {
                throw new TikaConfigException("\"" + KEY + "\" entries must be objects");
            }
            Iterator<String> names = entry.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                if (!KNOWN.contains(name)) {
                    throw new TikaConfigException("\"" + KEY + "\" entry has unknown key \""
                            + name + "\"; known: " + KNOWN);
                }
            }
            String engineName = required(entry, "engine");
            InputKind input;
            try {
                input = InputKind.valueOf(required(entry, "input").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new TikaConfigException("\"input\" must be one of TEXT, PAGES, IMAGES, MEDIA");
            }
            String id = entry.hasNonNull("id") ? entry.get("id").asText()
                    : engineName + "-" + input.name().toLowerCase(Locale.ROOT);
            if (!LEGAL_ID.matcher(id).matches()) {
                throw new TikaConfigException("binding id \"" + id + "\" may use only letters, "
                        + "digits, '.', '_' and '-'");
            }
            if (!ids.add(id)) {
                throw new TikaConfigException("\"" + KEY + "\" binding id \"" + id
                        + "\" is used twice");
            }
            Engine engine = engines == null ? null : engines.get(engineName);
            if (engine == null) {
                throw new TikaConfigException("binding \"" + id + "\" names engine \""
                        + engineName + "\", which is not in \"engines\"");
            }
            List<String> taskNames = new ArrayList<>();
            if (entry.has("tasks")) {
                JsonNode tasks = entry.get("tasks");
                if (!tasks.isArray() || tasks.isEmpty()) {
                    throw new TikaConfigException("binding \"" + id + "\": \"tasks\" must be a "
                            + "non-empty array of task names");
                }
                for (JsonNode t : tasks) {
                    if (!t.isTextual()) {
                        throw new TikaConfigException("binding \"" + id + "\": task names must "
                                + "be strings");
                    }
                    taskNames.add(t.asText());
                }
            } else {
                taskNames.add("embed");
            }
            int maxChunks = bounded(entry, "maxChunks",
                    input == InputKind.MEDIA ? DEFAULT_MEDIA_MAX_CHUNKS : -1, id).intValue();
            long maxBytes = bounded(entry, "maxBytes", DEFAULT_MAX_BYTES, id);
            Set<MediaType> include = mimeTypes(entry, "_mime-include", id);
            Set<MediaType> exclude = mimeTypes(entry, "_mime-exclude", id);
            if (input == InputKind.MEDIA) {
                for (MediaType t : include) {
                    if (InputKind.of(t) != InputKind.MEDIA) {
                        throw new TikaConfigException("binding \"" + id + "\": \"_mime-include\" "
                                + t + " is not an audio or video type; a MEDIA binding would never see it");
                    }
                }
            }
            if (input == InputKind.IMAGES && include.isEmpty() && exclude.isEmpty()) {
                for (String t : NON_RASTER) {
                    exclude.add(MediaType.parse(t));
                }
            }
            TextChunker chunker = chunker(entry, id, input, context);
            if (input != InputKind.IMAGES && (entry.has("minWidth") || entry.has("minHeight"))) {
                throw new TikaConfigException("binding \"" + id + "\": \"minWidth\"/\"minHeight\""
                        + " apply to IMAGES only; a " + input + " binding has no image size");
            }
            Modality modality = modality(entry, id, input);
            InferenceBinding binding = InferenceBinding.builder(id, engineName, input)
                    .tasks(taskNames).include(include).exclude(exclude).maxChunks(maxChunks)
                    .maxBytes(maxBytes).enabled(entry.path("enabled").asBoolean(true))
                    .chunker(chunker).modality(modality)
                    .minSize(minimum(entry, "minWidth", id), minimum(entry, "minHeight", id))
                    .build();
            List<InferenceTask> tasks = new ArrayList<>();
            for (String taskName : taskNames) {
                InferenceTask task;
                try {
                    task = ComponentInstantiator.instantiateComponent(taskName,
                            context.getObjectMapper().createObjectNode(),
                            context.getObjectMapper(), context.getClassLoader(),
                            InferenceTask.class);
                } catch (TikaConfigException e) {
                    throw new TikaConfigException("binding \"" + id + "\" task \"" + taskName
                            + "\": " + e.getMessage(), e);
                }
                task.validate(binding, engine);
                tasks.add(task);
            }
            bound.add(new InferenceDispatcher.Bound(binding, engine, tasks));
        }
        return new InferenceDispatcher(bound);
    }

    /** {@code "chunker": {"<name>": {...}}} on a TEXT binding; absent means one chunk per document. */
    private static TextChunker chunker(JsonNode entry, String id, InputKind input,
                                       LoaderContext context) throws TikaConfigException {
        if (!entry.has("chunker")) {
            return null;
        }
        if (input != InputKind.TEXT) {
            throw new TikaConfigException("binding \"" + id + "\": \"chunker\" applies to TEXT "
                    + "bindings; " + input + " units are not chunked");
        }
        JsonNode node = entry.get("chunker");
        if (!node.isObject() || node.size() != 1) {
            throw new TikaConfigException("binding \"" + id + "\": \"chunker\" must be one "
                    + "{\"<chunker>\": {...}} object, e.g. {\"markdown-chunker\": "
                    + "{\"maxChunkChars\": 1500}}");
        }
        Map.Entry<String, JsonNode> type = node.fields().next();
        try {
            return ComponentInstantiator.instantiateComponent(type.getKey(), type.getValue(),
                    context.getObjectMapper(), context.getClassLoader(), TextChunker.class);
        } catch (TikaConfigException e) {
            throw new TikaConfigException("binding \"" + id + "\" chunker \"" + type.getKey()
                    + "\": " + e.getMessage(), e);
        }
    }

    /** Implied by the input except for MEDIA, which must say {@code audio} or {@code visual}. */
    private static Modality modality(JsonNode entry, String id, InputKind input)
            throws TikaConfigException {
        Modality implied = Modality.implied(input);
        if (!entry.has("modality")) {
            if (implied == null) {
                throw new TikaConfigException("binding \"" + id + "\" is on MEDIA and needs "
                        + "\"modality\": \"audio\" or \"visual\"");
            }
            return implied;
        }
        Modality given;
        try {
            given = Modality.valueOf(entry.get("modality").asText().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new TikaConfigException("binding \"" + id + "\": \"modality\" must be one of "
                    + "text, visual, audio");
        }
        if (implied != null && given != implied) {
            throw new TikaConfigException("binding \"" + id + "\": " + input + " is always "
                    + implied.name().toLowerCase(Locale.ROOT));
        }
        if (input == InputKind.MEDIA && given == Modality.TEXT) {
            throw new TikaConfigException("binding \"" + id + "\": MEDIA has no text channel");
        }
        return given;
    }

    private static String required(JsonNode entry, String field) throws TikaConfigException {
        if (!entry.hasNonNull(field) || !entry.get(field).isTextual()) {
            throw new TikaConfigException("\"" + KEY + "\" entry needs \"" + field + "\"");
        }
        return entry.get(field).asText();
    }

    /** A budget: -1 for no limit, 0 for none, else the value; anything below -1 is refused. */
    private static Long bounded(JsonNode entry, String field, long dflt, String id)
            throws TikaConfigException {
        if (!entry.has(field)) {
            return dflt;
        }
        JsonNode v = entry.get(field);
        if (!v.isIntegralNumber() || v.asLong() < -1) {
            throw new TikaConfigException("binding \"" + id + "\": \"" + field
                    + "\" must be -1 (no limit) or a non-negative integer");
        }
        return v.asLong();
    }

    /** {@code minWidth}/{@code minHeight}: a non-negative integer, 2 unless set. */
    private static int minimum(JsonNode entry, String field, String id)
            throws TikaConfigException {
        if (!entry.has(field)) {
            return InferenceBinding.DEFAULT_MIN_PIXELS;
        }
        JsonNode v = entry.get(field);
        if (!v.isIntegralNumber() || v.asInt() < 0) {
            throw new TikaConfigException("binding \"" + id + "\": \"" + field
                    + "\" must be a non-negative integer");
        }
        return v.asInt();
    }

    private static Set<MediaType> mimeTypes(JsonNode entry, String field, String id)
            throws TikaConfigException {
        Set<MediaType> types = new HashSet<>();
        if (!entry.has(field)) {
            return types;
        }
        if (!entry.get(field).isArray()) {
            throw new TikaConfigException("binding \"" + id + "\": \"" + field
                    + "\" must be an array of media types");
        }
        for (JsonNode t : entry.get(field)) {
            MediaType type = MediaType.parse(t.asText());
            if (type == null || ContentEnrichers.isLegacyOcrType(type)) {
                throw new TikaConfigException("binding \"" + id + "\": \"" + field
                        + "\" has an invalid media type " + t.asText());
            }
            types.add(type);
        }
        return types;
    }
}
