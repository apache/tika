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
package org.apache.tika.parser.microsoft.ooxml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

public class TruncatedOOXMLTest extends TikaTest {

    @Test
    public void testWordTrunc14435() throws Exception {
        //this is only very slightly truncated
        List<Metadata> metadataList =
                getRecursiveMetadata(truncate("testWORD_various.docx", 14435), true);
        assertEquals(1, metadataList.size());
        Metadata metadata = metadataList.get(0);
        String content = metadata.get(TikaCoreProperties.TIKA_CONTENT);
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                metadata.get(Metadata.CONTENT_TYPE));
        assertContains("This is the header", content);
        assertContains("This is the footer text", content);
        assertContains("Suddenly some Japanese", content);
    }


    @Test
    public void testTruncation() throws Exception {

        int length = (int) getResourceAsFile("/test-documents/testWORD_various.docx").length();
        Random r = new Random();
        for (int i = 0; i < 50; i++) {
            int targetLength = r.nextInt(length);
            InputStream is = truncate("testWORD_various.docx", targetLength);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            IOUtils.copy(is, bos);
            assertEquals(targetLength, bos.toByteArray().length);
        }
        try {
            InputStream is = truncate("testWORD_various.docx", length + 1);
            fail("should have thrown EOF");
        } catch (EOFException e) {
            //swallow
        }
    }

    @Test
    @Disabled("for dev/debugging only")
    public void listStreams() throws Exception {
        File tstDir = new File(getResourceAsUri("/test-documents"));
        for (File f : tstDir.listFiles()) {
            if (f.isDirectory()) {
                continue;
            }
            if (f.getName().endsWith(
                    ".xlsx")) { // || f.getName().endsWith(".pptx") || f.getName().endsWith("
                // .docx")) {

            } else {
                continue;
            }
            try (InputStream is = new FileInputStream(f)) {
                ZipArchiveInputStream zipArchiveInputStream = new ZipArchiveInputStream(is);
                ZipArchiveEntry zae = zipArchiveInputStream.getNextZipEntry();
                int cnt = 0;
                while (zae != null && !zae.isDirectory() && ++cnt <= 10) {
                    System.out.println(f.getName() + " : " + zae.getName());
                    if (zae.getName().equals("_rels/.rels")) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        IOUtils.copy(zipArchiveInputStream, bos);
                        System.out.println(new String(bos.toByteArray(), StandardCharsets.UTF_8));
                    }
                    zae = zipArchiveInputStream.getNextZipEntry();
                }
            } catch (Exception e) {
                System.out.println(f.getName() + " : " + e.getMessage());
            }
        }
    }
}
