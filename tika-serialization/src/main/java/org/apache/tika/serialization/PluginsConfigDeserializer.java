package org.apache.tika.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import org.apache.tika.plugins.PluginConfig;

public class PluginsConfigDeserializer extends JsonDeserializer<PluginConfig> {

    @Override
    public PluginConfig deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JacksonException {
        JsonNode node = jsonParser.getCodec().readTree(jsonParser);

        String pluginId = node.get("pluginId").asText();

        JsonNode jsonConfigNode = node.get("jsonConfig");

        String jsonConfigRaw = jsonConfigNode.toString();

        return new PluginConfig(pluginId, jsonConfigRaw);
    }
}
