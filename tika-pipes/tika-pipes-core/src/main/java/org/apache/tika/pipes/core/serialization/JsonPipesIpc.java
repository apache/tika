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

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;

import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.PipesResult;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.pipes.core.emitter.EmitDataImpl;

/**
 * Binary serialization/deserialization for IPC communication between PipesClient and PipesServer.
 * <p>
 * Uses Jackson's Smile binary format for efficient serialization. Smile is a binary JSON format
 * that is more compact and faster to parse than text JSON, while maintaining full compatibility
 * with the Jackson data binding API.
 */
public class JsonPipesIpc {

    private static final ObjectMapper OBJECT_MAPPER;

    static {
        // Use SmileFactory for binary format - more compact and faster than text JSON
        SmileFactory smileFactory = new SmileFactory();

        // Configure stream constraints for large content (e.g., 30MB+ documents)
        // Default Jackson limit is 20MB which is too small for IPC with large documents
        StreamReadConstraints constraints = StreamReadConstraints.builder()
                .maxStringLength(Integer.MAX_VALUE)
                .build();
        smileFactory.setStreamReadConstraints(constraints);

        // Create mapper with Smile factory and register TikaModule for Metadata/ParseContext serializers
        OBJECT_MAPPER = TikaObjectMapperFactory.createMapper(smileFactory);

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
     * Serialize an object to Smile binary format bytes.
     */
    public static byte[] toBytes(Object obj) throws IOException {
        return OBJECT_MAPPER.writeValueAsBytes(obj);
    }

    /**
     * Deserialize Smile binary format bytes to an object.
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
