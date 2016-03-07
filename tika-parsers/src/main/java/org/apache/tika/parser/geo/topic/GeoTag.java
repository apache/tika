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

package org.apache.tika.parser.geo.topic;

import java.util.ArrayList;
import java.util.HashMap;

public class GeoTag {
	String Geographic_NAME;
	String Geographic_LONGTITUDE;
	String Geographic_LATITUDE;
	ArrayList<GeoTag> alternatives = new ArrayList<GeoTag>();

	public void setMain(String name, String longitude, String latitude) {
		Geographic_NAME = name;
		Geographic_LONGTITUDE = longitude;
		Geographic_LATITUDE = latitude;
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
	public void toGeoTag(HashMap<String, ArrayList<String>> resolvedGeonames,
			String bestNER) {

		for (String key : resolvedGeonames.keySet()) {
			ArrayList<String> cur = resolvedGeonames.get(key);
			if (key.equals(bestNER)) {
				this.Geographic_NAME = cur.get(0);
				this.Geographic_LONGTITUDE = cur.get(1);
				this.Geographic_LATITUDE = cur.get(2);
			} else {
				GeoTag alter = new GeoTag();
				alter.Geographic_NAME = cur.get(0);
				alter.Geographic_LONGTITUDE = cur.get(1);
				alter.Geographic_LATITUDE = cur.get(2);
				this.addAlternative(alter);
			}
		}
	}
}
