package org.apache.tika.pipes.async;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitData;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.TikaEmitterException;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MockEmitter implements Initializable, Emitter {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private Connection connection;
    private String jdbc;
    private PreparedStatement insert;

    @Field
    public void setJdbc(String jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String getName() {
        return "mock";
    }

    @Override
    public void emit(String emitKey, List<Metadata> metadataList) throws IOException, TikaEmitterException {
        emit(Collections.singletonList(
                new EmitData(
                new EmitKey(getName(), emitKey), metadataList)));
    }

    @Override
    public void emit(List<? extends EmitData> emitData) throws IOException, TikaEmitterException {
        for (EmitData d : emitData) {
            String json = objectMapper.writeValueAsString(d);
            try {
                insert.clearParameters();
                insert.setString(1, json);
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
            String sql = "insert into emitted (json) values (?)";
            insert = connection.prepareStatement(sql);
        } catch (SQLException e) {
            throw new TikaConfigException("problem w connection", e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {

    }
}
