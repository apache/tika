package org.apache.tika.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;

/**
 * Loads and validates Tika plugin configuration from JSON.
 */
public class TikaConfigs {

    private static final Set<String> KNOWN_ROOT_KEYS = Set.of(
            "fetchers",
            "emitters",
            "pipes-iterator",
            "pipes-reporters",
            "async",
            "pluginRoots"
    );

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    public static TikaConfigs load(InputStream is) throws IOException, TikaConfigException {
        try (Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            TikaConfigs configs = new TikaConfigs(OBJECT_MAPPER.readTree(reader));
            configs.validateNoUnknownKeys();
            return configs;
        }
    }
    public static TikaConfigs load(Path path) throws IOException, TikaConfigException {
        try (InputStream is = Files.newInputStream(path)) {
            return load(is);
        }
    }
    private final JsonNode root;

    private TikaConfigs(JsonNode root) {
        this.root = root;
    }

    public JsonNode getRoot() {
        return root;
    }

    public <T> T deserialize(Class<T> clazz, String key) throws IOException {
        return OBJECT_MAPPER.treeToValue(root.get(key), clazz);
    }

    /**
     * Validates that the config contains no unknown root-level keys.
     * This catches typos like "pipes-reporter" instead of "pipes-reporters".
     * <p>
     * Keys prefixed with "x-" are allowed for custom extensions.
     *
     * @throws TikaConfigException if unknown keys are found
     */
    private void validateNoUnknownKeys() throws TikaConfigException {
        Iterator<String> fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            if (!KNOWN_ROOT_KEYS.contains(key) && !key.startsWith("x-")) {
                throw new TikaConfigException("Unknown config key: '" + key +
                        "'. Valid keys: " + KNOWN_ROOT_KEYS + " (or use 'x-' prefix for custom keys)");
            }
        }
    }
}
