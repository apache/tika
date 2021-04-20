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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;

public class AsyncProcessorTest {

    private Path dbDir;
    private Path dbFile;
    private Connection connection;
    private Path tikaConfigPath;

    @Before
    public void setUp() throws SQLException, IOException {
        dbDir = Files.createTempDirectory("async-db");
        dbFile = dbDir.resolve("emitted-db");
        String jdbc = "jdbc:h2:file:" + dbFile.toAbsolutePath().toString() + ";AUTO_SERVER=TRUE";
        String sql = "create table emitted (id int auto_increment primary key, " +
                "emitkey varchar(2000), json varchar(20000))";

        connection = DriverManager.getConnection(jdbc);
        connection.createStatement().execute(sql);
        tikaConfigPath = dbDir.resolve("tika-config.xml");
        String xml = "" + "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" + "<properties>" +
                "  <emitters>" + "  <emitter class=\"org.apache.tika.pipes.async.MockEmitter\">\n" +
                "    <params>\n" + "      <param name=\"name\" type=\"string\">mock</param>\n" +
                "      <param name=\"jdbc\" type=\"string\">" + jdbc + "</param>\n" +
                "    </params>" + "  </emitter>" + "  </emitters>" + "  <fetchers>" +
                "    <fetcher class=\"org.apache.tika.pipes.async.MockFetcher\">" +
                "      <param name=\"name\" type=\"string\">mock</param>\n" + "    </fetcher>" +
                "  </fetchers>" + "</properties>";
        Files.write(tikaConfigPath, xml.getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() throws SQLException, IOException {
        connection.createStatement().execute("drop table emitted");
        connection.close();
        FileUtils.deleteDirectory(dbDir.toFile());
    }

    @Test
    public void testBasic() throws Exception {


        AsyncProcessor processor = AsyncProcessor.build(tikaConfigPath);
        int max = 100;
        for (int i = 0; i < max; i++) {
            FetchEmitTuple t = new FetchEmitTuple(new FetchKey("mock", "key-" + i),
                    new EmitKey("mock", "emit-" + i), new Metadata());
            processor.offer(t, 1000);
        }
        processor.close();
        String sql = "select emitkey from emitted";
        Set<String> emitKeys = new HashSet<>();
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String emitKey = rs.getString(1);
                emitKeys.add(emitKey);
            }
        }
        assertEquals(max, emitKeys.size());
        for (int i = 0; i < max; i++) {
            assertTrue(emitKeys.contains("emit-" + i));
        }
    }
}
