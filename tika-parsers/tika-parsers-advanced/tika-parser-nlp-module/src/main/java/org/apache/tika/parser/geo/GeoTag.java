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

package org.apache.tika.parser.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.tika.parser.geo.gazetteer.Location;

public class GeoTag {
    Location location = new Location();
    List<GeoTag> alternatives = new ArrayList<GeoTag>();

    public void setMain(String name, String longitude, String latitude) {
        this.location.setName(name);
        this.location.setLatitude(longitude);
        this.location.setLongitude(latitude);

    }

    public void addAlternative(GeoTag geotag) {
        alternatives.add(geotag);
    }

    /*
     * Store resolved geoName entities in a GeoTag
     *
     * @param resolvedGeonames resolved entities
     *
     * @param bestNER best name entity among all the extracted entities for the
     * input stream
     */
    public void toGeoTag(Map<String, List<Location>> resolvedGeonames, String bestNER) {

        for (String key : resolvedGeonames.keySet()) {
            List<Location> cur = resolvedGeonames.get(key);
            if (key.equals(bestNER)) {
                this.location = cur.get(0);

            } else {
                GeoTag alter = new GeoTag();
                alter.location = cur.get(0);
                this.addAlternative(alter);
            }
        }
    }
}
