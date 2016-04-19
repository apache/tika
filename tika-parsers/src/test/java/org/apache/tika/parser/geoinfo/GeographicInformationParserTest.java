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

package org.apache.tika.parser.geoinfo;

import static org.junit.Assert.assertEquals;

import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.junit.Test;


public class GeographicInformationParserTest extends TikaTest {

    @Test
    public void testISO19139() throws Exception{
        XMLResult r = getXML("sampleFile.iso19139", new GeographicInformationParser());
        assertEquals("text/iso19139+xml", r.metadata.get(Metadata.CONTENT_TYPE));
        assertEquals("UTF-8", r.metadata.get("CharacterSet"));
        assertEquals("https", r.metadata.get("TransferOptionsOnlineProtocol "));
        assertEquals("browser", r.metadata.get("TransferOptionsOnlineProfile "));
        assertEquals("Barrow Atqasuk ARCSS Plant", r.metadata.get("TransferOptionsOnlineName "));

        assertContains("Barrow Atqasuk ARCSS Plant", r.xml);
        assertContains("<td>GeographicElementWestBoundLatitude</td>\t<td>-157.24</td>", r.xml);
        assertContains("<td>GeographicElementEastBoundLatitude</td>\t<td>-156.4</td>", r.xml);
        assertContains("<td>GeographicElementNorthBoundLatitude</td>\t<td>71.18</td>", r.xml);
        assertContains("<td>GeographicElementSouthBoundLatitude</td>\t<td>70.27</td>", r.xml);

    }

}
