/**
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
package org.apache.tika.parser.dif;

import static org.junit.Assert.assertEquals;

import org.apache.tika.TikaTest;
import org.junit.Test;

public class DIFParserTest extends TikaTest {

	@Test
	public void testDifMetadata() throws Exception {
        XMLResult r = getXML("Zamora2010.dif", new DIFParser());
        assertEquals(r.metadata.get("DIF-Entry_ID"),"00794186-48f9-11e3-9dcb-00c0f03d5b7c");
        assertEquals(r.metadata.get("DIF-Metadata_Name"),"ACADIS IDN DIF");

        String content = r.xml;
        assertContains("Title: Zamora 2010 Using Sediment Geochemistry", content);
        assertContains("Southernmost_Latitude : </td><td>78.833", content);
        assertContains("Northernmost_Latitude : </td><td>79.016", content);
        assertContains("Westernmost_Longitude : </td><td>11.64", content);
        assertContains("Easternmost_Longitude : </td><td>13.34", content);
	}
}
