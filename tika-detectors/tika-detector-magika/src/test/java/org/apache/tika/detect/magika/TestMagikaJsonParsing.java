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
package org.apache.tika.detect.magika;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.FileProcessResult;

public class TestMagikaJsonParsing extends TikaTest {

    //TODO -- add testcontainers unit test with dockerized magika

    @Test
    public void testPython0_5_1() throws Exception {
        //this is the older python package available at the time of development from pypi
        FileProcessResult fileProcessResult = load("test-basic-0.5.1.json");
        Metadata metadata = new Metadata();
        MagikaDetector.processResult(fileProcessResult, metadata, false);
        assertEquals("ok", metadata.get(MagikaDetector.MAGIKA_STATUS));
        assertEquals("Python source", metadata.get(MagikaDetector.MAGIKA_DESCRIPTION));
        assertEquals(0.999987125396, Double.parseDouble(metadata.get(MagikaDetector.MAGIKA_SCORE)), 0.0000001);
        assertEquals("code", metadata.get(MagikaDetector.MAGIKA_GROUP));
        assertEquals("python", metadata.get(MagikaDetector.MAGIKA_LABEL));
        assertEquals("text/x-python", metadata.get(MagikaDetector.MAGIKA_MIME));
        assertNull(metadata.get(MagikaDetector.MAGIKA_IS_TEXT));
    }

    @Test
    public void testRust0_1_0_rc1() throws Exception {
        //this is the way of the future -- rust-based
        FileProcessResult fileProcessResult = load("test-basic.json");
        Metadata metadata = new Metadata();
        MagikaDetector.processResult(fileProcessResult, metadata, false);
        assertEquals("ok", metadata.get(MagikaDetector.MAGIKA_STATUS));
        assertEquals("Python source", metadata.get(MagikaDetector.MAGIKA_DESCRIPTION));
        assertEquals(0.753000020980835, Double.parseDouble(metadata.get(MagikaDetector.MAGIKA_SCORE)), 0.0000001);
        assertEquals("code", metadata.get(MagikaDetector.MAGIKA_GROUP));
        assertEquals("python", metadata.get(MagikaDetector.MAGIKA_LABEL));
        assertEquals("text/x-python", metadata.get(MagikaDetector.MAGIKA_MIME));
        assertEquals(true, Boolean.parseBoolean(metadata.get(MagikaDetector.MAGIKA_IS_TEXT)));

    }
/*
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


*/

    private FileProcessResult load(String jsonFileName) throws IOException {
        String jsonString = IOUtils.toString(
                getClass().getResourceAsStream("/json/" + jsonFileName), StandardCharsets.UTF_8);
        FileProcessResult r = new FileProcessResult();
        r.setStdout(jsonString);
        r.setExitValue(0);
        return r;
    }
}
