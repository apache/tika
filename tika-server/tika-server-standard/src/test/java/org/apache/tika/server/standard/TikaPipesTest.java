/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import java.nio.charset.StandardCharsets;
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
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.FetchEmitTuple;
import org.apache.tika.pipes.HandlerConfig;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.extractor.EmbeddedDocumentBytesConfig;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetcher.FetcherManager;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.serialization.JsonMetadataList;
import org.apache.tika.serialization.pipes.JsonFetchEmitTuple;
import org.apache.tika.server.core.CXFTestBase;
import org.apache.tika.server.core.FetcherStreamFactory;
import org.apache.tika.server.core.InputStreamFactory;
import org.apache.tika.server.core.TikaServerParseExceptionMapper;
import org.apache.tika.server.core.resource.PipesResource;
import org.apache.tika.server.core.writer.JSONObjWriter;
import org.apache.tika.utils.ProcessUtils;

/**
 * This offers basic integration tests with fetchers and emitters.
 * We use file system fetchers and emitters.
 */
public class TikaPipesTest extends CXFTestBase {

    private static final String PIPES_PATH = "/pipes";
    private static final String TEST_RECURSIVE_DOC = "test_recursive_embedded.docx";
    @TempDir
    private static Path TMP_WORKING_DIR;
    private static Path TMP_OUTPUT_DIR;
    private static Path TMP_OUTPUT_FILE;
    private static Path TMP_BYTES_DIR;
    private static Path TIKA_PIPES_LOG4j2_PATH;
    private static Path TIKA_CONFIG_PATH;
    private static String TIKA_CONFIG_XML;
    private static FetcherManager FETCHER_MANAGER;

    @BeforeAll
    public static void setUpBeforeClass() throws Exception {
        Path inputDir = TMP_WORKING_DIR.resolve("input");
        TMP_OUTPUT_DIR = TMP_WORKING_DIR.resolve("output");
        TMP_BYTES_DIR = TMP_WORKING_DIR.resolve("bytes");
        TMP_OUTPUT_FILE = TMP_OUTPUT_DIR.resolve(TEST_RECURSIVE_DOC + ".json");

        Files.createDirectories(inputDir);
        Files.createDirectories(TMP_OUTPUT_DIR);
        Files.copy(TikaPipesTest.class.getResourceAsStream("/test-documents/" + TEST_RECURSIVE_DOC), inputDir.resolve("test_recursive_embedded.docx"),
                StandardCopyOption.REPLACE_EXISTING);

        TIKA_CONFIG_PATH = Files.createTempFile(TMP_WORKING_DIR, "tika-pipes-", ".xml");
        TIKA_PIPES_LOG4j2_PATH = Files.createTempFile(TMP_WORKING_DIR, "log4j2-", ".xml");
        Files.copy(TikaPipesTest.class.getResourceAsStream("/log4j2.xml"), TIKA_PIPES_LOG4j2_PATH, StandardCopyOption.REPLACE_EXISTING);

        //TODO: templatify this config
        TIKA_CONFIG_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + "<properties>" + "<fetchers>" + "<fetcher class=\"org.apache.tika.pipes.fetcher.fs.FileSystemFetcher\">" +
                "<params>" + "<name>fsf</name>" + "<basePath>" + inputDir.toAbsolutePath() + "</basePath>" + "</params>" + "</fetcher>" + "</fetchers>" + "<emitters>" +
                "<emitter class=\"org.apache.tika.pipes.emitter.fs.FileSystemEmitter\">" + "<params>" + "<name>fse</name>" + "<basePath>" + TMP_OUTPUT_DIR.toAbsolutePath() +
                "</basePath>" + "</params>" + "</emitter>" + "<emitter class=\"org.apache.tika.pipes.emitter.fs.FileSystemEmitter\">" + "<params>" + "<name>bytes</name>" +
                "<basePath>" + TMP_BYTES_DIR.toAbsolutePath() + "</basePath>" + "</params>" + "</emitter>" + "</emitters>" + "<pipes><params><tikaConfig>" +
                ProcessUtils.escapeCommandLine(TIKA_CONFIG_PATH
                        .toAbsolutePath()
                        .toString()) + "</tikaConfig><numClients>10</numClients>" + "<forkedJvmArgs>" + "<arg>-Xmx256m</arg>" + "<arg>-Dlog4j.configurationFile=file:" +
                ProcessUtils.escapeCommandLine(TIKA_PIPES_LOG4j2_PATH
                        .toAbsolutePath()
                        .toString()) + "</arg>" + "</forkedJvmArgs>" + "</params></pipes>" + "</properties>";
        Files.write(TIKA_CONFIG_PATH, TIKA_CONFIG_XML.getBytes(StandardCharsets.UTF_8));
    }


