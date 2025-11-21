package org.apache.tika.plugins;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TikaConfigs {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);

    public static TikaConfigs load(InputStream is) throws IOException {
        try (Reader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return new TikaConfigs(OBJECT_MAPPER.readTree(reader));
        }
    }
    public static TikaConfigs load(Path path) throws IOException {
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
}
