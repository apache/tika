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
package org.apache.tika.mime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

public class CustomReaderTest {

    @Test
    public void testCustomReader() throws Exception {
        MimeTypes mimeTypes = new MimeTypes();
        CustomMimeTypesReader reader = new CustomMimeTypesReader(mimeTypes);
        try (InputStream is = getClass().getResourceAsStream("/custom-mimetypes.xml")) {
            reader.read(is);
        }
        String key = "hello/world-file";

        MimeType hello = mimeTypes.forName(key);
        assertEquals("A \"Hello World\" file", hello.getDescription());
        assertEquals("world", reader.values.get(key));
        assertEquals(0, reader.ignorePatterns.size());

        // Now add another resource with conflicting regex
        try (InputStream is = getClass().getResourceAsStream("/custom-mimetypes2.xml")) {
            reader.read(is);
        }
        key = "another/world-file";
        MimeType another = mimeTypes.forName(key);
        assertEquals("kittens", reader.values.get(key));
        assertEquals(1, reader.ignorePatterns.size());
        assertEquals(
                another.toString() + ">>*" + hello.getExtension(), reader.ignorePatterns.get(0));
        assertTrue(another.isInterpreted(), "Server-side script type not detected");
    }

    static class CustomMimeTypesReader extends MimeTypesReader {
        public Map<String, String> values = new HashMap<>();
        public List<String> ignorePatterns = new ArrayList<>();

        CustomMimeTypesReader(MimeTypes types) {
            super(types);
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes)
                throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            if ("hello".equals(qName)) {
                characters = new StringBuilder();
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            super.endElement(uri, localName, qName);
            if (type != null) {
                if ("hello".equals(qName)) {
                    values.put(type.toString(), characters.toString().trim());
                    characters = null;
                }
            }
        }

        @Override
        protected void handleGlobError(
                MimeType type,
                String pattern,
                MimeTypeException ex,
                String qName,
                Attributes attributes)
                throws SAXException {
            ignorePatterns.add(type.toString() + ">>" + pattern);
        }
    }
}
