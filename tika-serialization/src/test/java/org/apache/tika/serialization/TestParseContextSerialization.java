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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringWriter;
import java.io.Writer;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.filter.CompositeMetadataFilter;
import org.apache.tika.metadata.filter.DateNormalizingMetadataFilter;
import org.apache.tika.metadata.filter.MetadataFilter;
import org.apache.tika.parser.ParseContext;

public class TestParseContextSerialization {

    @Test
    public void testBasic() throws Exception {
        MetadataFilter metadataFilter = new CompositeMetadataFilter(List.of(new DateNormalizingMetadataFilter()));
        ParseContext pc = new ParseContext();
        pc.set(MetadataFilter.class, metadataFilter);

        String json;
        try (Writer writer = new StringWriter()) {
            try (JsonGenerator jsonGenerator = new JsonFactory().createGenerator(writer)) {
                jsonGenerator.writeStartObject();
                ParseContextSerializer serializer = new ParseContextSerializer();
                serializer.serialize(pc, jsonGenerator, null);
                jsonGenerator.writeEndObject();
            }
            json = writer.toString();
        }
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addDeserializer(ParseContext.class, new ParseContextDeserializer());
        mapper.registerModule(module);

        ParseContext deserialized = mapper.readValue(json, ParseContext.class);
        MetadataFilter dMetadataFilter = deserialized.get(MetadataFilter.class);
        assertTrue(dMetadataFilter instanceof CompositeMetadataFilter);
        List<MetadataFilter> metadataFilters = ((CompositeMetadataFilter)dMetadataFilter).getFilters();
        assertEquals(1, metadataFilters.size());
        assertTrue(metadataFilters.get(0) instanceof DateNormalizingMetadataFilter);
    }
}
