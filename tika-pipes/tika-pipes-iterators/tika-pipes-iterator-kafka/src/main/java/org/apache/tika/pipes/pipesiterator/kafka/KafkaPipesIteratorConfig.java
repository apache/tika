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
package org.apache.tika.pipes.pipesiterator.kafka;

import java.io.IOException;
import java.util.Objects;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorBaseConfig;
import org.apache.tika.pipes.api.pipesiterator.PipesIteratorConfig;

public class KafkaPipesIteratorConfig implements PipesIteratorConfig {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static KafkaPipesIteratorConfig load(JsonNode jsonNode) throws IOException, TikaConfigException {
        try {
            return OBJECT_MAPPER.treeToValue(jsonNode, KafkaPipesIteratorConfig.class);
        } catch (JacksonException e) {
            throw new TikaConfigException("problem with json", e);
        }
    }

    private String topic;
    private String bootstrapServers;
    private String keySerializer;
    private String valueSerializer;
    private String groupId;
    private String autoOffsetReset = "earliest";
    private int pollDelayMs = 100;
    private int emitMax = -1;
    private int groupInitialRebalanceDelayMs = 3000;
    private PipesIteratorBaseConfig baseConfig = null;

    public String getTopic() {
        return topic;
    }

    public String getBootstrapServers() {
        return bootstrapServers;
    }

    public String getKeySerializer() {
        return keySerializer;
    }

    public String getValueSerializer() {
        return valueSerializer;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getAutoOffsetReset() {
        return autoOffsetReset;
    }

    public int getPollDelayMs() {
        return pollDelayMs;
    }

    public int getEmitMax() {
        return emitMax;
    }

    public int getGroupInitialRebalanceDelayMs() {
        return groupInitialRebalanceDelayMs;
    }

    @Override
    public PipesIteratorBaseConfig getBaseConfig() {
        return baseConfig;
    }

    @Override
    public final boolean equals(Object o) {
        if (!(o instanceof KafkaPipesIteratorConfig that)) {
            return false;
        }

        return pollDelayMs == that.pollDelayMs &&
                emitMax == that.emitMax &&
                groupInitialRebalanceDelayMs == that.groupInitialRebalanceDelayMs &&
                Objects.equals(topic, that.topic) &&
                Objects.equals(bootstrapServers, that.bootstrapServers) &&
                Objects.equals(keySerializer, that.keySerializer) &&
                Objects.equals(valueSerializer, that.valueSerializer) &&
                Objects.equals(groupId, that.groupId) &&
                Objects.equals(autoOffsetReset, that.autoOffsetReset) &&
                Objects.equals(baseConfig, that.baseConfig);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(topic);
        result = 31 * result + Objects.hashCode(bootstrapServers);
        result = 31 * result + Objects.hashCode(keySerializer);
        result = 31 * result + Objects.hashCode(valueSerializer);
        result = 31 * result + Objects.hashCode(groupId);
        result = 31 * result + Objects.hashCode(autoOffsetReset);
        result = 31 * result + pollDelayMs;
        result = 31 * result + emitMax;
        result = 31 * result + groupInitialRebalanceDelayMs;
        result = 31 * result + Objects.hashCode(baseConfig);
        return result;
    }
}
