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
package org.apache.tika.detect.apple;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.iwork.iwana.IWork13PackageParser.IWork13DocumentType;
import org.apache.tika.parser.iwork.iwana.IWork18PackageParser.IWork18DocumentType;

public class IWorkDetectorTest extends TikaTest {

    @Test
    public void testDetectKeynote13() throws Exception {
        String testFile = "/test-documents/testKeynote2013.detect";
        IWorkDetector detector = new IWorkDetector();
        try (TikaInputStream tis = TikaInputStream.get(getResourceAsStream(testFile));
                ZipFile zipFile = new ZipFile(tis.getFile())) {
            MediaType result = detector.detect(zipFile, tis);
            assertEquals(IWork13DocumentType.KEYNOTE13.getType(), result);
        }
    }

    @Test
    public void testDetectKeynote18() throws Exception {
        String testFile = "/test-documents/testKeynote2018.key";
        IWorkDetector detector = new IWorkDetector();
        try (TikaInputStream tis = TikaInputStream.get(getResourceAsStream(testFile));
                ZipFile zipFile = new ZipFile(tis.getFile())) {
            MediaType result = detector.detect(zipFile, tis);
            assertEquals(IWork18DocumentType.KEYNOTE18.getType(), result);
        }
    }

}
