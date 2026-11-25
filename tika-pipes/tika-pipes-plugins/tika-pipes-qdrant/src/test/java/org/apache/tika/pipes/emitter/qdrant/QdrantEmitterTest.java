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
package org.apache.tika.pipes.emitter.qdrant;

import static org.apache.tika.pipes.emitter.qdrant.QdrantClientTest.b64;
import static org.apache.tika.pipes.emitter.qdrant.QdrantClientTest.withChunks;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.Header;
import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import org.apache.tika.client.TikaClientException;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.pipes.api.emitter.EmitData;
import org.apache.tika.plugins.ExtensionConfig;

/** Checks the requests the emitter sends to the Qdrant points endpoints. */
public class QdrantEmitterTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record Request(String method, String path, String query, String auth, JsonNode body) {
    }

    private final List<Request> requests = new ArrayList<>();
    private final Deque<String> scrollPages = new ArrayDeque<>();
    private int status = 200;

    private QdrantClient client(String apiKey, Integer batchSize, Boolean replaceExisting)
            throws Exception {
        HttpClient http = Mockito.mock(HttpClient.class);
        Mockito.when(http.execute(ArgumentMatchers.any(HttpUriRequest.class))).thenAnswer(inv -> {
            HttpEntityEnclosingRequestBase request = inv.getArgument(0);
            Header auth = request.getFirstHeader("Authorization");
            String body = EntityUtils.toString(request.getEntity(), StandardCharsets.UTF_8);
            requests.add(new Request(request.getMethod(), request.getURI().getRawPath(),
                    request.getURI().getRawQuery(), auth == null ? null : auth.getValue(),
                    MAPPER.readTree(body)));
            // like Qdrant, reject a whole request that holds one unknown vector name
            int code = body.contains("\"bad\"") ? 400 : status;
            String ok = request.getURI().getPath().endsWith("/scroll")
                    ? (scrollPages.isEmpty() ? "{\"result\":{\"points\":[],\"next_page_offset\":null}}"
                            : scrollPages.poll())
                    : "{\"result\":{\"status\":\"completed\"},\"status\":\"ok\"}";
            BasicHttpResponse response = new BasicHttpResponse(HttpVersion.HTTP_1_1, code, null);
            response.setEntity(new StringEntity(code == 200 ? ok
                    : "{\"status\":{\"error\":\"Wrong input: Not existing vector name: p\"}}",
                    ContentType.APPLICATION_JSON));
            return response;
        });
        QdrantEmitterConfig config = new QdrantEmitterConfig("http://qdrant.test:6333/",
                "my coll", apiKey, null, batchSize, replaceExisting, null);
        return new QdrantClient(config, http);
    }

    private static Metadata threeChunks() {
        return withChunks("[{\"producer\":\"p\",\"vector\":\"" + b64(1f) + "\"},"
                + "{\"producer\":\"p\",\"vector\":\"" + b64(2f) + "\"},"
                + "{\"producer\":\"p\",\"vector\":\"" + b64(3f) + "\"}]");
    }

    private static Metadata badChunk() {
        return withChunks("[{\"producer\":\"bad\",\"vector\":\"" + b64(1f) + "\"}]");
    }

    private static String page(String next, Object... ids) throws Exception {
        return "{\"result\":{\"points\":" + MAPPER.writeValueAsString(
                Arrays.stream(ids).map(i -> Map.of("id", i)).toList())
                + ",\"next_page_offset\":" + MAPPER.writeValueAsString(next) + "}}";
    }

    @Test
    public void testBatchedUpsertsThenStaleDelete() throws Exception {
        String kept0 = QdrantClient.pointId("doc.pdf", 0, 0).toString();
        String kept1 = QdrantClient.pointId("doc.pdf", 0, 1).toString();
        String kept2 = QdrantClient.pointId("doc.pdf", 0, 2).toString();
        String stale = QdrantClient.pointId("doc.pdf", 0, 3).toString();
        scrollPages.add(page("next-page", kept0, stale));
        scrollPages.add(page(null, kept1, kept2, 7));
        client("secret", 2, null).emitDocument("doc.pdf", List.of(threeChunks()));

        assertEquals(5, requests.size());
        for (int i = 0; i < 2; i++) {
            Request upsert = requests.get(i);
            assertEquals("PUT", upsert.method());
            assertEquals("/collections/my%20coll/points", upsert.path());
            assertEquals("wait=true", upsert.query());
            assertEquals("Bearer secret", upsert.auth());
        }
        assertEquals(2, requests.get(0).body().get("points").size());
        assertEquals(1, requests.get(1).body().get("points").size());
        assertEquals(3.0, requests.get(1).body().get("points").get(0)
                .get("vector").get("p").get(0).asDouble());

        Request scroll = requests.get(2);
        assertEquals("/collections/my%20coll/points/scroll", scroll.path());
        assertEquals("Bearer secret", scroll.auth());
        assertEquals("doc.pdf", scroll.body().get("filter").get("must").get(0).get("match")
                .get("value").asText());
        assertFalse(scroll.body().has("offset"));
        assertEquals("next-page", requests.get(3).body().get("offset").asText());

        Request delete = requests.get(4);
        assertEquals("POST", delete.method());
        assertEquals("/collections/my%20coll/points/delete", delete.path());
        assertEquals("wait=true", delete.query());
        assertEquals("Bearer secret", delete.auth());
        assertEquals("[\"" + stale + "\",7]", delete.body().get("points").toString());
    }

    @Test
    public void testNoDeleteWhenNothingIsStale() throws Exception {
        scrollPages.add(page(null, QdrantClient.pointId("doc.pdf", 0, 0).toString(),
                QdrantClient.pointId("doc.pdf", 0, 1).toString(),
                QdrantClient.pointId("doc.pdf", 0, 2).toString()));
        client(null, null, null).emitDocument("doc.pdf", List.of(threeChunks()));
        assertEquals(List.of("PUT", "POST"), requests.stream().map(Request::method).toList());
    }

    @Test
    public void testRepeatedScrollOffsetFails() throws Exception {
        scrollPages.add(page("same"));
        scrollPages.add(page("same"));
        QdrantClient client = client(null, null, null);
        TikaClientException e = assertThrows(TikaClientException.class,
                () -> client.emitDocument("doc.pdf", List.of(threeChunks())));
        assertTrue(e.getMessage().contains("same offset"), e.getMessage());
    }

    @Test
    public void testNoDeleteWhenReplaceExistingIsOff() throws Exception {
        client("", null, false).emitDocument("doc.pdf", List.of(threeChunks()));
        assertEquals(1, requests.size());
        assertEquals("PUT", requests.get(0).method());
        assertNull(requests.get(0).auth(), "an empty apiKey sends no header");
    }

    @Test
    public void testEmitWithoutPointsLeavesEarlierPoints() throws Exception {
        QdrantClient client = client(null, null, null);
        client.emitDocument("doc.pdf", List.of());
        client.emitDocument("doc.pdf", List.of(new Metadata()));
        assertTrue(requests.isEmpty());
    }

    @Test
    public void testRejectedContainerLeavesTheOthersWritten() throws Exception {
        QdrantClient client = client(null, null, null);
        TikaClientException e = assertThrows(TikaClientException.class,
                () -> client.emitDocuments(List.of(emitData("a.pdf", threeChunks()),
                        emitData("c.pdf", badChunk()), emitData("b.txt", threeChunks()))));
        assertTrue(e.getMessage().contains("[c.pdf]"), e.getMessage());
        assertTrue(e.getMessage().contains("Not existing vector name"), e.getMessage());
        // a.pdf: upsert, scroll. c.pdf: rejected upsert, no scroll. b.txt: upsert, scroll.
        assertEquals(List.of("PUT", "POST", "PUT", "PUT", "POST"),
                requests.stream().map(Request::method).toList());
        assertEquals("b.txt", requests.get(4).body().get("filter").get("must").get(0)
                .get("match").get("value").asText());
    }

    @Test
    public void testBlankEmitKeyIsRejectedBeforeAnyRequest() throws Exception {
        QdrantClient client = client(null, null, null);
        assertThrows(TikaClientException.class,
                () -> client.emitDocument(null, List.of(threeChunks())));
        assertThrows(TikaClientException.class,
                () -> client.emitDocument(" ", List.of(threeChunks())));
        assertTrue(requests.isEmpty());
    }

    @Test
    public void testErrorStatusFailsTheEmit() throws Exception {
        status = 400;
        QdrantClient client = client(null, null, false);
        TikaClientException e = assertThrows(TikaClientException.class,
                () -> client.emitDocument("doc.pdf", List.of(threeChunks())));
        assertTrue(e.getMessage().contains("400"), e.getMessage());
        assertTrue(e.getMessage().contains("Not existing vector name"), e.getMessage());
    }

    @Test
    public void testFactoryBuildsFromJson() throws Exception {
        QdrantEmitterFactory factory = new QdrantEmitterFactory();
        assertTrue(factory.buildExtension(new ExtensionConfig("q", QdrantEmitterFactory.NAME,
                "{\"qdrantUrl\":\"http://localhost:6333\",\"collection\":\"c\"}"))
                instanceof QdrantEmitter);
        QdrantEmitter unreachable = (QdrantEmitter) factory.buildExtension(new ExtensionConfig("q",
                QdrantEmitterFactory.NAME, "{\"qdrantUrl\":\"http://127.0.0.1:9\",\"collection\":\"c\"}"));
        unreachable.emit(null);
        unreachable.emit(List.of());
        assertThrows(TikaConfigException.class, () -> factory.buildExtension(
                new ExtensionConfig("q", QdrantEmitterFactory.NAME, "{\"collection\":\"c\"}")));
    }

    private static EmitData emitData(String key, Metadata metadata) {
        return new EmitData() {
            @Override
            public String getEmitKey() {
                return key;
            }

            @Override
            public List<Metadata> getMetadataList() {
                return List.of(metadata);
            }

            @Override
            public String getContainerStackTrace() {
                return "";
            }

            @Override
            public long getEstimatedSizeBytes() {
                return 0;
            }

            @Override
            public ParseContext getParseContext() {
                return new ParseContext();
            }
        };
    }
}
