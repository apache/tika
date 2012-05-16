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
package org.apache.tika.parser.executable;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;

public class ExecutableParserTest extends TestCase {

    public void testWin32Parser() throws Exception {
        InputStream input = ExecutableParserTest.class.getResourceAsStream(
                "/test-documents/testWindows-x86-32.exe");
        try {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler();
            new ExecutableParser().parse(input, handler, metadata, new ParseContext());

            assertEquals("application/x-msdownload",
                    metadata.get(Metadata.CONTENT_TYPE));
            assertEquals("2012-05-13T13:40:11Z",
                    metadata.get(Metadata.CREATION_DATE));
            
            assertEquals(ExecutableParser.MACHINE_x86_32, 
                    metadata.get(ExecutableParser.MACHINE_TYPE));
            assertEquals("Little", 
                  metadata.get(ExecutableParser.ENDIAN));
            assertEquals("32", 
                  metadata.get(ExecutableParser.ARCHITECTURE_BITS));
            assertEquals("Windows", 
                  metadata.get(ExecutableParser.PLATFORM));

            String content = handler.toString();
            assertEquals("", content); // No text yet
        } finally {
            input.close();
        }
    }
    
    public void testElfParser_x86_32() throws Exception {
       InputStream input = ExecutableParserTest.class.getResourceAsStream(
             "/test-documents/testLinux-x86-32");
     try {
         Metadata metadata = new Metadata();
         ContentHandler handler = new BodyContentHandler();
         new ExecutableParser().parse(input, handler, metadata, new ParseContext());

         assertEquals("application/x-executable",
                 metadata.get(Metadata.CONTENT_TYPE));
         
         assertEquals(ExecutableParser.MACHINE_x86_32, 
                 metadata.get(ExecutableParser.MACHINE_TYPE));
         assertEquals("Little", 
               metadata.get(ExecutableParser.ENDIAN));
         assertEquals("32", 
               metadata.get(ExecutableParser.ARCHITECTURE_BITS));
//         assertEquals("Linux", 
//               metadata.get(ExecutableParser.PLATFORM));

         String content = handler.toString();
         assertEquals("", content); // No text yet
     } finally {
         input.close();
     }       
    }

}
