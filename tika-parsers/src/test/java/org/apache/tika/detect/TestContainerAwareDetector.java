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
package org.apache.tika.detect;

import java.io.InputStream;

import junit.framework.TestCase;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

/**
 * Junit test class for {@link ContainerAwareDetector}
 */
public class TestContainerAwareDetector extends TestCase {
    private TikaConfig tc;
    private ContainerAwareDetector d;

    public void setUp() throws Exception {
        tc = TikaConfig.getDefaultConfig();
        d = new ContainerAwareDetector(tc.getMimeRepository());
    }
    
    private InputStream getTestDoc(String filename) {
        InputStream input = TestContainerAwareDetector.class.getResourceAsStream(
            "/test-documents/" + filename);
        assertNotNull(input);
        return input;
    }
    
    public void testDetectOLE2() throws Exception {
        InputStream input;
        
        input = getTestDoc("testEXCEL.xls");
        assertEquals(
        	MediaType.application("vnd.ms-excel"),
        	d.detect(input, new Metadata())
        );
        
        input = getTestDoc("testWORD.doc");
        assertEquals(
        	MediaType.application("msword"),
        	d.detect(input, new Metadata())
        );
        
        input = getTestDoc("testPPT.ppt");
        assertEquals(
        	MediaType.application("vnd.ms-powerpoint"),
        	d.detect(input, new Metadata())
        );
        
        TikaInputStream tis = TikaInputStream.get(getTestDoc("testPPT.ppt"));
        assertEquals(
        	MediaType.application("vnd.ms-powerpoint"),
        	d.detect(tis, new Metadata())
        );
        
        assertNotNull(tis.getOpenContainer());
        assertEquals(POIFSFileSystem.class, tis.getOpenContainer().getClass());
    }
    
    public void testDetectODF() throws Exception {
        InputStream input;
        
        input = getTestDoc("testODFwithOOo3.odt");
        assertEquals(
        	MediaType.application("vnd.oasis.opendocument.text"),
        	d.detect(input, new Metadata())
        );
        
        input = getTestDoc("testOpenOffice2.odf");
        assertEquals(
        	MediaType.application("vnd.oasis.opendocument.formula"),
        	d.detect(input, new Metadata())
        );
        
        TikaInputStream tis = TikaInputStream.get(getTestDoc("testOpenOffice2.odf"));
        assertEquals(
        	MediaType.application("vnd.oasis.opendocument.formula"),
        	d.detect(tis, new Metadata())
        );
        // Doesn't store the zip parser yet
        assertNull(tis.getOpenContainer());
    }
    
    public void testDetectOOXML() throws Exception {
        InputStream input;
        
        input = getTestDoc("testEXCEL.xlsx");
        assertEquals(
        	MediaType.application("vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
        	d.detect(input, new Metadata())
        );
        
        input = getTestDoc("testWORD.docx");
        assertEquals(
        	MediaType.application("vnd.openxmlformats-officedocument.wordprocessingml.document"),
        	d.detect(input, new Metadata())
        );
        
        input = getTestDoc("testPPT.pptx");
        assertEquals(
        	MediaType.application("vnd.openxmlformats-officedocument.presentationml.presentation"),
        	d.detect(input, new Metadata())
        );
        
        TikaInputStream tis = TikaInputStream.get(getTestDoc("testPPT.pptx"));
        assertEquals(
        	MediaType.application("vnd.openxmlformats-officedocument.presentationml.presentation"),
        	d.detect(tis, new Metadata())
        );
        
        assertNotNull(tis.getOpenContainer());
        assertTrue(
                "Open container should be OPCPackage, not " + tis.getOpenContainer().getClass(), 
                tis.getOpenContainer() instanceof OPCPackage
        );
    }
    
    public void testDetectZip() throws Exception {
	
    }
}
