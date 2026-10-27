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
package org.apache.tika.parser.geo.topic.gazetteer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

public class GeoGazetteerClientTest {

    @Test
    public void testParseLocations() throws Exception {
        // lucene-geo-gazetteer sends coordinates as numbers, and fields Location doesn't have
        String json = "{\"Pasadena\":[{\"name\":\"Pasadena\",\"countryCode\":\"US\",\"admin1Code\":\"CA\","
                + "\"admin2Code\":\"037\",\"latitude\":34.14778,\"longitude\":-118.14452,\"population\":141029}],"
                + "\"Nowhere\":[]}";
        Map<String, List<Location>> locations = GeoGazetteerClient.parseLocations(json);
        assertEquals(2, locations.size());
        assertTrue(locations.get("Nowhere").isEmpty());
        Location loc = locations.get("Pasadena").get(0);
        assertEquals("Pasadena", loc.getName());
        assertEquals("US", loc.getCountryCode());
        assertEquals("CA", loc.getAdmin1Code());
        assertEquals("037", loc.getAdmin2Code());
        assertEquals("34.14778", loc.getLatitude());
        assertEquals("-118.14452", loc.getLongitude());
    }

    @Test
    public void testNullLocations() throws Exception {
        assertNull(GeoGazetteerClient.parseLocations("null"));
    }
}
