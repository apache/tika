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

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.tika.parser.geo.topic.GeoParserConfig;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class GeoGazetteerClient {
	private static final String SEARCH_API = "/api/search";
	private static final String SEARCH_PARAM = "s";
	private static final String PING = "/api/ping";

	private static final Logger LOG = LoggerFactory.getLogger(GeoGazetteerClient.class);

	private String url;
	
	/**
	 * Pass URL on which lucene-geo-gazetteer is available - eg. http://localhost:8765/api/search
	 * @param url
	 */
	public GeoGazetteerClient(String url) {
		this.url = url;
	}
	
	public GeoGazetteerClient(GeoParserConfig config) {
		this.url = config.getGazetteerRestEndpoint();
	}
	
	/**
	 * Calls API of lucene-geo-gazetteer to search location name in gazetteer.
	 * @param locations List of locations to be searched in gazetteer
	 * @return Map of input location strings to gazetteer locations
	 */
	public Map<String, List<Location>> getLocations(List<String> locations){
		HttpClient httpClient = new DefaultHttpClient();
		
		try {
			URIBuilder uri = new URIBuilder(url+SEARCH_API);
			for(String loc: locations){
				uri.addParameter(SEARCH_PARAM, loc);
			}
			HttpGet httpGet = new HttpGet(uri.build());
			
			HttpResponse resp = httpClient.execute(httpGet);
			String respJson = IOUtils.toString(resp.getEntity().getContent(), Charsets.UTF_8);
			
			@SuppressWarnings("serial")
			Type typeDef = new TypeToken<Map<String, List<Location>>>(){}.getType();
			
			return new Gson().fromJson(respJson, typeDef);
			
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		
		return null;
	}
	
	/**
	 * Ping lucene-geo-gazetteer API
	 * @return true if API is available else returns false
	 */
	public boolean checkAvail() {
		HttpClient httpClient = new DefaultHttpClient();
		
		try {
			HttpGet httpGet = new HttpGet(url + PING);
			
			HttpResponse resp = httpClient.execute(httpGet);
			if(resp.getStatusLine().getStatusCode() == 200){
				return true;
			}
			
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		
		return false;
	}
	
}
