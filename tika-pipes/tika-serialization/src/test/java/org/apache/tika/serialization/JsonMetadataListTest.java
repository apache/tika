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
package org.apache.tika.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class JsonMetadataListTest {


    @Test
    public void testListBasic() throws Exception {
        Metadata m1 = new Metadata();
        m1.add("k1", "v1");
        m1.add("k1", "v2");
        m1.add("k1", "v3");
        m1.add("k1", "v4");
        m1.add("k1", "v4");
        m1.add("k2", "v1");

        Metadata m2 = new Metadata();
        m2.add("k3", "v1");
        m2.add("k3", "v2");
        m2.add("k3", "v3");
        m2.add("k3", "v4");
        m2.add("k3", "v4");
        m2.add("k4", "v1");

        List<Metadata> metadataList = new LinkedList<>();
        metadataList.add(m1);
        metadataList.add(m2);
        StringWriter writer = new StringWriter();
        JsonMetadataList.toJson(metadataList, writer);
        List<Metadata> deserialized = JsonMetadataList.fromJson(new StringReader(writer.toString()));
        assertEquals(metadataList, deserialized);

        //now test streaming serializer
        writer = new StringWriter();
        try (JsonStreamingSerializer streamingSerializer = new JsonStreamingSerializer(writer)) {
            streamingSerializer.add(m1);
            streamingSerializer.add(m2);
        }
        deserialized = JsonMetadataList.fromJson(new StringReader(writer.toString()));
        assertEquals(metadataList, deserialized);

    }

    @Test
    public void testListNull() throws Exception {
        StringWriter writer = new StringWriter();
        JsonMetadataList.toJson(null, writer);
        assertEquals("null", writer
                .toString()
                .trim());

        List<Metadata> m = JsonMetadataList.fromJson(null);
        assertNull(m);
    }

    @Test
    public void testListCorrupted() throws Exception {
        String json = "[{\"k1\":[\"v1\",\"v2\",\"v3\",\"v4\",\"v4\"],\"k2\":\"v1\"}," + "\"k3\":[\"v1\",\"v2\",\"v3\",\"v4\",\"v4\"],\"k4\":\"v1\"}]";
        List<Metadata> m = JsonMetadataList.fromJson(null);
        assertNull(m);
    }

    @Test
    public void testPrettyPrint() throws Exception {
        Metadata m1 = new Metadata();
        m1.add(TikaCoreProperties.TIKA_CONTENT, "this is the content");
        m1.add("zk1", "v1");
        m1.add("zk1", "v2");
        m1.add("zk1", "v3");
        m1.add("zk1", "v4");
        m1.add("zk1", "v4");
        m1.add("zk2", "v1");

        Metadata m2 = new Metadata();
        m2.add("k3", "v1");
        m2.add("k3", "v2");
        m2.add("k3", "v3");
        m2.add("k3", "v4");
        m2.add("k3", "v4");
        m2.add("k4", "v1");

        List<Metadata> metadataList = new LinkedList<>();
        metadataList.add(m1);
        metadataList.add(m2);
        StringWriter writer = new StringWriter();
        JsonMetadataList.toJson(metadataList, writer);
        assertTrue(writer
                .toString()
                .startsWith("["));
        writer = new StringWriter();
        JsonMetadataList.setPrettyPrinting(true);
        JsonMetadataList.toJson(metadataList, writer);
        assertTrue(writer
                .toString()
                .replaceAll("\r\n", "\n")
                .startsWith("[ {\n" + "  \"zk1\" : [ \"v1\", \"v2\", \"v3\", \"v4\", \"v4\" ],\n" + "  \"zk2\" : \"v1\",\n" + "  \"X-TIKA:content\" : \"this is the content\"\n" +
                        "},"));


        //now set it back to false
        JsonMetadataList.setPrettyPrinting(false);
        writer = new StringWriter();
        JsonMetadataList.toJson(metadataList, writer);
        assertTrue(writer
                .toString()
                .startsWith("["));
    }

    @Test
    public void testSwitchingOrderOfMainDoc() throws Exception {
        Metadata m1 = new Metadata();
        m1.add("k1", "v1");
        m1.add("k1", "v2");
        m1.add("k1", "v3");
        m1.add("k1", "v4");
        m1.add("k1", "v4");
        m1.add("k2", "v1");
        m1.add(TikaCoreProperties.EMBEDDED_RESOURCE_PATH, "/embedded-1");

        Metadata m2 = new Metadata();
        m2.add("k3", "v1");
        m2.add("k3", "v2");
        m2.add("k3", "v3");
        m2.add("k3", "v4");
        m2.add("k3", "v4");
        m2.add("k4", "v1");

        List<Metadata> truth = new ArrayList<>();
        truth.add(m2);
        truth.add(m1);
        StringWriter stringWriter = new StringWriter();
        try (JsonStreamingSerializer serializer = new JsonStreamingSerializer(stringWriter)) {
            serializer.add(m1);
            serializer.add(m2);
        }
        Reader reader = new StringReader(stringWriter.toString());
        List<Metadata> deserialized = JsonMetadataList.fromJson(reader);
        assertEquals(truth, deserialized);

    }
}
