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
package org.apache.tika.pipes.core.serialization;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.pipes.core.emitter.EmitDataImpl;

/**
 * JSON serialization/deserialization for IPC communication between PipesClient and PipesServer.
 */
public class JsonPipesIpc {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        // Use TikaObjectMapperFactory which provides TikaModule with Metadata/ParseContext serializers
        OBJECT_MAPPER = TikaObjectMapperFactory.createMapper();

        // Add pipes-specific serializers
        SimpleModule pipesModule = new SimpleModule();
        pipesModule.addSerializer(FetchEmitTuple.class, new FetchEmitTupleSerializer());
        pipesModule.addDeserializer(FetchEmitTuple.class, new FetchEmitTupleDeserializer());
        pipesModule.addSerializer(EmitData.class, new EmitDataSerializer());
        pipesModule.addDeserializer(EmitDataImpl.class, new EmitDataDeserializer());
        pipesModule.addSerializer(PipesResult.class, new PipesResultSerializer());
        pipesModule.addDeserializer(PipesResult.class, new PipesResultDeserializer());
        OBJECT_MAPPER.registerModule(pipesModule);
    }

    /**
     * Serialize an object to JSON bytes.
     */
    public static byte[] toBytes(Object obj) throws IOException {
        return OBJECT_MAPPER.writeValueAsBytes(obj);
    }

    /**
     * Deserialize JSON bytes to an object.
     */
    public static <T> T fromBytes(byte[] bytes, Class<T> clazz) throws IOException {
        return OBJECT_MAPPER.readValue(bytes, clazz);
    }

    /**
     * Get the configured ObjectMapper for direct use if needed.
     */
    public static ObjectMapper getMapper() {
        return OBJECT_MAPPER;
    }
}
