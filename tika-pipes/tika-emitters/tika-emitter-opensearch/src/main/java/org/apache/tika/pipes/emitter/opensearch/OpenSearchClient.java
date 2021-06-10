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
package org.apache.tika.pipes.emitter.opensearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.TikaClientException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.StringUtils;

public class OpenSearchClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchEmitter.class);

    //this includes the full url and the index, should not end in /
    //e.g. https://localhost:9200/my-index
    private final String openSearchUrl;
    private final HttpClient httpClient;

    private OpenSearchClient(String openSearchUrl, HttpClient httpClient) {
        this.openSearchUrl = openSearchUrl;
        this.httpClient = httpClient;
    }

    public void addDocument(String emitKey, List<Metadata> metadataList) throws IOException,
            TikaClientException {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (Metadata metadata : metadataList) {
            String id = emitKey;
            if (i > 0) {
                id += "-" + i;
            }
            String indexJson = getBulkIndexJson(id, emitKey);
            sb.append(indexJson).append("\n");
            if (i == 0) {
                sb.append(metadataToJsonContainer(metadata));
            } else {
                sb.append(metadataToJsonEmbedded(metadata, emitKey));
            }
            sb.append("\n");
            i++;
        }
        //System.out.println(sb.toString());
        String requestUrl = openSearchUrl + "/bulk?routing=" + URLEncoder
                .encode(emitKey, StandardCharsets.UTF_8.name());
        JsonResponse response = postJson(requestUrl, sb.toString());
        if (response.getStatus() != 200) {
            throw new TikaClientException(response.getMsg());
        } else {
            //if there's a single error, throw the full json.
            //this has not been thoroughly tested with versions of es < 7
            JsonNode errorNode = response.getJson().get("errors");
            if (errorNode.asText().equals("true")) {
                throw new TikaClientException(response.getJson().toString());
            }
        }
    }

    private String metadataToJsonEmbedded(Metadata metadata, String emitKey) throws IOException {
        StringWriter writer = new StringWriter();
        try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
            jsonGenerator.writeStartObject();

            writeMetadata(metadata, jsonGenerator);
            jsonGenerator.writeStartObject("relation_type");
            jsonGenerator.writeStringField("name", "embedded");
            jsonGenerator.writeStringField("parent", emitKey);
            //end the relation type object
            jsonGenerator.writeEndObject();
            //end the metadata object
            jsonGenerator.writeEndObject();
        }
        return writer.toString();
    }

    private String metadataToJsonContainer(Metadata metadata) throws IOException {
        StringWriter writer = new StringWriter();
        try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
            jsonGenerator.writeStartObject();
            writeMetadata(metadata, jsonGenerator);
            jsonGenerator.writeStringField("relation_type", "container");
            jsonGenerator.writeEndObject();
        }
        return writer.toString();
    }

    private void writeMetadata(Metadata metadata, JsonGenerator jsonGenerator) throws IOException {
        //writes the metadata without the start { or the end }
        //to allow for other fields to be added
        for (String n : metadata.names()) {
            String[] vals = metadata.getValues(n);
            if (vals.length == 1) {
                jsonGenerator.writeStringField(n, vals[0]);
            } else {
                jsonGenerator.writeStartArray(n);
                for (String v : vals) {
                    jsonGenerator.writeString(v);
                }
                jsonGenerator.writeEndArray();
            }
        }
    }

    private String getBulkIndexJson(String id, String routing) throws IOException {
        StringWriter writer = new StringWriter();
        try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeStartObject("index");
            jsonGenerator.writeStringField("_id", id);
            if (!StringUtils.isEmpty(routing)) {
                jsonGenerator.writeStringField("routing", routing);
            }

            jsonGenerator.writeEndObject();
            jsonGenerator.writeEndObject();
        }
        return writer.toString();
    }

    protected JsonResponse postJson(String url, String json) throws IOException {
        HttpPost httpRequest = new HttpPost(url);
        ByteArrayEntity entity = new ByteArrayEntity(json.getBytes(StandardCharsets.UTF_8));
        httpRequest.setEntity(entity);
        httpRequest.setHeader("Accept", "application/json");
        httpRequest.setHeader("Content-type", "application/json; charset=utf-8");
        //At one point, this was required because of connection already
        // bound exceptions on windows :(
        //httpPost.setHeader("Connection", "close");

        //try (CloseableHttpClient httpClient = HttpClients.createDefault()) {

        HttpResponse response = null;
        try {
            response = httpClient.execute(httpRequest);
            int status = response.getStatusLine().getStatusCode();
            if (status == 200) {
                try (Reader reader = new BufferedReader(
                        new InputStreamReader(response.getEntity().getContent(),
                                StandardCharsets.UTF_8))) {
                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode node = mapper.readTree(reader);
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("node:", node);
                    }
                    return new JsonResponse(200, node);
                }
            } else {
                return new JsonResponse(status,
                        new String(EntityUtils.toByteArray(response.getEntity()),
                                StandardCharsets.UTF_8));
            }
        } finally {
            if (response != null && response instanceof CloseableHttpResponse) {
                ((CloseableHttpResponse)response).close();
            }
            httpRequest.releaseConnection();
        }
    }
}
