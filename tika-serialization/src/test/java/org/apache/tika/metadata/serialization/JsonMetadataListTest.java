package org.apache.tika.metadata.serialization;

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

import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedList;
import java.util.List;

import org.apache.tika.metadata.Metadata;
import org.junit.Test;

import static junit.framework.Assert.assertTrue;
import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.assertEquals;

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

        List<Metadata> metadataList = new LinkedList<Metadata>();
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
        String json = "[{\"k1\":[\"v1\",\"v2\",\"v3\",\"v4\",\"v4\"],\"k2\":\"v1\"}," +
                "\"k3\":[\"v1\",\"v2\",\"v3\",\"v4\",\"v4\"],\"k4\":\"v1\"}]";
        List<Metadata> m = JsonMetadataList.fromJson(null);
        assertNull(m);
    }

    @Test
    public void testPrettyPrint() throws Exception {
        Metadata m1 = new Metadata();
        m1.add("tika:content", "this is the content");
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

        List<Metadata> metadataList = new LinkedList<Metadata>();
        metadataList.add(m1);
        metadataList.add(m2);
        StringWriter writer = new StringWriter();
        JsonMetadataList.toJson(metadataList, writer);
        assertTrue(writer.toString().startsWith("[{\"tika:content\":\"this is the content\",\"zk1\":[\"v1\",\"v2\","));
        writer = new StringWriter();
        JsonMetadataList.setPrettyPrinting(true);
        JsonMetadataList.toJson(metadataList, writer);
        assertTrue(writer.toString().startsWith("[\n" +
                "  {\n" +
                "    \"zk1\": [\n" +
                "      \"v1\",\n" +
                "      \"v2\","));
        assertTrue(writer.toString().contains("    \"zk2\": \"v1\",\n" +
                "    \"tika:content\": \"this is the content\"\n" +
                "  },"));

        //now set it back to false
        JsonMetadataList.setPrettyPrinting(false);
        writer = new StringWriter();
        JsonMetadataList.toJson(metadataList, writer);
        assertTrue(writer.toString().startsWith("[{\"tika:content\":\"this is the content\",\"zk1\":[\"v1\",\"v2\","));
    }
}
