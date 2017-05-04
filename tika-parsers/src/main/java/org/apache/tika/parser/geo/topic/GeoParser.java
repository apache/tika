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
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.geo.topic.gazetteer.GeoGazetteerClient;
import org.apache.tika.parser.geo.topic.gazetteer.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.TokenNameFinderModel;

public class GeoParser extends AbstractParser {
    private static final long serialVersionUID = -2241391757440215491L;
    private static final Logger LOG = LoggerFactory.getLogger(GeoParser.class);
    private static final MediaType MEDIA_TYPE = 
                                    MediaType.application("geotopic");
    private static final Set<MediaType> SUPPORTED_TYPES = 
                                    Collections.singleton(MEDIA_TYPE);
    
    private GeoParserConfig config = new GeoParserConfig();
    private GeoGazetteerClient gazetteerClient;
    
    private boolean initialized;
    private URL modelUrl;
    private NameFinderME nameFinder;
    private boolean available;

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext parseContext) {
        return SUPPORTED_TYPES;
    }

    /**
     * Initializes this parser
     * @param modelUrl the URL to NER model
     */
    public void initialize(URL modelUrl) {
        try {
          if (this.modelUrl != null && this.modelUrl.toURI().equals(modelUrl.toURI())) {
              return;
          }
        } catch (URISyntaxException e1) {
              throw new RuntimeException(e1.getMessage());
        }
        
        this.modelUrl = modelUrl;
        gazetteerClient = new GeoGazetteerClient(config);
        
        // Check if the NER model is available, and if the
        //  lucene-geo-gazetteer is available
        this.available = modelUrl != null && gazetteerClient.checkAvail();
        
        if (this.available) {
            try {
                TokenNameFinderModel model = new TokenNameFinderModel(modelUrl);
                this.nameFinder = new NameFinderME(model);
            } catch (Exception e) {
                LOG.warn("Named Entity Extractor setup failed: {}", e.getMessage(), e);
                this.available = false;
            }
        }
        initialized = true;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler,
            Metadata metadata, ParseContext context) throws IOException,
            SAXException, TikaException {

        /*----------------configure this parser by ParseContext Object---------------------*/

        this.config = context.get(GeoParserConfig.class, config);
        initialize(this.config.getNerModelUrl());
        if (!isAvailable()) {
            return;
        }
        NameEntityExtractor extractor = null;
        
        try {
            extractor = new NameEntityExtractor(nameFinder);
        } catch (Exception e) {
            LOG.warn("Named Entity Extractor setup failed: {}", e.getMessage(), e);
            return;
        }

        /*----------------get locationNameEntities and best nameEntity for the input stream---------------------*/
        extractor.getAllNameEntitiesfromInput(stream);
        extractor.getBestNameEntity();
        ArrayList<String> locationNameEntities = extractor.locationNameEntities;
        String bestner = extractor.bestNameEntity;

        /*------------------------resolve geonames for each ner, store results in a hashmap---------------------*/
        Map<String, List<Location>> resolvedGeonames = searchGeoNames(locationNameEntities);

        /*----------------store locationNameEntities and their geonames in a geotag, each input has one geotag---------------------*/
        GeoTag geotag = new GeoTag();
        geotag.toGeoTag(resolvedGeonames, bestner);

        /* add resolved entities in metadata */

        metadata.add("Geographic_NAME", geotag.location.getName());
        metadata.add("Geographic_LONGITUDE", geotag.location.getLongitude());
        metadata.add("Geographic_LATITUDE", geotag.location.getLatitude());
        for (int i = 0; i < geotag.alternatives.size(); ++i) {
            GeoTag alter = (GeoTag) geotag.alternatives.get(i);
            metadata.add("Optional_NAME" + (i + 1), alter.location.getName());
            metadata.add("Optional_LONGITUDE" + (i + 1),
                         alter.location.getLongitude());
            metadata.add("Optional_LATITUDE" + (i + 1),
                         alter.location.getLatitude());
        }
    }

    public Map<String, List<Location>> searchGeoNames(
            ArrayList<String> locationNameEntities) {
    	return gazetteerClient.getLocations(locationNameEntities);
    }

    public boolean isAvailable() {
        if (!initialized) {
            initialize(config.getNerModelUrl());
        }
        return this.available;
    }
}
