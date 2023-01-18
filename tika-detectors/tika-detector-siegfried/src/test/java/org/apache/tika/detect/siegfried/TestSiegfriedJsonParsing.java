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
package org.apache.tika.detect.siegfried;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.FileProcessResult;

public class TestSiegfriedJsonParsing extends TikaTest {

    //TODO -- add testcontainers unit test with dockerized siegfried

    @Test
    public void testBasic() throws Exception {
        FileProcessResult fileProcessResult = load("test-basic.json");
        Metadata metadata = new Metadata();
        SiegfriedDetector.processResult(fileProcessResult, metadata, false);
        assertEquals("1.9.5", metadata.get(SiegfriedDetector.SIEGFRIED_VERSION));
        assertEquals("default.sig", metadata.get(SiegfriedDetector.SIEGFRIED_SIGNATURE));
        assertEquals("fmt/19", metadata.get("sf:pronom:id"));
        assertEquals("extension match pdf; byte match at [[0 8] [810818 5]]", metadata.get("sf:pronom:basis"));
        assertEquals("Acrobat PDF 1.5 - Portable Document Format", metadata.get("sf:pronom:format"));
        assertEquals("application/pdf", metadata.get("sf:pronom:mime"));
        assertEquals("1.5", metadata.get("sf:pronom:version"));

    }

    @Test
    public void testErrors() throws Exception {
        FileProcessResult fileProcessResult = load("test-errors.json");
        Metadata metadata = new Metadata();
        SiegfriedDetector.processResult(fileProcessResult, metadata, false);
        //debug(metadata);
        assertEquals("1.9.5", metadata.get(SiegfriedDetector.SIEGFRIED_VERSION));
        assertEquals("default.sig", metadata.get(SiegfriedDetector.SIEGFRIED_SIGNATURE));
        assertEquals("x-fmt/111", metadata.get("sf:pronom:id"));
        assertEquals("extension match txt", metadata.get("sf:pronom:basis"));
        assertEquals("Plain Text File", metadata.get("sf:pronom:format"));
        assertEquals("text/plain", metadata.get("sf:pronom:mime"));
        assertNull(metadata.get("sf:pronom:version"));
        assertEquals("empty source", metadata.get(SiegfriedDetector.SIEGFRIED_ERRORS));
    }

    @Test
    public void testWarnings() throws Exception {
        FileProcessResult fileProcessResult = load("test-warnings.json");
        Metadata metadata = new Metadata();
        SiegfriedDetector.processResult(fileProcessResult, metadata, false);
        assertEquals("1.9.5", metadata.get(SiegfriedDetector.SIEGFRIED_VERSION));
        assertEquals("default.sig", metadata.get(SiegfriedDetector.SIEGFRIED_SIGNATURE));
        assertEquals("UNKNOWN", metadata.get("sf:pronom:id"));
        assertNull(metadata.get("sf:pronom:basis"));
        assertNull(metadata.get("sf:pronom:format"));
        assertNull(metadata.get("sf:pronom:mime"));
        assertNull(metadata.get("sf:pronom:version"));
        assertTrue(metadata.get("sf:pronom:warning")
                .startsWith("no match; possibilities based on extension are fmt/14, fmt/15, fmt/16, " +
                        "fmt/17, fmt/18, fmt/19"));
    }




    private FileProcessResult load(String jsonFileName) throws IOException {
        String jsonString = IOUtils.toString(
                getClass().getResourceAsStream("/json/" + jsonFileName), StandardCharsets.UTF_8);
        FileProcessResult r = new FileProcessResult();
        r.setStdout(jsonString);
        r.setExitValue(0);
        return r;
    }
}
