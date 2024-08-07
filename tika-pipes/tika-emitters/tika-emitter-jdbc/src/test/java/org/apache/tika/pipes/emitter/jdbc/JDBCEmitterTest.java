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
package org.apache.tika.pipes.emitter.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.emitter.Emitter;
import org.apache.tika.pipes.emitter.EmitterManager;

public class JDBCEmitterTest {

    @Test
    public void testBasic(@TempDir Path tmpDir) throws Exception {
        Files.createDirectories(tmpDir.resolve("db"));
        Path dbDir = tmpDir.resolve("db/h2");
        Path config = tmpDir.resolve("tika-config.xml");
        String connectionString = "jdbc:h2:file:" + dbDir.toAbsolutePath();

        writeConfig("/configs/tika-config-jdbc-emitter.xml",
                connectionString, config);

        EmitterManager emitterManager = EmitterManager.load(config);
        Emitter emitter = emitterManager.getEmitter();
        List<String[]> data = new ArrayList<>();
        data.add(new String[]{"k1", "true", "k2", "some string1", "k3", "4", "k4", "100"});
        data.add(new String[]{"k1", "false", "k2", "some string2", "k3", "5", "k4", "101"});
        data.add(new String[]{"k1", "true", "k2", "some string3", "k3", "6", "k4", "102"});
        //test dates with and without timezones
        data.add(new String[]{"k1", "false", "k2", "some string4", "k3", "7", "k4", "103", "k5",
                "100002", "k6", "2022-11-04T17:10:15Z"});

        data.add(new String[]{"k1", "true", "k2", "some string5", "k3", "8", "k4", "104", "k5",
                "100002", "k6", "2022-11-04T17:10:15"});
        int id = 0;
        for (String[] d : data) {
            emitter.emit("id" + id++, Collections.singletonList(m(d)), new ParseContext());
        }

        try (Connection connection = DriverManager.getConnection(connectionString)) {
            try (Statement st = connection.createStatement()) {
                try (ResultSet rs = st.executeQuery("select * from test")) {
                    int rows = 0;
                    while (rs.next()) {
                        assertEquals("id" + rows, rs.getString(1));
                        assertEquals(rows % 2 == 0, rs.getBoolean(2));
                        assertEquals("some string" + (rows + 1), rs.getString(3));
                        assertEquals(rows + 4, rs.getInt(4));
                        assertEquals(100 + rows, rs.getLong(5));
                        if (rows > 2) {
                            assertEquals(100002, rs.getLong(6));
                            Timestamp timestamp = rs.getTimestamp(7);
                            String str = timestamp.toInstant().atZone(ZoneId.of("UTC")).toString();
                            //TODO fix this to work in other timezones
                            assertTrue(str.startsWith("2022-11"));
                        }
                        rows++;
                    }
                }
            }
        }
    }

    @Test
    public void testTableExists(@TempDir Path tmpDir) throws Exception {
        String createTable = "create table test (path varchar(512) primary key," +
                "k1 boolean,k2 varchar(512),k3 integer,k4 long);";

        Files.createDirectories(tmpDir.resolve("db"));
        Path dbDir = tmpDir.resolve("db/h2");
        Path config = tmpDir.resolve("tika-config.xml");
        String connectionString = "jdbc:h2:file:" + dbDir.toAbsolutePath();
        writeConfig("/configs/tika-config-jdbc-emitter-existing-table.xml",
                connectionString, config);

        try (Connection connection = DriverManager.getConnection(connectionString)) {
            connection.createStatement().execute(createTable);
        }
        EmitterManager emitterManager = EmitterManager.load(config);
        Emitter emitter = emitterManager.getEmitter();
        List<String[]> data = new ArrayList<>();
        data.add(new String[]{"k1", "true", "k2", "some string1", "k3", "4", "k4", "100"});
        data.add(new String[]{"k1", "false", "k2", "some string2", "k3", "5", "k4", "101"});
        data.add(new String[]{"k1", "true", "k2", "some string3", "k3", "6", "k4", "102"});
        int id = 0;
        for (String[] d : data) {
            emitter.emit("id" + id++, Collections.singletonList(m(d)), new ParseContext());
        }

        try (Connection connection = DriverManager.getConnection(connectionString)) {
            try (Statement st = connection.createStatement()) {
                try (ResultSet rs = st.executeQuery("select * from test")) {
                    int rows = 0;
                    while (rs.next()) {
                        assertEquals("id" + rows, rs.getString(1));
                        assertEquals(rows % 2 == 0, rs.getBoolean(2));
                        assertEquals("some string" + (rows + 1), rs.getString(3));
                        assertEquals(rows + 4, rs.getInt(4));
                        assertEquals(100 + rows, rs.getLong(5));
                        rows++;
                    }
                }
            }
        }
    }

