/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright owlocationNameEntitieship.
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.external.ExternalParser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class GeoParser extends AbstractParser {

	private static final long serialVersionUID = -2241391757440215491L;
	private static final MediaType MEDIA_TYPE = MediaType
			.application("geotopic");
	private static final Set<MediaType> SUPPORTED_TYPES = Collections
			.singleton(MEDIA_TYPE);
	private GeoParserConfig config = new GeoParserConfig();
	private static final Logger LOG = Logger.getLogger(GeoParser.class
			.getName());

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext parseContext) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler,
			Metadata metadata, ParseContext context) throws IOException,
			SAXException, TikaException {

		/*----------------configure this parser by ParseContext Object---------------------*/
		config = context.get(GeoParserConfig.class, config);
		String nerModelPath = config.getNERPath();

		if (!isAvailable()) {
			return;
		}

		/*----------------get locationNameEntities and best nameEntity for the input stream---------------------*/
		NameEntityExtractor extractor = new NameEntityExtractor(nerModelPath);
		extractor.getAllNameEntitiesfromInput(stream);
		extractor.getBestNameEntity();
		ArrayList<String> locationNameEntities = extractor.locationNameEntities;
		String bestner = extractor.bestNameEntity;

		/*------------------------resolve geonames for each ner, store results in a hashmap---------------------*/
		HashMap<String, ArrayList<String>> resolvedGeonames = searchGeoNames(locationNameEntities);

		/*----------------store locationNameEntities and their geonames in a geotag, each input has one geotag---------------------*/
		GeoTag geotag = new GeoTag();
		geotag.toGeoTag(resolvedGeonames, bestner);

		/* add resolved entities in metadata */

		metadata.add("Geographic_NAME", geotag.Geographic_NAME);
		metadata.add("Geographic_LONGITUDE", geotag.Geographic_LONGTITUDE);
		metadata.add("Geographic_LATITUDE", geotag.Geographic_LATITUDE);
		for (int i = 0; i < geotag.alternatives.size(); ++i) {
			GeoTag alter = (GeoTag) geotag.alternatives.get(i);
			metadata.add("Optional_NAME" + (i + 1), alter.Geographic_NAME);
			metadata.add("Optional_LONGITUDE" + (i + 1),
					alter.Geographic_LONGTITUDE);
			metadata.add("Optional_LATITUDE" + (i + 1),
					alter.Geographic_LATITUDE);
		}
	}

	public HashMap<String, ArrayList<String>> searchGeoNames(
			ArrayList<String> locationNameEntities) throws ExecuteException,
			IOException {
		CommandLine cmdLine = new CommandLine("lucene-geo-gazetteer");
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		cmdLine.addArgument("-s");
		for (String name : locationNameEntities) {
			cmdLine.addArgument(name);
		}

		LOG.fine("Executing: " + cmdLine);
		DefaultExecutor exec = new DefaultExecutor();
		exec.setExitValue(0);
		ExecuteWatchdog watchdog = new ExecuteWatchdog(60000);
		exec.setWatchdog(watchdog);
		PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
		exec.setStreamHandler(streamHandler);
		int exitValue = exec.execute(cmdLine,
				EnvironmentUtils.getProcEnvironment());
		String outputJson = outputStream.toString("UTF-8");
		JSONArray json = (JSONArray) JSONValue.parse(outputJson);

		HashMap<String, ArrayList<String>> returnHash = new HashMap<String, ArrayList<String>>();
		for (int i = 0; i < json.size(); i++) {
			JSONObject obj = (JSONObject) json.get(i);
			for (Object key : obj.keySet()) {
				String theKey = (String) key;
				JSONArray vals = (JSONArray) obj.get(theKey);
				ArrayList<String> stringVals = new ArrayList<String>(
						vals.size());
				for (int j = 0; j < vals.size(); j++) {
					String val = (String) vals.get(j);
					stringVals.add(val);
				}

				returnHash.put(theKey, stringVals);
			}
		}

		return returnHash;

	}

	public boolean isAvailable() {
		return ExternalParser.check(new String[] { "lucene-geo-gazetteer",
				"--help" }, -1)
				&& config.getNERPath() != null
				&& !config.getNERPath().equals("");
	}

}
