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
package org.apache.tika.server.standard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.Response;
import org.apache.commons.io.FileUtils;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.config.loader.TikaJsonConfig;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.FetchEmitTuple;
import org.apache.tika.pipes.api.ParseMode;
import org.apache.tika.pipes.api.emitter.EmitKey;
import org.apache.tika.pipes.api.fetcher.FetchKey;
import org.apache.tika.pipes.core.extractor.UnpackConfig;
import org.apache.tika.pipes.core.fetcher.FetcherManager;
import org.apache.tika.pipes.core.serialization.JsonFetchEmitTuple;
import org.apache.tika.plugins.TikaPluginManager;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.PipesResource;
import org.apache.tika.server.core.writer.JSONObjWriter;

/**
 * This offers basic integration tests with fetchers and emitters.
 * We use file system fetchers and emitters.
 */
public class TikaPipesTest extends CXFTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(TikaPipesTest.class);

    private static final String PIPES_PATH = "/pipes";
    private static final String TEST_RECURSIVE_DOC = "test_recursive_embedded.docx";
    private static final String TEST_TWO_BOXES_PDF = "testPDFTwoTextBoxes.pdf";

    @TempDir
    private static Path TMP_WORKING_DIR;
    private static Path OUTPUT_JSON_DIR;
    private static Path OUTPUT_BYTES_DIR;
    private static Path TIKA_PIPES_LOG4j2_PATH;
    private static Path TIKA_CONFIG_PATH;
    private static FetcherManager FETCHER_MANAGER;

    private PipesResource pipesResource;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        Path inputDir = TMP_WORKING_DIR.resolve("input");
        OUTPUT_JSON_DIR = TMP_WORKING_DIR.resolve("output");
        OUTPUT_BYTES_DIR = TMP_WORKING_DIR.resolve("bytes");

        Files.createDirectories(inputDir);
        Files.createDirectories(OUTPUT_JSON_DIR);
        Files.copy(TikaPipesTest.class.getResourceAsStream("/test-documents/" + TEST_RECURSIVE_DOC), inputDir.resolve("test_recursive_embedded.docx"),
                StandardCopyOption.REPLACE_EXISTING);
        Files.copy(TikaPipesTest.class.getResourceAsStream("/test-documents/" + TEST_TWO_BOXES_PDF), inputDir.resolve(TEST_TWO_BOXES_PDF),
                StandardCopyOption.REPLACE_EXISTING);
        TIKA_PIPES_LOG4j2_PATH = Files.createTempFile(TMP_WORKING_DIR, "log4j2-", ".xml");
        Files.copy(TikaPipesTest.class.getResourceAsStream("/log4j2.xml"), TIKA_PIPES_LOG4j2_PATH, StandardCopyOption.REPLACE_EXISTING);

        TIKA_CONFIG_PATH = Files.createTempFile(TMP_WORKING_DIR, "tika-pipes-config-", ".json");
        CXFTestBase.createPluginsConfig(TIKA_CONFIG_PATH, inputDir, OUTPUT_JSON_DIR, OUTPUT_BYTES_DIR, 10000L);

        TikaJsonConfig tikaJsonConfig = TikaJsonConfig.load(TIKA_CONFIG_PATH);
        TikaPluginManager pluginManager = TikaPluginManager.load(tikaJsonConfig);
        FETCHER_MANAGER = FetcherManager.load(pluginManager, tikaJsonConfig);

    }


    @BeforeEach
    public void setUpEachTest() throws Exception {
        FileUtils.deleteDirectory(OUTPUT_JSON_DIR.toFile());
        assertFalse(Files.isDirectory(OUTPUT_JSON_DIR));
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        List<ResourceProvider> rCoreProviders = new ArrayList<>();
        try {
            pipesResource = new PipesResource(TIKA_CONFIG_PATH);
            rCoreProviders.add(new SingletonResourceProvider(pipesResource));
        } catch (IOException | TikaConfigException e) {
            throw new RuntimeException(e);
        }
        sf.setResourceProviders(rCoreProviders);
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        if (pipesResource != null) {
            pipesResource.close();
            pipesResource = null;
        }
        super.tearDown();
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper(true));
        providers.add(new JSONObjWriter());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() throws IOException {
        return new ByteArrayInputStream(Files.readAllBytes(TIKA_CONFIG_PATH));
    }

    @Override
    protected InputStream getPipesConfigInputStream() throws IOException {
        return new ByteArrayInputStream(Files.readAllBytes(TIKA_CONFIG_PATH));
    }


    @Test
    public void testBasic() throws Exception {

        FetchEmitTuple t = new FetchEmitTuple("myId", new FetchKey(FETCHER_ID, "test_recursive_embedded.docx"),
                new EmitKey(EMITTER_JSON_ID, ""));
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + PIPES_PATH;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .post(writer.toString());
        assertEquals(200, response.getStatus());

        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(OUTPUT_JSON_DIR.resolve(TEST_RECURSIVE_DOC + ".json"))) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(12, metadataList.size());
        assertContains("When in the Course", metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testConcatenated() throws Exception {
        ParseContext parseContext = new ParseContext();
        // Set ParseMode directly - it's now separate from ContentHandlerFactory
        parseContext.set(ParseMode.class, ParseMode.CONCATENATE);

        FetchEmitTuple t = new FetchEmitTuple("myId", new FetchKey(FETCHER_ID, "test_recursive_embedded.docx"),
                new EmitKey(EMITTER_JSON_ID, ""), new Metadata(), parseContext,
                FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + PIPES_PATH;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .post(writer.toString());
        assertEquals(200, response.getStatus());

        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(OUTPUT_JSON_DIR.resolve(TEST_RECURSIVE_DOC + ".json"))) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        assertContains("When in the Course", metadataList
                .get(0)
                .get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testPDFConfig() throws Exception {
        ParseContext parseContext = new ParseContext();
        // Configure PDFParser via JSON config (pdf-parser is self-configuring)
        parseContext.setJsonConfig("pdf-parser", "{\"sortByPosition\": true}");

        FetchEmitTuple t = new FetchEmitTuple("myId", new FetchKey(FETCHER_ID, TEST_TWO_BOXES_PDF),
                new EmitKey(EMITTER_JSON_ID, ""), new Metadata(), parseContext);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);
        String getUrl = endPoint + PIPES_PATH;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .post(writer.toString());
        assertEquals(200, response.getStatus());

        List<Metadata> metadataList = null;
        Path outputFile = OUTPUT_JSON_DIR.resolve(TEST_TWO_BOXES_PDF + ".json");
        try (Reader reader = Files.newBufferedReader(outputFile)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        String content = metadataList.get(0).get(TikaCoreProperties.TIKA_CONTENT);
        content = content.replaceAll("\\s+", " ");
        // Column text is now interleaved:
        assertContains(
                "Left column line 1 Right column line 1 Left colu mn line 2 Right column line 2",
                content);
    }

    @Test
    public void testBytes() throws Exception {
        UnpackConfig config = new UnpackConfig(true);
        config.setEmitter(EMITTER_BYTES_ID);
        config.setIncludeOriginal(true);
        config.setKeyBaseStrategy(UnpackConfig.KEY_BASE_STRATEGY.CUSTOM);
        config.setEmitKeyBase("test_recursive_embedded.docx");
        config.setEmbeddedIdPrefix("-");
        config.setZeroPadName(10);
        config.setSuffixStrategy(UnpackConfig.SUFFIX_STRATEGY.EXISTING);
        ParseContext parseContext = new ParseContext();
        // Set default content handler and parse mode
        parseContext.set(ContentHandlerFactory.class,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        parseContext.set(ParseMode.class, ParseMode.RMETA);
        parseContext.set(UnpackConfig.class, config);
        FetchEmitTuple t =
                new FetchEmitTuple("myId", new FetchKey(FETCHER_ID, "test_recursive_embedded.docx"),
                        new EmitKey(EMITTER_JSON_ID, "test_recursive_embedded.docx"), new Metadata(), parseContext,
                        FetchEmitTuple.ON_PARSE_EXCEPTION.EMIT);
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);
        FetchEmitTuple deserialized = JsonFetchEmitTuple.fromJson(new StringReader(writer.toString()));

        assertEquals(t, deserialized);
        String getUrl = endPoint + PIPES_PATH;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .post(writer.toString());
        assertEquals(200, response.getStatus());

        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(OUTPUT_JSON_DIR.resolve(TEST_RECURSIVE_DOC + ".json"))) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(12, metadataList.size());
        assertContains("When in the Course", metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT));
        Map<String, Long> expected = loadExpected();
        Map<String, Long> byteFileNames = getFileNames(OUTPUT_BYTES_DIR);
        assertEquals(expected, byteFileNames);
    }

    private Map<String, Long> loadExpected() {
        Map<String, Long> m = new HashMap<>();
        m.put("test_recursive_embedded.docx-0000000009.txt", 8151l);
        m.put("test_recursive_embedded.docx-0000000007.txt", 8l);
        m.put("test_recursive_embedded.docx-0000000006.txt", 8l);
        m.put("test_recursive_embedded.docx-0000000002.zip", 4827l);
        m.put("test_recursive_embedded.docx-0000000001.emf", 4992l);
        m.put("test_recursive_embedded.docx-0000000008.zip", 4048l);
        m.put("test_recursive_embedded.docx-0000000004.txt", 8l);
        m.put("test_recursive_embedded.docx-0000000000.docx", 27082l);
        m.put("test_recursive_embedded.docx-0000000003.txt", 8l);
        m.put("test_recursive_embedded.docx-0000000011.txt", 7l);
        m.put("test_recursive_embedded.docx-0000000005.zip", 4492l);
        m.put("test_recursive_embedded.docx-0000000010.zip", 163l);
        return m;
    }

    private Map<String, Long> getFileNames(Path p) throws Exception {
        final Map<String, Long> ret = new HashMap<>();
        Files.walkFileTree(OUTPUT_BYTES_DIR, new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                ret.put(file
                        .getFileName()
                        .toString(), Files.size(file));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });
        return ret;
    }
}
