package org.apache.tika.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.apache.tika.plugins.PluginConfig;
import org.apache.tika.plugins.PluginConfigs;

public class PluginConfigLoader {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addSerializer(PluginConfig.class, new PluginsConfigSerializer());
        OBJECT_MAPPER.registerModule(module);
        OBJECT_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    public static PluginConfigs load(InputStream is) throws IOException  {
        try (Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            return OBJECT_MAPPER.readValue(reader, PluginConfigs.class);
        }
    }

}
