/* Copyright 2016 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.wordperfect;

import static org.junit.Assert.assertEquals;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;

/**
 * Junit test class for the {@link WordPerfectParser}.
 * @author Pascal Essiembre
 */
public class QuattroProTest extends TikaTest {
    //TODO add testWB/testQUATTRO.wb3 if .wb? files get supported
    
    @Test
    public void testQPW() throws Exception {

        XMLResult r = getXML("testQUATTRO.qpw");
        assertEquals("application/x-quattro-pro", 
                r.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals(1, r.metadata.getValues(Metadata.CONTENT_TYPE).length);
        assertContains("This is an example spreadsheet", r.xml);
    }
}
