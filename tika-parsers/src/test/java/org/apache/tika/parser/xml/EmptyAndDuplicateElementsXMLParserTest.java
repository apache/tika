/**
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
package org.apache.tika.parser.xml;

import static org.junit.Assert.assertEquals;

import java.io.InputStream;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.TeeContentHandler;
import org.junit.Test;
import org.xml.sax.ContentHandler;

public class EmptyAndDuplicateElementsXMLParserTest extends TikaTest {
    
    private Property FIRST_NAME = Property.internalTextBag(
            "custom" + Metadata.NAMESPACE_PREFIX_DELIMITER + "FirstName");
    private Property LAST_NAME = Property.internalTextBag(
            "custom" + Metadata.NAMESPACE_PREFIX_DELIMITER + "LastName");

    @Test
    public void testDefaultBehavior() throws Exception {
        InputStream input = EmptyAndDuplicateElementsXMLParserTest.class.getResourceAsStream(
                "/test-documents/testXML3.xml");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new DefaultCustomXMLTestParser().parse(input, handler, metadata, new ParseContext());
            
            assertEquals(4, metadata.getValues(FIRST_NAME).length);
            assertEquals(2, metadata.getValues(LAST_NAME).length);
            
            assertEquals("John", metadata.getValues(FIRST_NAME)[0]);
            assertEquals("Smith", metadata.getValues(LAST_NAME)[0]);
            
            assertEquals("Jane", metadata.getValues(FIRST_NAME)[1]);
            assertEquals("Doe", metadata.getValues(LAST_NAME)[1]);
            
            // We didn't know Bob's last name, but now we don't know an entry existed
            assertEquals("Bob", metadata.getValues(FIRST_NAME)[2]);
            
            // We don't know Kate's last name because it was a duplicate
            assertEquals("Kate", metadata.getValues(FIRST_NAME)[3]);
        } finally {
            input.close();
        }
    }
    
    @Test
    public void testEmptiesAndRepeats() throws Exception {
        InputStream input = EmptyAndDuplicateElementsXMLParserTest.class.getResourceAsStream(
                "/test-documents/testXML3.xml");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new AllowEmptiesAndDuplicatesCustomXMLTestParser().parse(input, handler, metadata, new ParseContext());
            
            assertEquals(4, metadata.getValues(FIRST_NAME).length);
            assertEquals(4, metadata.getValues(LAST_NAME).length);
            
            assertEquals("John", metadata.getValues(FIRST_NAME)[0]);
            assertEquals("Smith", metadata.getValues(LAST_NAME)[0]);
            
            assertEquals("Jane", metadata.getValues(FIRST_NAME)[1]);
            assertEquals("Doe", metadata.getValues(LAST_NAME)[1]);
            
            assertEquals("Bob", metadata.getValues(FIRST_NAME)[2]);
            assertEquals("", metadata.getValues(LAST_NAME)[2]);
            
            assertEquals("Kate", metadata.getValues(FIRST_NAME)[3]);
            assertEquals("Smith", metadata.getValues(LAST_NAME)[3]);
        } finally {
            input.close();
        }
    }
    
    private class DefaultCustomXMLTestParser extends XMLParser {
    
        private static final long serialVersionUID = 2458579047014545931L;

        protected ElementMetadataHandler getCustomElementHandler(Metadata metadata, Property tikaProperty, String localPart) {
            return new ElementMetadataHandler(
                    "http://custom",
                    localPart,
                    metadata,
                    tikaProperty);
        }
        
        protected ContentHandler getContentHandler(
                ContentHandler handler, Metadata metadata, ParseContext context) {
            return new TeeContentHandler(
                    super.getContentHandler(handler, metadata, context),
                    getCustomElementHandler(metadata, FIRST_NAME, "FirstName"),
                    getCustomElementHandler(metadata, LAST_NAME, "LastName"));
        }
    }
    
    private class AllowEmptiesAndDuplicatesCustomXMLTestParser extends DefaultCustomXMLTestParser {
        
        private static final long serialVersionUID = 3735646809954466229L;

        protected ElementMetadataHandler getCustomElementHandler(Metadata metadata, Property tikaProperty, String localPart) {
            return new ElementMetadataHandler(
                    "http://custom",
                    localPart,
                    metadata,
                    tikaProperty,
                    true,
                    true);
        }
    }
    
    
}
