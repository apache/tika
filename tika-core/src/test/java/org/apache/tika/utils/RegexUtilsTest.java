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
package org.apache.tika.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Test case for {@link RegexUtils}.
 *
 * @version $Revision$ $Date$
 */
public class RegexUtilsTest {

    /**
     * Test {@link RegexUtils#extractLinks(String)} with no links.
     */

    @Test
    public void testExtractLinksNone() {
        List<String> links = null;

        links = RegexUtils.extractLinks(null);
        assertNotNull(links);
        assertEquals(0, links.size());

        links = RegexUtils.extractLinks("");
        assertNotNull(links);
        assertEquals(0, links.size());

        links = RegexUtils.extractLinks("Test with no links " + "What about www.google.com");
        assertNotNull(links);
        assertEquals(0, links.size());
    }


    /**
     * Test {@link RegexUtils#extractLinks(String)} for http.
     */
    @Test
    public void testExtractLinksHttp() {
        List<String> links = RegexUtils.extractLinks(
                "Test with http://www.nutch.org/index.html is it found? " +
                        "What about www.google.com at http://www.google.de " +
                        "A longer URL could be http://www.sybit.com/solutions/portals.html");

        assertTrue(links.size() == 3, "Url not found!");
        assertEquals("http://www.nutch.org/index.html", links.get(0), "Wrong URL");
        assertEquals("http://www.google.de", links.get(1), "Wrong URL");
        assertEquals("http://www.sybit.com/solutions/portals.html", links.get(2),
                "Wrong URL");
    }

    /**
     * Test {@link RegexUtils#extractLinks(String)} for ftp.
     */
    @Test
    public void testExtractLinksFtp() {
        List<String> links = RegexUtils.extractLinks("Test with ftp://www.nutch.org is it found? " +
                "What about www.google.com at ftp://www.google.de");

        assertTrue(links.size() == 2, "Url not found!");
        assertEquals("ftp://www.nutch.org", links.get(0), "Wrong URL");
        assertEquals("ftp://www.google.de", links.get(1), "Wrong URL");
    }
}
