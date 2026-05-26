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
package org.apache.tika.pipes.googledrive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import org.apache.tika.pipes.core.testutil.AbstractConfigExamplesTest;
import org.apache.tika.pipes.fetcher.googledrive.config.GoogleDriveFetcherConfig;

/**
 * Validates Google Drive fetcher configuration examples used in documentation.
 */
public class ConfigExamplesTest extends AbstractConfigExamplesTest {

    @Test
    public void testGoogleDriveFetcherConfig() throws Exception {
        loadAndValidate("google-drive-fetcher.json");

        JsonNode inner = innerComponent(readExample("google-drive-fetcher.json"),
                "fetchers", "gdf", "google-drive-fetcher");
        GoogleDriveFetcherConfig config = GoogleDriveFetcherConfig.load(inner.toString());
        assertEquals("tika-pipes", config.getApplicationName());
        assertEquals("user@example.com", config.getSubjectUser());
        assertNotNull(config.getServiceAccountKeyBase64());
        assertTrue(config.getScopes().contains("https://www.googleapis.com/auth/drive.readonly"));
        assertTrue(config.isSpoolToTemp());
    }
}
