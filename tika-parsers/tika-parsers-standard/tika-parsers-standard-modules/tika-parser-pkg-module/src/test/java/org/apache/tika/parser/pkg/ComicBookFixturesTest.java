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
package org.apache.tika.parser.pkg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;

/**
 * testComicBook.cbr (RAR5), .cb7 and .cbt hold three colored pages stored as 3, 1, 2 and a
 * ComicInfo.xml, for trying the parsers by hand; page 1, the red one, is the cover.
 */
public class ComicBookFixturesTest extends AbstractPkgTest {

    @Test
    public void testFixtures() throws Exception {
        String[][] cases = {{"testComicBook.cbr", "application/vnd.comicbook-rar"},
                {"testComicBook.cb7", "application/x-cb7"},
                {"testComicBook.cbt", "application/x-cbt"}};
        for (String[] c : cases) {
            List<Metadata> list = getRecursiveMetadata(c[0]);
            assertEquals(c[1], list.get(0).get(HttpHeaders.CONTENT_TYPE), c[0]);
            Map<String, String> types = resourceTypes(list);
            assertEquals("THUMBNAIL", types.get("pages/page-001.png"), c[0] + " " + types);
            assertEquals("null", types.get("pages/page-003.png"), c[0]);
        }
    }
}
