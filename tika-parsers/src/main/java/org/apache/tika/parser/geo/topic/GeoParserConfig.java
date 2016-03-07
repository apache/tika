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

import java.io.File;
import java.io.Serializable;
import java.net.URISyntaxException;

public class GeoParserConfig implements Serializable {

	private static final long serialVersionUID = 1L;
	private String nerModelPath = null;

	public GeoParserConfig() {
		try {
			if (GeoParserConfig.class.getResource("en-ner-location.bin") != null) {
				this.nerModelPath = new File(GeoParserConfig.class.getResource(
						"en-ner-location.bin").toURI()).getAbsolutePath();
			}
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
	}

	public void setNERModelPath(String path) {
		if (path == null)
			return;
		File file = new File(path);
		if (file.isDirectory() || !file.exists()) {
			return;
		}
		nerModelPath = path;
	}

	public String getNERPath() {
		return nerModelPath;
	}

}
