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
package org.apache.tika.batch;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import org.junit.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.batch.fs.RecursiveParserWrapperFSConsumer;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.NoOpFilter;
import org.apache.tika.metadata.serialization.JsonMetadataList;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.RecursiveParserWrapper;
import org.apache.tika.sax.BasicContentHandlerFactory;

public class RecursiveParserWrapperFSConsumerTest extends TikaTest {


    @Test
    public void testEmbeddedWithNPE() throws Exception {
        final String path = "/test-documents/embedded_with_npe.xml";
        final Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "embedded_with_npe.xml");

        ArrayBlockingQueue<FileResource> queue = new ArrayBlockingQueue<>(2);
        queue.add(new FileResource() {

            @Override
            public String getResourceId() {
                return "testFile";
            }

            @Override
            public Metadata getMetadata() {
                return metadata;
            }

            @Override
            public InputStream openInputStream() throws IOException {
                return getResourceAsStream(path);
            }
        });
        queue.add(new PoisonFileResource());

        MockOSFactory mockOSFactory = new MockOSFactory();
        Parser p = new RecursiveParserWrapper(
                new AutoDetectParserFactory().getParser(new TikaConfig()));
        RecursiveParserWrapperFSConsumer consumer = new RecursiveParserWrapperFSConsumer(queue, p,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                mockOSFactory, NoOpFilter.NOOP_FILTER);

        IFileProcessorFutureResult result = consumer.call();
        mockOSFactory.getStreams().get(0).flush();
        byte[] bytes = mockOSFactory.getStreams().get(0).toByteArray();
        List<Metadata> results = JsonMetadataList
                .fromJson(new InputStreamReader(new ByteArrayInputStream(bytes), UTF_8));

        assertEquals(4, results.size());
        assertContains("another null pointer",
                results.get(2).get(TikaCoreProperties.EMBEDDED_EXCEPTION));

        assertEquals("Nikolai Lobachevsky", results.get(0).get("author"));
        for (int i = 1; i < 4; i++) {
            assertEquals("embeddedAuthor" + i, results.get(i).get("author"));
            assertContains("some_embedded_content" + i,
                    results.get(i).get(TikaCoreProperties.TIKA_CONTENT));
        }
    }

    @Test
    public void testEmbeddedThenNPE() throws Exception {
        final String path = "/test-documents/embedded_then_npe.xml";
        final Metadata metadata = new Metadata();
        metadata.add(TikaCoreProperties.RESOURCE_NAME_KEY, "embedded_then_npe.xml");

        ArrayBlockingQueue<FileResource> queue = new ArrayBlockingQueue<>(2);
        queue.add(new FileResource() {

            @Override
            public String getResourceId() {
                return "testFile";
            }

            @Override
            public Metadata getMetadata() {
                return metadata;
            }

            @Override
            public InputStream openInputStream() throws IOException {
                return getResourceAsStream(path);
            }
        });
        queue.add(new PoisonFileResource());

        MockOSFactory mockOSFactory = new MockOSFactory();
        Parser p = new RecursiveParserWrapper(
                new AutoDetectParserFactory().getParser(new TikaConfig()));
        RecursiveParserWrapperFSConsumer consumer = new RecursiveParserWrapperFSConsumer(queue, p,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                mockOSFactory, NoOpFilter.NOOP_FILTER);

        IFileProcessorFutureResult result = consumer.call();
        mockOSFactory.getStreams().get(0).flush();
        byte[] bytes = mockOSFactory.getStreams().get(0).toByteArray();
        List<Metadata> results = JsonMetadataList
                .fromJson(new InputStreamReader(new ByteArrayInputStream(bytes), UTF_8));
        assertEquals(2, results.size());
        assertContains("another null pointer",
                results.get(0).get(TikaCoreProperties.CONTAINER_EXCEPTION));
        assertEquals("Nikolai Lobachevsky", results.get(0).get("author"));
        assertEquals("embeddedAuthor", results.get(1).get("author"));
        assertContains("some_embedded_content",
                results.get(1).get(TikaCoreProperties.TIKA_CONTENT));
    }


    private class MockOSFactory implements OutputStreamFactory {
        List<ByteArrayOutputStream> streams = new ArrayList<>();

        @Override
        public OutputStream getOutputStream(Metadata metadata) throws IOException {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            streams.add(bos);
            return bos;
        }

        public List<ByteArrayOutputStream> getStreams() {
            return streams;
        }
    }
}
