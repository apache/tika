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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonMappingException;

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
    }

    @Test
    public void testListNull() throws Exception {
        StringWriter writer = new StringWriter();
        JsonMetadataList.toJson(null, writer);
        assertEquals("null", writer.toString().trim());

        List<Metadata> m = JsonMetadataList.fromJson(null);
        assertNull(m);
    }

    @Test
    public void testListCorrupted() throws Exception {
        String json = "[{\"k1\":[\"v1\",\"v2\",\"v3\",\"v4\",\"v4\"],\"k2\":\"v1\"},"
                + "\"k3\":[\"v1\",\"v2\",\"v3\",\"v4\",\"v4\"],\"k4\":\"v1\"}]";
        Exception ex = assertThrows(JsonMappingException.class,
                () -> JsonMetadataList.fromJson(new StringReader(json)));
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
        assertTrue(writer.toString().startsWith("["));
        writer = new StringWriter();
        JsonMetadata.setPrettyPrinting(true);

        JsonMetadataList.setPrettyPrinting(true);
        JsonMetadataList.toJson(metadataList, writer);
        String expected = "[ {[NEWLINE]  \"zk1\" : [ \"v1\", \"v2\", \"v3\", \"v4\", \"v4\" ],[NEWLINE]  \"zk2\" : \"v1\",[NEWLINE]"
                + "  \"X-TIKA:content\" : \"this is the content\"[NEWLINE]}, "
                + "{[NEWLINE]  \"k3\" : [ \"v1\", \"v2\", \"v3\", \"v4\", \"v4\" ],[NEWLINE]  \"k4\" : \"v1\"[NEWLINE]} ]";
        assertEquals(expected, writer.toString().replaceAll("[\r\n]+", "[NEWLINE]"));

        //now set it back to false
        JsonMetadataList.setPrettyPrinting(false);
        writer = new StringWriter();
        JsonMetadataList.toJson(metadataList, writer);
        assertTrue(writer.toString().startsWith("["));
    }

    @Test
    public void testLargeValues() throws Exception {
        //TIKA-4154
        TikaConfig tikaConfig = null;
        try (InputStream is = JsonMetadata.class.getResourceAsStream("/config/tika-config-json.xml")) {
            tikaConfig = new TikaConfig(is);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30000000; i++) {
            sb.append("v");
        }
        Metadata m = new Metadata();
        m.add("large_value", sb.toString());
        List<Metadata> list = new ArrayList<>();
        list.add(m);
        list.add(m);
        StringWriter writer = new StringWriter();
        JsonMetadataList.toJson(list, writer);
        List<Metadata> deserialized = JsonMetadataList.fromJson(new StringReader(writer.toString()));
        assertEquals(list, deserialized);
    }
}
