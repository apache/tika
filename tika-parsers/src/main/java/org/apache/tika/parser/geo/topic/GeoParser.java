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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import java.util.Collections;

import java.util.HashMap;

import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class GeoParser extends AbstractParser {

	private static final long serialVersionUID = -2241391757440215491L;
	private static final MediaType MEDIA_TYPE = MediaType
			.application("geoTopic");
	private static final Set<MediaType> SUPPORTED_TYPES = Collections
			.singleton(MEDIA_TYPE);

	private static String gazetteerPath = "";
	private GeoParserConfig defaultconfig = new GeoParserConfig();

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext arg0) {
		// TODO Auto-generated method stub
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler,
			Metadata metadata, ParseContext context) throws IOException,
			SAXException, TikaException {

		/*----------------configure this parser by ParseContext Object---------------------*/
		GeoParserConfig localconfig = context.get(GeoParserConfig.class,
				defaultconfig);
		String nerModelPath = localconfig.getNERPath();
		gazetteerPath = localconfig.getGazetterPath();

		/*----------------get locationNameEntities and best nameEntity for the input stream---------------------*/
		NameEntityExtractor extractor = new NameEntityExtractor(nerModelPath);
		extractor.getAllNameEntitiesfromInput(stream);
		extractor.getBestNameEntity();
		ArrayList<String> locationNameEntities = extractor.locationNameEntities;
		String bestner = extractor.bestNameEntity;

		/*----------------build lucene search engine for the gazetteer file, 
		 *------------------------resolve geonames for each ner, store results in a hashmap---------------------*/
		GeoNameResolver resolver = new GeoNameResolver();
		resolver.buildIndex(gazetteerPath);
		HashMap<String, ArrayList<String>> resolvedGeonames = resolver
				.searchGeoName(locationNameEntities);

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

}
