package org.apache.tika.serialization;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import org.apache.tika.plugins.PluginConfig;

public class PluginsConfigSerializer extends JsonSerializer<PluginConfig> {

    @Override
    public void serialize(PluginConfig pluginsConfig, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
        jsonGenerator.writeStartObject();
        jsonGenerator.writeStringField("pluginId", pluginsConfig.pluginId());
        jsonGenerator.writeFieldName("jsonConfig");
        jsonGenerator.writeRawValue(pluginsConfig.jsonConfig());
        jsonGenerator.writeEndObject();
    }
}
