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
package org.apache.tika.parser.chm;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.chm.accessor.ChmDirectoryListingSet;
import org.apache.tika.parser.chm.accessor.DirectoryListingEntry;
import org.apache.tika.parser.chm.core.ChmExtractor;
import org.apache.tika.sax.BodyContentHandler;
import org.junit.Test;
import org.xml.sax.SAXException;

public class TestChmExtraction {

    private final Parser parser = new ChmParser();

    private final List<String> files = Arrays.asList(
            "/test-documents/testChm.chm",
            "/test-documents/testChm2.chm",
            "/test-documents/testChm3.chm");

    @Test
    public void testGetText() throws Exception {
        BodyContentHandler handler = new BodyContentHandler();
        new ChmParser().parse(
                new ByteArrayInputStream(TestParameters.chmData),
                handler, new Metadata(), new ParseContext());
        assertTrue(handler.toString().contains(
                "The TCard method accepts only numeric arguments"));
    }

    @Test
    public void testChmParser() throws Exception{
        for (String fileName : files) {
            InputStream stream = TestChmExtraction.class.getResourceAsStream(fileName);
            testingChm(stream);
        }
    }

    private void testingChm(InputStream stream) throws IOException, SAXException, TikaException {
      try {
          BodyContentHandler handler = new BodyContentHandler(-1);
          parser.parse(stream, handler, new Metadata(), new ParseContext());
          assertTrue(!handler.toString().isEmpty());
      } finally {
          stream.close();
      }
    }

    @Test
    public void testExtractChmEntries() throws TikaException, IOException{
        for (String fileName : files) {
            InputStream stream =
                    TestChmExtraction.class.getResourceAsStream(fileName);
            try {
                testExtractChmEntry(stream);
            } finally {
                stream.close();
            }
        }
    }
    
    protected boolean findZero(byte[] textData) {
        for (byte b : textData) {
            if (b==0) {
                return true;
            }
        }
        
        return false;
    }
    
    protected boolean niceAscFileName(String name) {
        for (char c : name.toCharArray()) {
            if (c>=127 || c<32) {
                //non-ascii char or control char
                return false;
            }
        }
        
        return true;
    }
    
    protected void testExtractChmEntry(InputStream stream) throws TikaException, IOException{
        ChmExtractor chmExtractor = new ChmExtractor(stream);
        ChmDirectoryListingSet entries = chmExtractor.getChmDirList();
        final Pattern htmlPairP = Pattern.compile("\\Q<html\\E.+\\Q</html>\\E"
                , Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);
        
        Set<String> names = new HashSet<String>();
        
        for (DirectoryListingEntry directoryListingEntry : entries.getDirectoryListingEntryList()) {
            byte[] data = chmExtractor.extractChmEntry(directoryListingEntry);
            
            //Entry names should be nice. Disable this if the test chm do have bad looking but valid entry names.
            if (! niceAscFileName(directoryListingEntry.getName())) {
                throw new TikaException("Warning: File name contains a non ascii char : " + directoryListingEntry.getName());
            }
            
            final String lowName = directoryListingEntry.getName().toLowerCase(Locale.ROOT);
            
            //check duplicate entry name which is seen before.
            if (names.contains(lowName)) {
                throw new TikaException("Duplicate File name detected : " + directoryListingEntry.getName());
            }
            names.add(lowName);
            
            if (lowName.endsWith(".html")
                    || lowName.endsWith(".htm")
                    || lowName.endsWith(".hhk")
                    || lowName.endsWith(".hhc")
                    //|| name.endsWith(".bmp")
                    ) {
                if (findZero(data)) {
                    throw new TikaException("Xhtml/text file contains '\\0' : " + directoryListingEntry.getName());
                }

                //validate html
                String html = new String(data, "ISO-8859-1");
                if (! htmlPairP.matcher(html).find()) {
                    System.err.println(lowName + " is invalid.");
                    System.err.println(html);
                    throw new TikaException("Invalid xhtml file : " + directoryListingEntry.getName());
                }
//                else {
//                    System.err.println(directoryListingEntry.getName() + " is valid.");
//                }
            }
        }
    }
    

    @Test
    public void testMultiThreadedChmExtraction() throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(TestParameters.NTHREADS);
        for (int i = 0; i < TestParameters.NTHREADS; i++) {
            executor.execute(new Runnable() {
                public void run() {
                    for (String fileName : files) {
                        InputStream stream = null;
                        try {
                            stream = TestChmExtraction.class.getResourceAsStream(fileName);
                            BodyContentHandler handler = new BodyContentHandler(-1);
                            parser.parse(stream, handler, new Metadata(), new ParseContext());
                            assertTrue(!handler.toString().isEmpty());
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            try {
                                stream.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            });
        }
        executor.shutdown();
        // Waits until all threads will have finished
        while (!executor.isTerminated()) {
            Thread.sleep(500);
        }
    }
    
    @Test
    public void test_TIKA_1446() throws Exception {
        URL chmDir = TestChmExtraction.class.getResource("/test-documents/chm/");
        File chmFolder = new File(chmDir.toURI());
        for (String fileName : chmFolder.list()) {
            File file = new File(chmFolder, fileName);
            InputStream stream = new FileInputStream(file);
            testingChm(stream);
        }
    }
}
