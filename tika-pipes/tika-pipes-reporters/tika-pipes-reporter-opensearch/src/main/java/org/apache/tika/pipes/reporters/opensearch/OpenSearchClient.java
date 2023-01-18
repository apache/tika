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
package org.apache.tika.pipes.reporters.opensearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.client.TikaClientException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.StringUtils;

public class OpenSearchClient {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchClient.class);

    //this includes the full url and the index, should not end in /
    //e.g. https://localhost:9200/my-index
    protected final String openSearchUrl;
    protected final HttpClient httpClient;

    protected OpenSearchClient(String openSearchUrl, HttpClient httpClient) {
        this.openSearchUrl = openSearchUrl;
        this.httpClient = httpClient;
    }

    public void emitDocument(String emitKey, String routing, Metadata metadata)
            throws IOException, TikaClientException {

        StringWriter writer = new StringWriter();
        //we're choosing bulk request to avoid
        //having to url encode document id
        //and frankly this was copy/paste/edit from the emitter

        writeBulkRequest(emitKey, routing, writer);
        writer.append("\n");
        writeDoc(metadata, writer);
        writer.append("\n");
        emitJson(writer.toString());
    }

    private void emitJson(String json) throws IOException, TikaClientException {
        String requestUrl = openSearchUrl + "/_bulk";
        JsonResponse response = postJson(requestUrl, json);
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

    public JsonResponse postJson(String url, String json) throws IOException {
        HttpPost httpRequest = new HttpPost(url);
        StringEntity entity = new StringEntity(json, StandardCharsets.UTF_8);
        httpRequest.setEntity(entity);
        httpRequest.setHeader("Accept", "application/json");
        httpRequest.setHeader("Content-type", "application/json; charset=utf-8");

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
                ((CloseableHttpResponse) response).close();
            }
            httpRequest.releaseConnection();
        }
    }

    public void writeDoc(Metadata metadata, StringWriter writer) throws IOException {

        try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeObjectFieldStart("doc");
            writeMetadata(metadata, jsonGenerator);
            jsonGenerator.writeEndObject();
            jsonGenerator.writeBooleanField("doc_as_upsert", true);
            jsonGenerator.writeEndObject();
        }
    }


    public void writeBulkRequest(String id, String routing, StringWriter writer) throws IOException {

        try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
            jsonGenerator.writeStartObject();
            jsonGenerator.writeObjectFieldStart("update");
            jsonGenerator.writeStringField("_id", id);
            if (!StringUtils.isEmpty(routing)) {
                jsonGenerator.writeStringField("routing", routing);
            }
            jsonGenerator.writeNumberField("retry_on_conflict", 3);
            jsonGenerator.writeEndObject();
            jsonGenerator.writeEndObject();
        }
    }


    private static void writeMetadata(Metadata metadata, JsonGenerator jsonGenerator)
            throws IOException {
        //writes the metadata without the start { or the end }
        //to allow for other fields to be added
        for (String n : metadata.names()) {
            String[] vals = metadata.getValues(n);
            if (vals.length == 1) {
                jsonGenerator.writeStringField(n, vals[0]);
            } else {
                jsonGenerator.writeArrayFieldStart(n);
                for (String v : vals) {
                    jsonGenerator.writeString(v);
                }
                jsonGenerator.writeEndArray();
            }
        }
    }
}
