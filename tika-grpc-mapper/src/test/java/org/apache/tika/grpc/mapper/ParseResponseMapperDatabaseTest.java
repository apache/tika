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
package org.apache.tika.grpc.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import org.apache.tika.grpc.v1.ParseResponse;

class ParseResponseMapperDatabaseTest extends ParseFixtureSupport {

    @Test
    void mapsDbfDatabaseContentType() throws Exception {
        ParseResponse response = map(parseBody("testDBF.dbf"), "testDBF.dbf");

        assertTrue(response.hasDatabase());
        assertTrue(response.hasContentType());
        assertTrue(response.getContentType().startsWith("application/x-dbf"));
        assertTrue(response.getDatabase().getContentType().isEmpty());
    }

    @Test
    void mapsAccessDatabaseWhenAvailable() throws Exception {
        List<String> accessFiles = ClasspathTestDocuments.listByExtensions(".mdb", ".accdb");
        Assumptions.assumeFalse(accessFiles.isEmpty(), "No Access fixtures on classpath");
        ParseResponse response = map(parseBody(accessFiles.get(0)), accessFiles.get(0));
        Assumptions.assumeTrue(response.hasDatabase(), "Access DB did not map (JDBC may be unavailable)");
        assertTrue(response.hasContentType());
        String mime = response.getContentType().toLowerCase(java.util.Locale.ROOT);
        assertTrue(mime.contains("access") || mime.contains("mdb"));
    }

}
