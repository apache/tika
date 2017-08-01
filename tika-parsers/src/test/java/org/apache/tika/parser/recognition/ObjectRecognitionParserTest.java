/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.recognition;

import org.apache.commons.lang.StringUtils;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.tika.Tika;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.recognition.tf.TensorflowImageRecParser;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.List;

/**
 * Testcases for Object Recognition Parser
 */
public class ObjectRecognitionParserTest {

    // Config files
    private static final String CONFIG_FILE_OBJ_REC = "org/apache/tika/parser/recognition/tika-config-tflow.xml";
    private static final String CONFIG_REST_FILE_OBJ_REC = "org/apache/tika/parser/recognition/tika-config-tflow-rest.xml";
    private static final String CONFIG_REST_FILE_IM2TXT = "org/apache/tika/parser/recognition/tika-config-tflow-im2txt-rest.xml";

    // Test images
    private static final String CAT_IMAGE_JPEG = "test-documents/testJPEG.jpg";
    private static final String CAT_IMAGE_PNG = "test-documents/testPNG.png";
    private static final String CAT_IMAGE_GIF = "test-documents/testGIF.gif";

    private static final String BASEBALL_IMAGE_JPEG = "test-documents/baseball.jpg";
    private static final String BASEBALL_IMAGE_PNG = "test-documents/baseball.png";
    private static final String BASEBALL_IMAGE_GIF = "test-documents/baseball.gif";

    private static final ClassLoader loader = ObjectRecognitionParserTest.class.getClassLoader();

    private static final Logger LOG = LoggerFactory.getLogger(ObjectRecognitionParserTest.class);
    
    @Test
    public void jpegTFObjRecTest() throws IOException, TikaException, SAXException {
      TensorflowImageRecParser p = new TensorflowImageRecParser();
      Assume.assumeTrue(p.isAvailable());      
        try (InputStream stream = loader.getResourceAsStream(CONFIG_FILE_OBJ_REC)) {
            assert stream != null;
            Tika tika = new Tika(new TikaConfig(stream));
            Metadata metadata = new Metadata();
            try (InputStream imageStream = loader.getResourceAsStream(CAT_IMAGE_JPEG)) {
                Reader reader = tika.parse(imageStream, metadata);
                List<String> lines = IOUtils.readLines(reader);
                String text = StringUtils.join(lines, " ");
                String[] expectedObjects = {"Egyptian cat", "tabby, tabby cat"};
                String metaValues = StringUtils.join(metadata.getValues(ObjectRecognitionParser.MD_KEY_OBJ_REC), " ");
                for (String expectedObject : expectedObjects) {
                    String message = "'" + expectedObject + "' must have been detected";
                    Assert.assertTrue(message, text.contains(expectedObject));
                    Assert.assertTrue(message, metaValues.contains(expectedObject));
                }
            }
        }
    }

    @Test
    public void jpegRESTObjRecTest() throws Exception {
        String apiUrl = "http://localhost:8764/inception/v4/ping";
        boolean available = false;
        int status = 500;
        try{
          status = WebClient.create(apiUrl).get().getStatus();
          available = status == 200;
        }
        catch(Exception ignore){}
        Assume.assumeTrue(available);
        String[] expectedObjects = {"Egyptian cat", "tabby, tabby cat"};
        doRecognize(CONFIG_REST_FILE_OBJ_REC, CAT_IMAGE_JPEG,
                ObjectRecognitionParser.MD_KEY_OBJ_REC, expectedObjects);
    }

    @Test
    public void pngRESTObjRecTest() throws Exception {
        String apiUrl = "http://localhost:8764/inception/v4/ping";
        boolean available = false;
        int status = 500;
        try{
            status = WebClient.create(apiUrl).get().getStatus();
            available = status == 200;
        }
        catch(Exception ignore){}
        Assume.assumeTrue(available);
        String[] expectedObjects = {"Egyptian cat", "tabby, tabby cat"};
        doRecognize(CONFIG_REST_FILE_OBJ_REC, CAT_IMAGE_PNG,
                ObjectRecognitionParser.MD_KEY_OBJ_REC, expectedObjects);
    }

    @Test
    public void gifRESTObjRecTest() throws Exception {
        String apiUrl = "http://localhost:8764/inception/v4/ping";
        boolean available = false;
        int status = 500;
        try{
            status = WebClient.create(apiUrl).get().getStatus();
            available = status == 200;
        }
        catch(Exception ignore){}
        Assume.assumeTrue(available);
        String[] expectedObjects = {"Egyptian cat"};
        doRecognize(CONFIG_REST_FILE_OBJ_REC, CAT_IMAGE_GIF,
                ObjectRecognitionParser.MD_KEY_OBJ_REC, expectedObjects);
    }

    @Test
    public void jpegRESTim2txtTest() throws Exception {
        String apiUrl = "http://localhost:8764/inception/v3/ping";
        boolean available = false;
        int status = 500;
        try{
          status = WebClient.create(apiUrl).get().getStatus();
          available = status == 200;
        }
        catch(Exception ignore){}
        Assume.assumeTrue(available);   
        String[] expectedCaption = {"a baseball player holding a bat on a field"};
        doRecognize(CONFIG_REST_FILE_IM2TXT, BASEBALL_IMAGE_JPEG,
                ObjectRecognitionParser.MD_KEY_IMG_CAP, expectedCaption);
    }

    @Test
    public void pngRESTim2txtTest() throws Exception {
        String apiUrl = "http://localhost:8764/inception/v3/ping";
        boolean available = false;
        int status = 500;
        try{
          status = WebClient.create(apiUrl).get().getStatus();
          available = status == 200;
        }
        catch(Exception ignore){}
        Assume.assumeTrue(available);  
        String[] expectedCaption = {"a baseball player holding a bat on a field"};
        doRecognize(CONFIG_REST_FILE_IM2TXT, BASEBALL_IMAGE_PNG,
                ObjectRecognitionParser.MD_KEY_IMG_CAP, expectedCaption);
    }

    @Test
    public void gifRESTim2txtTest() throws Exception {
        String apiUrl = "http://localhost:8764/inception/v3/ping";
        boolean available = false;
        int status = 500;
        try{
          status = WebClient.create(apiUrl).get().getStatus();
          available = status == 200;
        }
        catch(Exception ignore){}
        Assume.assumeTrue(available);  
        String[] expectedCaption = {"a baseball player pitching a ball on top of a field"};
        doRecognize(CONFIG_REST_FILE_IM2TXT, BASEBALL_IMAGE_GIF,
                ObjectRecognitionParser.MD_KEY_IMG_CAP, expectedCaption);
    }

    private void doRecognize(String configFile, String testImg, String mdKey, String[] expectedObjects) throws Exception {
        try (InputStream stream = loader.getResourceAsStream(configFile)) {
            assert stream != null;
            Tika tika = new Tika(new TikaConfig(stream));
            Metadata metadata = new Metadata();
            try (InputStream imageStream = loader.getResourceAsStream(testImg)) {
                Reader reader = tika.parse(imageStream, metadata);
                String text = IOUtils.toString(reader);
                String metaValues = StringUtils.join(metadata.getValues(mdKey), " ");
                LOG.info("MetaValues = {}", metaValues);
                for (String expectedObject : expectedObjects) {
                    String message = "'" + expectedObject + "' must have been detected";
                    Assert.assertTrue(message, text.contains(expectedObject));
                    Assert.assertTrue(message, metaValues.contains(expectedObject));
                }
            }
        }
    }
}