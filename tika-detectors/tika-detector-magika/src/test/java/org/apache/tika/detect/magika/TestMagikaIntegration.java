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

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.Parser;

@Disabled("need to have magika on the path")
public class TestMagikaIntegration extends TikaTest {

    @Test
    public void testIntegration() throws Exception {
        TikaConfig tikaConfig = new TikaConfig(getConfig("tika-config.xml"));
        Parser p = new AutoDetectParser(tikaConfig);
        List<Metadata> metadataList = getRecursiveMetadata("testPDF.pdf", p);
        debug(getRecursiveMetadata("testPDF.pdf", p));
        Metadata m = metadataList.get(0);
        assertEquals("PDF document", m.get(MagikaDetector.MAGIKA_DESCRIPTION));
        assertEquals(1.0, Double.parseDouble(m.get(MagikaDetector.MAGIKA_SCORE)), 0.1);
        assertEquals("document", m.get(MagikaDetector.MAGIKA_GROUP));
        assertEquals("0.1.0-rc.1", m.get(MagikaDetector.MAGIKA_VERSION));
        assertEquals("application/pdf", m.get(MagikaDetector.MAGIKA_MIME));
        assertEquals("application/pdf", m.get(Metadata.CONTENT_TYPE));
        assertEquals("ok", m.get(MagikaDetector.MAGIKA_STATUS));
    }

    private Path getConfig(String configName) throws URISyntaxException {
        return Paths.get(
                getClass().getResource("/configs/" + configName).toURI());
    }

}
