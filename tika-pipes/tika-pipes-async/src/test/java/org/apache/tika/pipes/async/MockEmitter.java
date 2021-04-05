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
package org.apache.tika.pipes.async;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonMetadataSerializer;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;

public class MockEmitter implements Initializable, Emitter {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Connection connection;
    private String jdbc;
    private PreparedStatement insert;

    public MockEmitter() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(Metadata.class, new JsonMetadataSerializer());
        objectMapper.registerModule(module);
    }

    @Field
    public void setJdbc(String jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList)
            throws IOException, TikaEmitterException {
        emit(Collections
                .singletonList(new EmitData(new EmitKey(getName(), emitKey), metadataList)));
    }

    @Override
    public void emit(List<? extends EmitData> emitData) throws IOException, TikaEmitterException {
        for (EmitData d : emitData) {
            String json = objectMapper.writeValueAsString(d);
            try {
                insert.clearParameters();
                insert.setString(1, d.getEmitKey().getEmitKey());
                insert.setString(2, json);
                insert.execute();
            } catch (SQLException e) {
                throw new TikaEmitterException("problem inserting", e);
            }
        }
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        try {
            connection = DriverManager.getConnection(jdbc);
            String sql = "insert into emitted (emitkey, json) values (?, ?)";
            insert = connection.prepareStatement(sql);
        } catch (SQLException e) {
            throw new TikaConfigException("problem w connection", e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler)
            throws TikaConfigException {

    }
}
