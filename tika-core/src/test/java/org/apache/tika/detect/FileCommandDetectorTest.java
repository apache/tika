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
package org.apache.tika.detect;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

public class FileCommandDetectorTest {

    private static Detector DETECTOR;

    @BeforeClass
    public static void setUp() throws Exception {
        try (InputStream is = TikaConfig.class.getResourceAsStream("FileCommandDetector.xml")) {
            DETECTOR = new TikaConfig(is).getDetector();
        }
    }

    @Test
    public void testBasic() throws Exception {
        assumeTrue(FileCommandDetector.checkHasFile());

        try (InputStream is = getClass().getResourceAsStream("/test-documents/basic_embedded.xml")) {
            assertEquals(MediaType.text("xml"), DETECTOR.detect(is, new Metadata()));
            //make sure that the detector is resetting the stream
            assertEquals(MediaType.text("xml"), DETECTOR.detect(is, new Metadata()));
        }

        //now try with TikaInputStream
        try (InputStream is = TikaInputStream.get(getClass()
                .getResourceAsStream("/test-documents/basic_embedded.xml"))) {
            assertEquals(MediaType.text("xml"), DETECTOR.detect(is, new Metadata()));
            //make sure that the detector is resetting the stream
            assertEquals(MediaType.text("xml"), DETECTOR.detect(is, new Metadata()));
        }
    }
}
