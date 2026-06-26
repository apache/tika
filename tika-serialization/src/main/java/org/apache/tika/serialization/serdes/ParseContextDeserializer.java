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
package org.apache.tika.serialization.serdes;

import static org.apache.tika.serialization.serdes.ParseContextSerializer.PARSE_CONTEXT;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.ComponentInfo;
import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.serialization.ComponentNameResolver;

/**
 * Deserializes ParseContext from JSON. Every entry is stored as a JSON config and resolved lazily
 * by {@link org.apache.tika.serialization.ParseContextUtils#resolveAll}; nothing is constructed
 * here. Example: {@code {"basic-content-handler-factory":{"type":"XML"},"metadata-filters":[...]}}
 */
public class ParseContextDeserializer extends JsonDeserializer<ParseContext> {

    private static final Logger LOG = LoggerFactory.getLogger(ParseContextDeserializer.class);

    /**
     * When true, refuses context-key types blocked from untrusted wire input
     * (see {@link ComponentNameResolver#isWireBlocked(Class)}). Use for request bodies (e.g.
     * tika-server /pipes); the default (false) is for trusted load-time config via TikaLoader.
     */
    private final boolean restricted;

    public ParseContextDeserializer() {
        this(false);
    }

    public ParseContextDeserializer(boolean restricted) {
        this.restricted = restricted;
    }

    private static ObjectMapper plainMapper() {
        return TikaObjectMapperFactory.getPlainMapper();
    }

    @Override
    public ParseContext deserialize(JsonParser jsonParser, DeserializationContext ctxt)
            throws IOException {
        JsonNode root = jsonParser.readValueAsTree();
        return readParseContext(root, restricted);
    }

    /**
     * Deserializes a ParseContext from a JsonNode. Every field is stored as a JSON config string
     * for lazy resolution; nothing is constructed here. Throws if multiple entries resolve to the
     * same context key (e.g. "bouncy-castle-digester" and "commons-digester" -> DigesterFactory).
     */
    public static ParseContext readParseContext(JsonNode jsonNode)
            throws IOException {
        return readParseContext(jsonNode, false);
    }

    /**
     * As {@link #readParseContext(JsonNode)}, but when {@code restricted} is true, refuses any
     * reference to a context-key type that is blocked from untrusted wire input
     * (see {@link ComponentNameResolver#isWireBlocked(Class)}).
     */
    public static ParseContext readParseContext(JsonNode jsonNode, boolean restricted)
            throws IOException {
        // Handle optional wrapper: { "parse-context": {...} }
        JsonNode contextNode = jsonNode.get(PARSE_CONTEXT);
        if (contextNode == null) {
            contextNode = jsonNode;
        }

        ParseContext parseContext = new ParseContext();

        if (!contextNode.isObject()) {
            return parseContext;
        }

        // Untrusted wire input: refuse any blocked component anywhere in the tree before anything
        // is constructed.
        if (restricted) {
            assertNoBlockedComponents(contextNode);
        }

        // contextKey -> friendlyName, to detect duplicates within this document.
        Map<Class<?>, String> seenContextKeys = new HashMap<>();

        Iterator<String> fieldNames = contextNode.fieldNames();
        while (fieldNames.hasNext()) {
            String name = fieldNames.next();
            JsonNode value = contextNode.get(name);
            checkForDuplicateContextKey(name, seenContextKeys);
            // Store as a lazy JSON config (plain mapper: the main one may be binary/Smile).
            parseContext.setJsonConfig(name, plainMapper().writeValueAsString(value));
        }

        return parseContext;
    }

    /**
     * Checks if a JSON config entry would create a duplicate context key.
     * <p>
     * Looks up the friendly name in the component registry to determine its context key,
     * then checks if that key has already been seen in this document.
     *
     * @param friendlyName the friendly name of the config entry
     * @param seenContextKeys map of already-seen context keys to their friendly names
     * @throws IOException if a duplicate context key is detected
     */
    private static void checkForDuplicateContextKey(String friendlyName,
                                                     Map<Class<?>, String> seenContextKeys)
            throws IOException {
        Optional<ComponentInfo> infoOpt = ComponentNameResolver.getComponentInfo(friendlyName);
        if (infoOpt.isEmpty()) {
            // Not a registered component - can't check for duplicates, that's okay
            return;
        }

        ComponentInfo info = infoOpt.get();

        // Self-configuring components (e.g., parsers) stay as JSON configs and are
        // accessed by string key at runtime via ParseContextConfig.getConfig().
        // They never get resolved to typed objects in the context map, so multiple
        // self-configuring components with the same context key are not duplicates.
        if (info.selfConfiguring()) {
            return;
        }

        Class<?> contextKey = ComponentNameResolver.determineContextKey(info);

        String existingName = seenContextKeys.get(contextKey);
        if (existingName != null) {
            throw new IOException("Duplicate parse-context entries resolve to the same key " +
                    contextKey.getName() + ": '" + existingName + "' and '" + friendlyName + "'");
        }
        seenContextKeys.put(contextKey, friendlyName);
    }

    /**
     * Refuses any wire-blocked component a request must not be able to bind. Deserialization is
     * lazy, so this gate runs before {@code resolveAll} would instantiate anything. It is
     * position-aware: a top-level flat key for a self-configuring component (e.g.
     * {@code {"pdf-parser":{...}}}) is an inert per-request config that resolveAll skips, so it is
     * allowed and its opaque config subtree is not scanned; every other key is checked and its
     * subtree scanned, refusing any blocked object key at any depth. Bare-string references are not
     * scanned -- they name a defaults instance with no config. Running before resolution matters
     * because merely constructing a blocked component can have side effects.
     */
    private static void assertNoBlockedComponents(JsonNode contextNode) throws IOException {
        if (contextNode == null || !contextNode.isObject()) {
            return;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = contextNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            String name = e.getKey();
            if (isSelfConfiguring(name)) {
                // Inert per-request config for a self-configuring component; resolveAll skips it.
                continue;
            }
            assertNameAllowed(name);
            scanForBlocked(e.getValue());
        }
    }

    /**
     * Recursively refuses blocked-component object keys within a scanned subtree. Only object keys
     * are checked; bare strings are inert defaults (see {@link #assertNoBlockedComponents}).
     */
    private static void scanForBlocked(JsonNode node) throws IOException {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> e = fields.next();
                assertNameAllowed(e.getKey());
                scanForBlocked(e.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                scanForBlocked(child);
            }
        }
    }

    private static boolean isSelfConfiguring(String name) {
        Optional<ComponentInfo> infoOpt = ComponentNameResolver.getComponentInfo(name);
        return infoOpt.isPresent() && infoOpt.get().selfConfiguring();
    }

    private static void assertNameAllowed(String name) throws IOException {
        Optional<ComponentInfo> infoOpt = ComponentNameResolver.getComponentInfo(name);
        if (infoOpt.isEmpty()) {
            return;
        }
        Class<?> contextKey = ComponentNameResolver.determineContextKey(infoOpt.get());
        if (ComponentNameResolver.isWireBlocked(contextKey)) {
            throw new IOException(wireBlockedMessage(name, contextKey));
        }
    }

    private static String wireBlockedMessage(String friendlyName, Class<?> contextKey) {
        return "Component '" + friendlyName + "' (context key " + contextKey.getName() +
                ") may not be supplied via a request parseContext. Components of this type must " +
                "be configured server-side in the Tika config, not at request time.";
    }
}
