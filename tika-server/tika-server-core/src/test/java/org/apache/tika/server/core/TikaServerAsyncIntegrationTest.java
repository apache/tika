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
package org.apache.tika.server.core;


import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.core.Response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.FileUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.serialization.JsonFetchEmitTupleList;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;

@Ignore("useful for development...need to turn it into a real unit test")
public class TikaServerAsyncIntegrationTest extends IntegrationTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaServerAsyncIntegrationTest.class);
    private static final int NUM_FILES = 1000;
    private static final String EMITTER_NAME = "fse";
    private static final String FETCHER_NAME = "fsf";
    private static FetchEmitTuple.ON_PARSE_EXCEPTION ON_PARSE_EXCEPTION =
            FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT;
    private static Path TMP_DIR;
    private static Path TMP_OUTPUT_DIR;
    private static String TIKA_CONFIG_XML;
    private static Path TIKA_CONFIG;
    private static List<String> FILE_LIST = new ArrayList<>();
    private static String[] FILES = new String[]{"hello_world.xml", "null_pointer.xml"
            // "heavy_hang_30000.xml", "real_oom.xml", "system_exit.xml"
    };

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        TMP_DIR = Files.createTempDirectory("tika-emitter-test-");
        Path inputDir = TMP_DIR.resolve("input");
        TMP_OUTPUT_DIR = TMP_DIR.resolve("output");
        Files.createDirectories(inputDir);
        Files.createDirectories(TMP_OUTPUT_DIR);

        for (int i = 0; i < NUM_FILES; i++) {
            for (String mockFile : FILES) {
                String targetName = i + "-" + mockFile;
                Path target = inputDir.resolve(targetName);
                FILE_LIST.add(targetName);
                Files.copy(TikaEmitterTest.class
                        .getResourceAsStream("/test-documents/mock/" + mockFile), target);

            }
        }
        TIKA_CONFIG = TMP_DIR.resolve("tika-config.xml");

        TIKA_CONFIG_XML =
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<properties>" + "<fetchers>" +
                        "<fetcher class=\"org.apache.tika.pipes.fetcher.FileSystemFetcher\">" +
                        "<params>" + "<param name=\"name\" type=\"string\">" + FETCHER_NAME +
                        "</param>" + "<param name=\"basePath\" type=\"string\">" +
                        inputDir.toAbsolutePath() + "</param>" + "</params>" + "</fetcher>" +
                        "</fetchers>" + "<emitters>" +
                        "<emitter class=\"org.apache.tika.pipes.emitter.fs.FileSystemEmitter\">" +
                        "<params>" + "<param name=\"name\" type=\"string\">" + EMITTER_NAME +
                        "</param>" +

                        "<param name=\"basePath\" type=\"string\">" +
                        TMP_OUTPUT_DIR.toAbsolutePath() + "</param>" + "</params>" + "</emitter>" +
                        "</emitters>" +
                        "<server><enableUnsecureFeatures>true</enableUnsecureFeatures></server>" +
                        "</properties>";

        FileUtils.write(TIKA_CONFIG.toFile(), TIKA_CONFIG_XML, UTF_8);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        FileUtils.deleteDirectory(TMP_DIR.toFile());
    }

    @Before
    public void setUpEachTest() throws Exception {
        for (String problemFile : FILES) {
            Path targ = TMP_OUTPUT_DIR.resolve(problemFile + ".json");

            if (Files.exists(targ)) {
                Files.delete(targ);
                assertFalse(Files.isRegularFile(targ));
            }
        }
    }


    @Test
    public void testBasic() throws Exception {

        Thread serverThread = new Thread() {
            @Override
            public void run() {
                TikaServerCli.main(new String[]{"-p", INTEGRATION_TEST_PORT, "-config",
                        TIKA_CONFIG.toAbsolutePath().toString()});
            }
        };
        serverThread.start();

        try {
            JsonNode response = sendAsync(FILE_LIST);
            int expected = (ON_PARSE_EXCEPTION == FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT) ?
                    FILE_LIST.size() : FILE_LIST.size() / 2;
            int targets = 0;
            while (targets < FILE_LIST.size()) {
                targets = countTargets();
                Thread.sleep(1000);
            }
        } finally {
            serverThread.interrupt();
        }
    }

    private int countTargets() {
        return TMP_OUTPUT_DIR.toFile().listFiles().length;
    }

    private JsonNode sendAsync(List<String> fileNames) throws Exception {
        awaitServerStartup();
        List<FetchEmitTuple> tuples = new ArrayList<>();
        for (String f : fileNames) {
            tuples.add(getFetchEmitTuple(f));
        }
        String json = JsonFetchEmitTupleList.toJson(tuples);

        Response response =
                WebClient.create(endPoint + "/async").accept("application/json").post(json);
        Reader reader = new InputStreamReader((InputStream) response.getEntity(), UTF_8);
        return new ObjectMapper().readTree(reader);
    }

    private FetchEmitTuple getFetchEmitTuple(String fileName) throws IOException {
        return new FetchEmitTuple(new FetchKey(FETCHER_NAME, fileName),
                new EmitKey(EMITTER_NAME, ""), new Metadata(), ON_PARSE_EXCEPTION);
    }
}
