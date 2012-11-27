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
package org.apache.tika;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.net.URL;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import junit.framework.TestCase;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;

/**
 * Parent class of Tika tests
 */
public abstract class TikaTest extends TestCase {
   /**
    * This method will give you back the filename incl. the absolute path name
    * to the resource. If the resource does not exist it will give you back the
    * resource name incl. the path.
    * 
    * @param name
    *            The named resource to search for.
    * @return an absolute path incl. the name which is in the same directory as
    *         the the class you've called it from.
    */
   public File getResourceAsFile(String name) throws URISyntaxException {
       URL url = this.getClass().getResource(name);
       if (url != null) {
           return new File(url.toURI());
       } else {
           // We have a file which does not exists
           // We got the path
           url = this.getClass().getResource(".");
           File file = new File(new File(url.toURI()), name);
           if (file == null) {
              fail("Unable to find requested file " + name);
           }
           return file;
       }
   }

   public InputStream getResourceAsStream(String name) {
       InputStream stream = this.getClass().getResourceAsStream(name);
       if (stream == null) {
          fail("Unable to find requested resource " + name);
       }
       return stream;
   }
    
    public void assertContains(String needle, String haystack) {
       assertTrue(needle + " not found in:\n" + haystack, haystack.contains(needle));
    }

    protected static class XMLResult {
        public final String xml;
        public final Metadata metadata;

        public XMLResult(String xml, Metadata metadata) {
            this.xml = xml;
            this.metadata = metadata;
        }
    }

    protected XMLResult getXML(String filePath) throws Exception {
        InputStream input = null;
        Metadata metadata = new Metadata();
        Parser parser = new AutoDetectParser();
        
        StringWriter sw = new StringWriter();
        SAXTransformerFactory factory = (SAXTransformerFactory)
                 SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.INDENT, "no");
        handler.setResult(new StreamResult(sw));

        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);

        input = getResourceAsStream("/test-documents/" + filePath);
        try {
            parser.parse(input, handler, metadata, context);
            return new XMLResult(sw.toString(), metadata);
        } finally {
            input.close();
        }
    }

}
