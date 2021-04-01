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
package org.apache.tika.mime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.InputStream;

import org.junit.Before;
import org.junit.Test;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

public class TestMimeTypesExtended {

    MimeTypes repo;

    @Before
    public void setUp() throws Exception {
        TikaConfig config = TikaConfig.getDefaultConfig();
        repo = config.getMimeRepository();
    }

    @Test
    public void testNetCDF() throws Exception {
        assertTypeByData("application/x-netcdf", "sresa1b_ncar_ccsm3_0_run1_200001.nc");
    }

    private void assertTypeByData(String expected, String filename) throws IOException {
        try (InputStream stream = TikaInputStream.get(TestMimeTypesExtended.class
                .getResourceAsStream("/test-documents/" + filename))) {
            assertNotNull("Test file not found: " + filename, stream);
            Metadata metadata = new Metadata();
            assertEquals(expected, repo.detect(stream, metadata).toString());
        }
    }
}
