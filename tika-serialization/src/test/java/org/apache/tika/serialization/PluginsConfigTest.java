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
package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import org.apache.tika.plugins.PluginConfig;
import org.apache.tika.plugins.PluginConfigs;

public class PluginsConfigTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(PluginConfig.class, new PluginsConfigDeserializer());
        module.addSerializer(PluginConfig.class, new PluginsConfigSerializer());
        OBJECT_MAPPER.registerModule(module);
        OBJECT_MAPPER.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    }

    @Test
    public void testBasic() throws Exception {

        PluginConfig p1 = new PluginConfig("pluginId",
                """
                        {"basePath":"/my/docs","includeSystemInfo":true}
                        """);
        String json = OBJECT_MAPPER.writeValueAsString(p1);

        PluginConfig deserialized = OBJECT_MAPPER.readValue(json, PluginConfig.class);
        assertEquals(p1.factoryPluginId(), deserialized.factoryPluginId());
        assertEquals(flatten(p1.jsonConfig()), flatten(deserialized.jsonConfig()));
    }

    @Test
    public void testMap() throws Exception {
        PluginConfig p1 = new PluginConfig("pluginId1",
                """
                        {"basePath":"/my/docs1","includeSystemInfo":true}
                        """);
        PluginConfig p2 = new PluginConfig("pluginId2",
                """
                        {"basePath":"/my/docs2","includeSystemInfo":false}
                        """);
        Map<String, PluginConfig> map = new HashMap<>();
        map.put(p1.factoryPluginId(), p1);
        map.put(p2.factoryPluginId(), p2);
        PluginConfigs pluginConfigManager = new PluginConfigs(map);

        String json = OBJECT_MAPPER.writeValueAsString(pluginConfigManager);

        PluginConfigs deserialized = OBJECT_MAPPER.readValue(json, PluginConfigs.class);
        assertEquals(pluginConfigManager.get(p1.factoryPluginId()).get().factoryPluginId(), deserialized.get(p1.factoryPluginId()).get().factoryPluginId());
        assertEquals(flatten(pluginConfigManager.get(p1.factoryPluginId()).get().jsonConfig()),
                flatten(deserialized.get(p1.factoryPluginId()).get().jsonConfig()));
    }

    private static String flatten(String s) {
        return s.replaceAll("[\r\n]", "");
    }
}
