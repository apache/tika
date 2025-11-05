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
package org.apache.tika.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TikaCLIAsyncTest {

    private static final Logger LOG = LoggerFactory.getLogger(TikaCLI.class);

    final static String JSON_TEMPLATE = """
            {
              "plugins" : {
                "fetchers": {
                  "fsf": {
                    "file-system-fetcher": {
                      "basePath": "FETCHER_BASE_PATH",
                      "extractFileSystemMetadata": false
                    }
                  }
                },
                "emitters": {
                  "fse": {
                    "file-system-emitter": {
                      "basePath": "EMITTER_BASE_PATH",
                      "fileExtension": "jsn",
                      "onExists":"EXCEPTION",
                      "prettyPrint": true
                    }
                  }
                },
                "pipes_iterator": {
                  "fspi": {
                    "file-system-pipes-iterator": {
                      "basePath": "FETCHER_BASE_PATH",
                      "countTotal": true,
                      "baseConfig": {
                        "fetcherId": "fsf",
                        "emitterId": "fse",
                        "handlerConfig": {
                          "type": "TEXT",
                          "parseMode": "RMETA",
                          "writeLimit": -1,
                          "maxEmbeddedResources": -1,
                          "throwOnWriteLimitReached": true
                        },
                        "onParseException": "EMIT",
                        "maxWaitMs": 600000,
                        "queueSize": 10000
                      }
                    }
                  }
                }
              },
              "pluginsPaths": "PLUGINS_PATHS"
            }
            """;

    final static String JSON_TEMPLATE_FETCH_EMIT_ONLY = """
            {
              "plugins" : {
                "fetchers": {
                  "fsf": {
                    "file-system-fetcher": {
                      "basePath": "FETCHER_BASE_PATH",
                      "extractFileSystemMetadata": false
                    }
                  }
                },
                "emitters": {
                  "fse": {
                    "file-system-emitter": {
                      "basePath": "EMITTER_BASE_PATH",
                      "fileExtension": "jsn",
                      "onExists":"EXCEPTION",
                      "prettyPrint": true
                    }
                  }
                }
              },
              "pluginsPaths": "PLUGINS_PATHS"
            }
            """;

    static final File TEST_DATA_FILE = new File("src/test/resources/test-data");

    /* Test members */
    private ByteArrayOutputStream outContent = null;
    private ByteArrayOutputStream errContent = null;
    private PrintStream stdout = null;
    private PrintStream stderr = null;

    private static Path ASYNC_CONFIG;
    private static Path ASYNC_PLUGINS_CONFIG;

    @TempDir
    private static Path ASYNC_OUTPUT_DIR;

    @BeforeAll
    public static void setUpClass() throws Exception {
        ASYNC_CONFIG = Files.createTempFile(ASYNC_OUTPUT_DIR, "async-config-", ".xml");
        String xml = "<properties>" + "<async>" + "<numClients>3</numClients>" + "<tikaConfig>" + ASYNC_CONFIG.toAbsolutePath() + "</tikaConfig>" + "</async>" +
                "<pipesIterator class=\"org.apache.tika.pipes.pipesiterator.fs.FileSystemPipesIterator\">" + "<basePath>" + TEST_DATA_FILE.getAbsolutePath() + "</basePath>" +
                "<fetcherId>fsf</fetcherId>" + "<emitterId>fse</emitterId>" + "</pipesIterator>" + "</properties>";
        Files.write(ASYNC_CONFIG, xml.getBytes(UTF_8));
        ASYNC_PLUGINS_CONFIG = Files.createTempFile(ASYNC_OUTPUT_DIR, "plugins-", ".json");

        Path pluginsDir = Paths.get("target/plugins");
        if (! Files.isDirectory(pluginsDir)) {
            LOG.warn("CAN'T FIND PLUGINS DIR. pwd={}", Paths.get("").toAbsolutePath().toString());
        }
        String json = JSON_TEMPLATE.replace("FETCHER_BASE_PATH", TEST_DATA_FILE.getAbsolutePath().toString())
                                   .replace("EMITTER_BASE_PATH", ASYNC_OUTPUT_DIR.toAbsolutePath().toString())
                                   .replace("PLUGINS_PATHS", pluginsDir.toAbsolutePath().toString());
        Files.writeString(ASYNC_PLUGINS_CONFIG, json, UTF_8);
    }

    /**
     * reset resourcePrefix
     * save original System.out and System.err
     * clear outContent and errContent if they are not empty
     * set outContent and errContent as System.out and System.err
     */
    @BeforeEach
    public void setUp() throws Exception {
        stdout = System.out;
        stderr = System.err;
        resetContent();
    }

    /**
     * Tears down the test. Returns the System.out and System.err
     */
    @AfterEach
    public void tearDown() {
        System.setOut(stdout);
        System.setErr(stderr);
    }

    /**
     * clear outContent and errContent if they are not empty by create a new one.
     * set outContent and errContent as System.out and System.err
     */
    private void resetContent() throws Exception {
        if (outContent == null || outContent.size() > 0) {
            outContent = new ByteArrayOutputStream();
            System.setOut(new PrintStream(outContent, true, UTF_8.name()));
        }

        if (errContent == null || errContent.size() > 0) {
            errContent = new ByteArrayOutputStream();
            System.setErr(new PrintStream(errContent, true, UTF_8.name()));
        }
    }


    @Test
    public void testAsync() throws Exception {
        //extension is "jsn" to avoid conflict with json config

        String content = getParamOutContent("-c", ASYNC_CONFIG.toAbsolutePath().toString(),
                "-a", ASYNC_PLUGINS_CONFIG.toAbsolutePath().toString());

        int json = 0;
        for (File f : ASYNC_OUTPUT_DIR
                .toFile()
                .listFiles()) {
            if (f
                    .getName()
                    .endsWith(".jsn")) {
                //check one file for pretty print
                if (f
                        .getName()
                        .equals("coffee.xls.jsn")) {
                    checkForPrettyPrint(f);
                }
                json++;
            }
        }
        assertEquals(21, json);
    }

    private void checkForPrettyPrint(File f) throws IOException {
        String json = FileUtils.readFileToString(f, UTF_8);
        int previous = json.indexOf("Content-Length");
        assertTrue(previous > -1);
        for (String k : new String[]{"Content-Type", "dc:creator", "dcterms:created", "dcterms:modified", "X-TIKA:content\""}) {
            int i = json.indexOf(k);
            assertTrue(i > -1, "should have found " + k);
            assertTrue(i > previous, "bad order: " + k + " at " + i + " not less than " + previous);
            previous = i;
        }
    }

    /**
     * reset outContent and errContent if they are not empty
     * run given params in TikaCLI and return outContent String with UTF-8
     */
    String getParamOutContent(String... params) throws Exception {
        resetContent();
        TikaCLI.main(params);
        return outContent.toString("UTF-8");
    }

}
