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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

public class GeoParserConfig implements Serializable {
    private static final long serialVersionUID = -3167692634278575818L;
    private URL nerModelUrl = null;
    private String gazetteerRestEndpoint = null;

    private static final Logger LOG = LoggerFactory.getLogger(GeoParserConfig.class);

    public GeoParserConfig() {
        this.nerModelUrl = GeoParserConfig.class.getResource("en-ner-location.bin");
        init(this.getClass().getResourceAsStream("GeoTopicConfig.properties"));
    }
    
    /**
     * Initialize configurations from property files
     * @param stream InputStream for GeoTopicConfig.properties
     */
    private void init(InputStream stream) {
        if (stream == null) {
            return;
        }
        Properties props = new Properties();

        try {
            props.load(stream);
        } catch (IOException e) {
            LOG.warn("GeoTopicConfig.properties not found in class path");
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException ioe) {
                    LOG.error("Unable to close stream: {}", ioe.getMessage());
                }
            }
        }
        setGazetteerRestEndpoint(props.getProperty("gazetter.rest.api", "http://localhost:8765"));
    }

    public void setNERModelPath(String path) {
        if (path == null)
            return;
        File file = new File(path);
        if (file.isDirectory() || !file.exists()) {
            return;
        }
        try {
            this.nerModelUrl = file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setNerModelUrl(URL url) {
        this.nerModelUrl = url;
    }
    public URL getNerModelUrl() {
        return nerModelUrl;
    }
    /**
     * @return REST endpoint for lucene-geo-gazetteer
     */
    public String getGazetteerRestEndpoint() {
		return gazetteerRestEndpoint;
	}
    /**
     * Configure REST endpoint for lucene-geo-gazetteer
     * @param gazetteerRestEndpoint
     */
    public void setGazetteerRestEndpoint(String gazetteerRestEndpoint) {
		this.gazetteerRestEndpoint = gazetteerRestEndpoint;
	}
}
