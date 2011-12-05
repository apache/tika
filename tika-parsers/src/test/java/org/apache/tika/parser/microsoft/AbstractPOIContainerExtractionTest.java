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
package org.apache.tika.parser.microsoft;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;

import org.apache.tika.extractor.ContainerExtractor;
import org.apache.tika.extractor.EmbeddedResourceHandler;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;

/**
 * Parent class of tests that the various POI powered parsers are
 *  able to extract their embedded contents.
 */
public abstract class AbstractPOIContainerExtractionTest extends TestCase {
    public static final MediaType TYPE_DOC = MediaType.application("msword");
    public static final MediaType TYPE_PPT = MediaType.application("vnd.ms-powerpoint");
    public static final MediaType TYPE_XLS = MediaType.application("vnd.ms-excel");
    public static final MediaType TYPE_DOCX = MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document");
    public static final MediaType TYPE_PPTX = MediaType.application("vnd.openxmlformats-officedocument.presentationml.presentation");
    public static final MediaType TYPE_XLSX = MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    public static final MediaType TYPE_MSG = MediaType.application("vnd.ms-outlook");
    
    public static final MediaType TYPE_TXT = MediaType.text("plain");
    public static final MediaType TYPE_PDF = MediaType.application("pdf");
    
    public static final MediaType TYPE_JPG = MediaType.image("jpeg");
    public static final MediaType TYPE_GIF = MediaType.image("gif");
    public static final MediaType TYPE_PNG = MediaType.image("png");
    public static final MediaType TYPE_EMF = MediaType.application("x-emf");
    public static final MediaType TYPE_WMF = MediaType.application("x-msmetafile");

    protected TrackingHandler process(String filename, ContainerExtractor extractor, boolean recurse) throws Exception {
        TikaInputStream stream = getTestFile(filename);
        try {
            assertEquals(true, extractor.isSupported(stream));

            // Process it
            TrackingHandler handler = new TrackingHandler();
            if(recurse) {
                extractor.extract(stream, extractor, handler);
            } else {
                extractor.extract(stream, null, handler);
            }

            // So they can check what happened
            return handler;
        } finally {
            stream.close();
        }
    }
    
    protected TikaInputStream getTestFile(String filename) throws Exception {
        URL input = AbstractPOIContainerExtractionTest.class.getResource(
               "/test-documents/" + filename);
        assertNotNull(filename + " not found", input);

        return TikaInputStream.get(input);
    }
    
    public static class TrackingHandler implements EmbeddedResourceHandler {
       public List<String> filenames = new ArrayList<String>();
       public List<MediaType> mediaTypes = new ArrayList<MediaType>();
       
       public void handle(String filename, MediaType mediaType,
            InputStream stream) {
          filenames.add(filename);
          mediaTypes.add(mediaType);
      }
    }
}
