package org.apache.tika.parser;

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


import org.apache.tika.metadata.Metadata;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.junit.Test;
import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class RecursiveParserWrapperTest {

    @Test
    public void testBasicXML() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        Metadata container = list.get(0);
        String content = container.get(RecursiveParserWrapper.TIKA_CONTENT);
        //not much differentiates html from xml in this test file
        assertTrue(content.indexOf("<p class=\"header\" />") > -1);
    }

    @Test
    public void testBasicHTML() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.HTML, -1));
        Metadata container = list.get(0);
        String content = container.get(RecursiveParserWrapper.TIKA_CONTENT);
        //not much differentiates html from xml in this test file
        assertTrue(content.indexOf("<p class=\"header\"></p>") > -1);
    }

    @Test
    public void testBasicText() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        Metadata container = list.get(0);
        String content = container.get(RecursiveParserWrapper.TIKA_CONTENT);
        assertTrue(content.indexOf("<p ") < 0);
        assertTrue(content.indexOf("embed_0") > -1);
    }

    @Test
    public void testIgnoreContent() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1));
        Metadata container = list.get(0);
        String content = container.get(RecursiveParserWrapper.TIKA_CONTENT);
        assertNull(content);
    }

    
    @Test
    public void testCharLimit() throws Exception {
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
      
        Parser wrapped = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(wrapped,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, 60));
        InputStream stream = RecursiveParserWrapperTest.class.getResourceAsStream(
                "/test-documents/test_recursive_embedded.docx");
        wrapper.parse(stream, new DefaultHandler(), metadata, context);
        List<Metadata> list = wrapper.getMetadata();
        
        assertEquals(5, list.size());
        
        int wlr = 0;
        for (Metadata m : list) {
            String limitReached = m.get(RecursiveParserWrapper.WRITE_LIMIT_REACHED);
            if (limitReached != null && limitReached.equals("true")){
                wlr++;
            }
        }
        assertEquals(1, wlr);

    }
    @Test
    public void testMaxEmbedded() throws Exception {
        int maxEmbedded = 4;
        int totalNoLimit = 12;//including outer container file
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String limitReached = null;
        
        Parser wrapped = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(wrapped,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));

        InputStream stream = RecursiveParserWrapperTest.class.getResourceAsStream(
                "/test-documents/test_recursive_embedded.docx");
        wrapper.parse(stream, new DefaultHandler(), metadata, context);
        List<Metadata> list = wrapper.getMetadata();
        //test default
        assertEquals(totalNoLimit, list.size());

        limitReached = list.get(0).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_LIMIT_REACHED);
        assertNull(limitReached);

        
        wrapper.reset();
        stream.close();

        //test setting value
        metadata = new Metadata();
        stream = RecursiveParserWrapperTest.class.getResourceAsStream(
                "/test-documents/test_recursive_embedded.docx");
        wrapper.setMaxEmbeddedResources(maxEmbedded);
        wrapper.parse(stream, new DefaultHandler(), metadata, context);
        list = wrapper.getMetadata();

        //add 1 for outer container file
        assertEquals(maxEmbedded+1, list.size());
        
        limitReached = list.get(0).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_LIMIT_REACHED);
        assertEquals("true", limitReached);

        wrapper.reset();
        stream.close();
        
        //test setting value < 0
        metadata = new Metadata();
        stream = RecursiveParserWrapperTest.class.getResourceAsStream(
                "/test-documents/test_recursive_embedded.docx");
        
        wrapper.setMaxEmbeddedResources(-2);
        wrapper.parse(stream, new DefaultHandler(), metadata, context);
        assertEquals(totalNoLimit, list.size());
        limitReached = list.get(0).get(RecursiveParserWrapper.EMBEDDED_RESOURCE_LIMIT_REACHED);
        assertNull(limitReached);
    }
    
    @Test
    public void testEmbeddedResourcePath() throws Exception {
        
        Set<String> targets = new HashSet<String>();
        targets.add("test_recursive_embedded.docx/embed1.zip");
        targets.add("test_recursive_embedded.docx/embed1.zip/embed2.zip");
        targets.add("test_recursive_embedded.docx/embed1.zip/embed2.zip/embed3.zip");
        targets.add("test_recursive_embedded.docx/embed1.zip/embed2.zip/embed3.zip/embed4.zip");
        targets.add("test_recursive_embedded.docx/embed1.zip/embed2.zip/embed3.zip/embed4.zip/embed4.txt");
        targets.add("test_recursive_embedded.docx/embed1.zip/embed2.zip/embed3.zip/embed3.txt");
        targets.add("test_recursive_embedded.docx/embed1.zip/embed2.zip/embed2a.txt");
        targets.add("test_recursive_embedded.docx/embed1.zip/embed2.zip/embed2b.txt");
        targets.add("test_recursive_embedded.docx/embed1.zip/embed1b.txt");
        targets.add("test_recursive_embedded.docx/embed1.zip/embed1a.txt");
        targets.add("test_recursive_embedded.docx/image1.emf");
        
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, "test_recursive_embedded.docx");
        List<Metadata> list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        Metadata container = list.get(0);
        String content = container.get(RecursiveParserWrapper.TIKA_CONTENT);
        assertTrue(content.indexOf("<p class=\"header\" />") > -1);        
        
        Set<String> seen = new HashSet<String>();
        for (Metadata m : list) {
            String path = m.get(RecursiveParserWrapper.EMBEDDED_RESOURCE_PATH);
            if (path != null) {
                seen.add(path);
            }
        }
        assertEquals(targets, seen);
    }
    
    private List<Metadata> getMetadata(Metadata metadata, ContentHandlerFactory contentHandlerFactory)
            throws Exception{
        ParseContext context = new ParseContext();
        Parser wrapped = new AutoDetectParser();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(wrapped, contentHandlerFactory);
        InputStream stream = RecursiveParserWrapperTest.class.getResourceAsStream(
                "/test-documents/test_recursive_embedded.docx");
        wrapper.parse(stream, new DefaultHandler(), metadata, context);
        return wrapper.getMetadata();
    }
}
