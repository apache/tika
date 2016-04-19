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

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class JsonMetadataTest {

    @Test
    public void testBasicSerializationAndDeserialization() throws Exception {
        Metadata metadata = new Metadata();
        metadata.add("k1", "v1");
        metadata.add("k1", "v2");
        //test duplicate value
        metadata.add("k3", "v3");
        metadata.add("k3", "v3");
        //test numeral with comma
        metadata.add("k4", "500,000");
        //test Chinese
        metadata.add("alma_mater", "\u666E\u6797\u65AF\u987F\u5927\u5B66");
        //test url
        metadata.add("url", "/myApp/myAction.html?method=router&cmd=1");
        //simple html entities
        metadata.add("html", "<html><body>&amp;&nbsp;</body></html>");
        //simple json escape chars
        metadata.add("json_escapes", "the: \"quick\" brown, fox");
        
        StringWriter writer = new StringWriter();
        JsonMetadata.toJson(metadata, writer);
        Metadata deserialized = JsonMetadata.fromJson(new StringReader(writer.toString()));
        assertEquals(7, deserialized.names().length);
        assertEquals(metadata, deserialized);

        //test that this really is 6 Chinese characters
        assertEquals(6, deserialized.get("alma_mater").length());

        //now test pretty print;
        writer = new StringWriter();
        JsonMetadata.setPrettyPrinting(true);
        JsonMetadata.toJson(metadata, writer);
        assertTrue(writer.toString().contains(
                "  \"json_escapes\": \"the: \\\"quick\\\" brown, fox\",\n" +
                "  \"k1\": [\n" +
                "    \"v1\",\n" +
                "    \"v2\"\n" +
                "  ],\n" +
                "  \"k3\": [\n" +
                "    \"v3\",\n" +
                "    \"v3\"\n" +
                "  ],\n" +
                "  \"k4\": \"500,000\",\n" +
                "  \"url\": \"/myApp/myAction.html?method\\u003drouter\\u0026cmd\\u003d1\"\n" +
                "}"));
    }
    
    @Test
    public void testDeserializationException() {
        //malformed json; 500,000 should be in quotes
        String json = "{\"k1\":[\"v1\",\"v2\"],\"k3\":\"v3\",\"k4\":500,000}";
        boolean ex = false;
        try {
            Metadata deserialized = JsonMetadata.fromJson(new StringReader(json));
        } catch (TikaException e) {
            ex = true;
        }
        assertTrue(ex);
    }
    
    @Test
    public void testNull() {
        StringWriter writer = new StringWriter();
        boolean ex = false;
        try {
            JsonMetadata.toJson(null, writer);
        } catch (TikaException e) {
            ex = true;
        }
        assertFalse(ex);
        assertEquals("null", writer.toString());        
    }

    @Test
    public void testLargeNumberOfKeys() throws Exception {
        Metadata m = new Metadata();
        for (int i = 0; i < 100000; i++) {
            m.set(Integer.toString(i), "val_"+i);
        }
        StringWriter writer = new StringWriter();
        JsonMetadata.toJson(m, writer);
        Metadata deserialized = JsonMetadata.fromJson(new StringReader(writer.toString()));
        assertEquals(m, deserialized);        
    }
    
    @Test
    public void testLargeValues() throws Exception {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 1000000; i++){
            sb.append("v");
        }
        Metadata m = new Metadata();
        m.add("large_value1", sb.toString());
        m.add("large_value2", sb.toString());
        StringWriter writer = new StringWriter();
        JsonMetadata.toJson(m, writer);
        Metadata deserialized = JsonMetadata.fromJson(new StringReader(writer.toString()));
        assertEquals(m, deserialized);        
    }
}