    @Test
    public void testAttachments(@TempDir Path tmpDir) throws Exception {
        Files.createDirectories(tmpDir.resolve("db"));
        Path dbDir = tmpDir.resolve("db/h2");
        Path config = tmpDir.resolve("tika-config.xml");
        String connectionString = "jdbc:h2:file:" + dbDir.toAbsolutePath();

        writeConfig("/configs/tika-config-jdbc-emitter-attachments.xml",
                connectionString, config);

        EmitterManager emitterManager = EmitterManager.load(config);
        Emitter emitter = emitterManager.getEmitter();
        List<Metadata> data = new ArrayList<>();
        data.add(m("k1", "true", "k2", "some string1", "k3", "4", "k4", "100"));
        data.add(m("k1", "false", "k2", "some string2", "k3", "5", "k4", "101"));
        data.add(m("k1", "true", "k2", "some string3", "k3", "6", "k4", "102"));
        emitter.emit("id0", data, new ParseContext());


        try (Connection connection = DriverManager.getConnection(connectionString)) {
            try (Statement st = connection.createStatement()) {
                try (ResultSet rs = st.executeQuery("select * from test")) {
                    int rows = 0;
                    assertEquals("path", rs.getMetaData().getColumnName(1).toLowerCase(Locale.US));
                    assertEquals("attachment_num",
                            rs.getMetaData().getColumnName(2).toLowerCase(Locale.US));
                    while (rs.next()) {
                        assertEquals("id0", rs.getString(1));
                        assertEquals(rows, rs.getInt(2));
                        assertEquals(rows % 2 == 0, rs.getBoolean(3));
                        assertEquals("some string" + (rows + 1), rs.getString(4));
                        assertEquals(rows + 4, rs.getInt(5));
                        assertEquals(100 + rows, rs.getLong(6));
                        rows++;
                    }
                }
            }
        }
    }

    @Test
    public void testMultiValuedFields(@TempDir Path tmpDir) throws Exception {
        Files.createDirectories(tmpDir.resolve("db"));
        Path dbDir = tmpDir.resolve("db/h2");
        Path config = tmpDir.resolve("tika-config.xml");
        String connectionString = "jdbc:h2:file:" + dbDir.toAbsolutePath();

        writeConfig("/configs/tika-config-jdbc-emitter-multivalued.xml",
                connectionString, config);

        EmitterManager emitterManager = EmitterManager.load(config);
        Emitter emitter = emitterManager.getEmitter();
        List<Metadata> data = new ArrayList<>();
        Metadata m = new Metadata();
        m.add("k1", "first");
        m.add("k1", "second");
        m.add("k1", "third");
        m.add("k1", "fourth");
        data.add(m);
        emitter.emit("id0", data, new ParseContext());

        String expected = "first, second, third, fourth";
        int rows = 0;
        try (Connection connection = DriverManager.getConnection(connectionString)) {
            try (Statement st = connection.createStatement()) {
                try (ResultSet rs = st.executeQuery("select * from test")) {
                    assertEquals("path", rs.getMetaData().getColumnName(1).toLowerCase(Locale.US));
                    while (rs.next()) {
                        assertEquals("id0", rs.getString(1));
                        assertEquals(expected, rs.getString(2));
                        rows++;
                    }
                }
            }
        }
        assertEquals(1, rows);
    }

    @Test
    public void testVarcharTruncation(@TempDir Path tmpDir) throws Exception {
        Files.createDirectories(tmpDir.resolve("db"));
        Path dbDir = tmpDir.resolve("db/h2");
        Path config = tmpDir.resolve("tika-config.xml");
        String connectionString = "jdbc:h2:file:" + dbDir.toAbsolutePath();

        writeConfig("/configs/tika-config-jdbc-emitter-trunc.xml",
                connectionString, config);

        EmitterManager emitterManager = EmitterManager.load(config);
        Emitter emitter = emitterManager.getEmitter();
        List<String[]> data = new ArrayList<>();
        data.add(new String[]{"k1", "abcd"});
        data.add(new String[]{"k1", "abcdefghijklmnopqrs"});
        data.add(new String[]{"k1", "abcdefghijk"});
        int id = 0;
        for (String[] d : data) {
            emitter.emit("id" + id++, Collections.singletonList(m(d)), new ParseContext());
        }

        int rows = 0;
        try (Connection connection = DriverManager.getConnection(connectionString)) {
            try (Statement st = connection.createStatement()) {
                try (ResultSet rs = st.executeQuery("select * from test")) {
                    while (rs.next()) {
                        String s = rs.getString(2);
                        assertTrue(s.length() < 13);
                        assertFalse(s.contains("m"));
                        rows++;
                    }
                }
            }
        }
        assertEquals(3, rows);
    }

    private void writeConfig(String srcConfig, String dbDir, Path config) throws IOException {
        String xml = IOUtils.resourceToString(srcConfig, StandardCharsets.UTF_8);
        xml = xml.replace("CONNECTION_STRING", dbDir);
        Files.write(config, xml.getBytes(StandardCharsets.UTF_8));
    }

    private Metadata m(String... strings) {
        Metadata metadata = new Metadata();
        for (int i = 0; i < strings.length; i++) {
            metadata.set(strings[i], strings[++i]);
        }
        return metadata;
    }
}
