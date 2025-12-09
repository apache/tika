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
package org.apache.tika.pipes.core.statestore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.tika.pipes.api.statestore.StateStoreException;
import org.apache.tika.plugins.ExtensionConfig;

/**
 * Helper class for serializing and deserializing components to/from StateStore.
 * Uses JSON serialization via Jackson ObjectMapper.
 */
public class ComponentSerializer {

    private final ObjectMapper objectMapper;

    public ComponentSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ComponentSerializer() {
        this(new ObjectMapper());
    }

    /**
     * Serialize an ExtensionConfig to bytes.
     *
     * @param config the config to serialize
     * @return byte array representation
     * @throws StateStoreException if serialization fails
     */
    public byte[] serializeConfig(ExtensionConfig config) throws StateStoreException {
        try {
            return objectMapper.writeValueAsString(config).getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StateStoreException("Failed to serialize ExtensionConfig", e);
        }
    }

    /**
     * Deserialize an ExtensionConfig from bytes.
     *
     * @param data the byte array to deserialize
     * @return the deserialized ExtensionConfig
     * @throws StateStoreException if deserialization fails
     */
    public ExtensionConfig deserializeConfig(byte[] data) throws StateStoreException {
        if (data == null) {
            return null;
        }
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, ExtensionConfig.class);
        } catch (IOException e) {
            throw new StateStoreException("Failed to deserialize ExtensionConfig", e);
        }
    }

    /**
     * Serialize a generic object to bytes.
     *
     * @param obj the object to serialize
     * @return byte array representation
     * @throws StateStoreException if serialization fails
     */
    public byte[] serialize(Object obj) throws StateStoreException {
        try {
            return objectMapper.writeValueAsString(obj).getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new StateStoreException("Failed to serialize object", e);
        }
    }

    /**
     * Deserialize a generic object from bytes.
     *
     * @param data the byte array to deserialize
     * @param clazz the class to deserialize to
     * @param <T> the type parameter
     * @return the deserialized object
     * @throws StateStoreException if deserialization fails
     */
    public <T> T deserialize(byte[] data, Class<T> clazz) throws StateStoreException {
        if (data == null) {
            return null;
        }
        try {
            String json = new String(data, StandardCharsets.UTF_8);
            return objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            throw new StateStoreException("Failed to deserialize object", e);
        }
    }
}
