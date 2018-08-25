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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;


public class CustomReaderTest {
  
  static class CustomMimeTypesReader extends MimeTypesReader {
    public Map<String, String> values = new HashMap<String, String>();
    public List<String> ignorePatterns = new ArrayList<String>();

    CustomMimeTypesReader(MimeTypes types) {
      super(types); 
    }
    

    @Override
    public void startElement(
            String uri, String localName, String qName,
            Attributes attributes) throws SAXException {
      super.startElement(uri, localName, qName, attributes);
      if ("hello".equals(qName)) {
          characters = new StringBuilder();
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
      super.endElement(uri, localName, qName);
        if (type != null) {
          if("hello".equals(qName)) {
            values.put(type.toString(), characters.toString().trim());
            characters = null;
          }
        }
    }

    @Override
    protected void handleGlobError(MimeType type, String pattern, MimeTypeException ex, 
        String qName, Attributes attributes) throws SAXException {
      ignorePatterns.add( type.toString() + ">>" + pattern);
    }
  }
  
  @Test
  public void testCustomReader() throws Exception {
    MimeTypes mimeTypes = new MimeTypes();
    CustomMimeTypesReader reader = new CustomMimeTypesReader(mimeTypes);
    reader.read(getClass().getResourceAsStream("custom-mimetypes.xml"));
    
    String key = "hello/world-file";

    MimeType hello = mimeTypes.forName(key);
    assertEquals("A \"Hello World\" file", hello.getDescription());    
    assertEquals("world", reader.values.get(key));
    assertEquals(0, reader.ignorePatterns.size());
    
    // Now add another resource with conflicting regex
    reader.read(getClass().getResourceAsStream("custom-mimetypes2.xml"));
    
    key = "another/world-file";
    MimeType another = mimeTypes.forName(key);
    assertEquals("kittens", reader.values.get(key));
    assertEquals(1, reader.ignorePatterns.size());
    assertEquals(another.toString()+">>*"+hello.getExtension(), 
        reader.ignorePatterns.get(0));
    assertTrue("Server-side script type not detected", another.isInterpreted());
    
    //System.out.println( mimeTypes.getMediaTypeRegistry().getTypes() );
  }
}