    @BeforeEach
    public void setUpEachTest() throws Exception {
        if (Files.exists(TMP_OUTPUT_FILE)) {
            Files.delete(TMP_OUTPUT_FILE);
        }

        assertFalse(Files.isRegularFile(TMP_OUTPUT_FILE));
    }

    @Override
    protected void setUpResources(JAXRSServerFactoryBean sf) {
        List<ResourceProvider> rCoreProviders = new ArrayList<>();
        try {
            rCoreProviders.add(new SingletonResourceProvider(new PipesResource(TIKA_CONFIG_PATH)));
        } catch (IOException | TikaConfigException e) {
            throw new RuntimeException(e);
        }
        sf.setResourceProviders(rCoreProviders);
    }

    @Override
    protected void setUpProviders(JAXRSServerFactoryBean sf) {
        List<Object> providers = new ArrayList<>();
        providers.add(new TikaServerParseExceptionMapper(true));
        providers.add(new JSONObjWriter());
        sf.setProviders(providers);
    }

    @Override
    protected InputStream getTikaConfigInputStream() {
        return new ByteArrayInputStream(TIKA_CONFIG_XML.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    protected InputStreamFactory getInputStreamFactory(InputStream is) {
        //TODO: fix this to read from the is
        return new FetcherStreamFactory(FETCHER_MANAGER);
    }


    @Test
    public void testBasic() throws Exception {

        FetchEmitTuple t = new FetchEmitTuple("myId", new FetchKey("fsf", "test_recursive_embedded.docx"), new EmitKey("fse", ""));
        StringWriter writer = new StringWriter();
        JsonFetchEmitTuple.toJson(t, writer);

        String getUrl = endPoint + PIPES_PATH;
        Response response = WebClient
                .create(getUrl)
                .accept("application/json")
                .post(writer.toString());
        assertEquals(200, response.getStatus());

        List<Metadata> metadataList = null;
        try (Reader reader = Files.newBufferedReader(TMP_OUTPUT_FILE)) {
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
        HandlerConfig handlerConfig = new HandlerConfig(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, HandlerConfig.PARSE_MODE.CONCATENATE, -1, -1, true);
        parseContext.set(HandlerConfig.class, handlerConfig);

        FetchEmitTuple t = new FetchEmitTuple("myId", new FetchKey("fsf", "test_recursive_embedded.docx"),
                new EmitKey("fse", ""), new Metadata(), parseContext,
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
        try (Reader reader = Files.newBufferedReader(TMP_OUTPUT_FILE)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(1, metadataList.size());
        assertContains("When in the Course", metadataList
                .get(0)
                .get(TikaCoreProperties.TIKA_CONTENT));
    }

    @Test
    public void testBytes() throws Exception {
        EmbeddedDocumentBytesConfig config = new EmbeddedDocumentBytesConfig(true);
        config.setEmitter("bytes");
        config.setIncludeOriginal(true);
        config.setEmbeddedIdPrefix("-");
        config.setZeroPadName(10);
        config.setSuffixStrategy(EmbeddedDocumentBytesConfig.SUFFIX_STRATEGY.EXISTING);
        ParseContext parseContext = new ParseContext();
        parseContext.set(HandlerConfig.class, HandlerConfig.DEFAULT_HANDLER_CONFIG);
        parseContext.set(EmbeddedDocumentBytesConfig.class, config);
        FetchEmitTuple t =
                new FetchEmitTuple("myId", new FetchKey("fsf", "test_recursive_embedded.docx"), new EmitKey("fse", "test_recursive_embedded.docx"), new Metadata(), parseContext,
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
        try (Reader reader = Files.newBufferedReader(TMP_OUTPUT_FILE)) {
            metadataList = JsonMetadataList.fromJson(reader);
        }
        assertEquals(12, metadataList.size());
        assertContains("When in the Course", metadataList
                .get(6)
                .get(TikaCoreProperties.TIKA_CONTENT));
        Map<String, Long> expected = loadExpected();
        Map<String, Long> byteFileNames = getFileNames(TMP_BYTES_DIR);
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
        //TODO -- this should keep the suffix -- figure out why it isn't
        m.put("test_recursive_embedded.docx-0000000000", 27082l);
        m.put("test_recursive_embedded.docx-0000000003.txt", 8l);
        m.put("test_recursive_embedded.docx-0000000011.txt", 7l);
        m.put("test_recursive_embedded.docx-0000000005.zip", 4492l);
        m.put("test_recursive_embedded.docx-0000000010.zip", 163l);
        return m;
    }

    private Map<String, Long> getFileNames(Path p) throws Exception {
        final Map<String, Long> ret = new HashMap<>();
        Files.walkFileTree(TMP_BYTES_DIR, new FileVisitor<Path>() {
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
