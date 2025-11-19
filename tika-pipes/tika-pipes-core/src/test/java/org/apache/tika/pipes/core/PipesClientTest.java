/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package org.apache.tika.pipes.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.tika.config.TikaTaskTimeout;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.metadata.filter.MockUpperCaseFilter;
import org.apache.tika.metadata.listfilter.AttachmentCountingListFilter;
import org.apache.tika.metadata.listfilter.CompositeMetadataListFilter;
import org.apache.tika.metadata.listfilter.MetadataListFilter;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.core.emitter.EmitKey;
import org.apache.tika.pipes.core.fetcher.FetchKey;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

public class PipesClientTest {
    String fetcherName = "fs";
    String testPdfFile = "testOverlappingText.pdf";

    private PipesClient pipesClient;

    @BeforeEach
    public void init() throws TikaConfigException, IOException, ParserConfigurationException,
                    SAXException {
        Path tikaConfigPath = Paths.get("src", "test", "resources", "org", "apache", "tika",
                        "pipes", "core", "tika-sample-config.xml");
        PipesConfig pipesConfig = PipesConfig.load(tikaConfigPath);
        pipesClient = new PipesClient(pipesConfig);
    }

    @Test
    public void testBasic() throws IOException, InterruptedException {
        PipesResult pipesResult = pipesClient.process(new FetchEmitTuple(testPdfFile,
                        new FetchKey(fetcherName, testPdfFile), new EmitKey(), new Metadata(),
                        new ParseContext(), FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        Assertions.assertNotNull(pipesResult.getEmitData().getMetadataList());
        assertEquals(1, pipesResult.getEmitData().getMetadataList().size());
        Metadata metadata = pipesResult.getEmitData().getMetadataList().get(0);
        assertEquals("testOverlappingText.pdf", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testMetadataFilter() throws IOException, InterruptedException {
        ParseContext parseContext = new ParseContext();
        MetadataFilter metadataFilter =
                        new CompositeMetadataFilter(List.of(new MockUpperCaseFilter()));
        parseContext.set(MetadataFilter.class, metadataFilter);
        PipesResult pipesResult = pipesClient.process(new FetchEmitTuple(testPdfFile,
                        new FetchKey(fetcherName, testPdfFile), new EmitKey(), new Metadata(),
                        parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        Assertions.assertNotNull(pipesResult.getEmitData().getMetadataList());
        assertEquals(1, pipesResult.getEmitData().getMetadataList().size());
        Metadata metadata = pipesResult.getEmitData().getMetadataList().get(0);
        assertEquals("TESTOVERLAPPINGTEXT.PDF", metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
    }

    @Test
    public void testMetadataListFilter() throws IOException, InterruptedException {
        ParseContext parseContext = new ParseContext();
        MetadataListFilter metadataFilter = new CompositeMetadataListFilter(
                        List.of(new AttachmentCountingListFilter()));
        parseContext.set(MetadataListFilter.class, metadataFilter);
        PipesResult pipesResult = pipesClient.process(new FetchEmitTuple("mock/embedded.xml",
                        new FetchKey(fetcherName, "mock/embedded.xml"), new EmitKey(),
                        new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        Assertions.assertNotNull(pipesResult.getEmitData().getMetadataList());
        assertEquals(5, pipesResult.getEmitData().getMetadataList().size());
        Metadata metadata = pipesResult.getEmitData().getMetadataList().get(0);
        assertEquals(4, Integer.parseInt(metadata.get("X-TIKA:attachment_count")));
    }

    @Test
    public void testTimeout() throws IOException, InterruptedException {
        // TODO -- add unit test for timeout > default
        // TODO -- figure out how to test pipes server timeout alone
        // I did both manually during development, but unit tests are better. :D
        ParseContext parseContext = new ParseContext();
        parseContext.set(TikaTaskTimeout.class, new TikaTaskTimeout(1000));
        MetadataListFilter metadataFilter = new CompositeMetadataListFilter(
                        List.of(new AttachmentCountingListFilter()));
        parseContext.set(MetadataListFilter.class, metadataFilter);
        PipesResult pipesResult = pipesClient.process(new FetchEmitTuple("mock/timeout-10s.xml",
                        new FetchKey(fetcherName, "mock/timeout-10s.xml"), new EmitKey(),
                        new Metadata(), parseContext, FetchEmitTuple.ON_PARSE_EXCEPTION.SKIP));
        assertEquals(PipesResult.TIMEOUT.getStatus(), pipesResult.getStatus());
    }
}
