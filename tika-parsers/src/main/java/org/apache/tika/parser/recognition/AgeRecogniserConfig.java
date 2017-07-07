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

package org.apache.tika.parser.recognition;

import java.net.URL;
import java.util.Map;

import org.apache.tika.config.Param;


/**
 * Stores URL for AgePredictor 
 */
public class AgeRecogniserConfig {

	private String pathClassifyModel = null;
	private String pathClassifyRegression = null;

	public AgeRecogniserConfig(Map<String, Param> params) {

		URL classifyUrl = AgeRecogniserConfig.class.getResource(
				params.get("age.path.classify").getValue().toString());

		if (classifyUrl != null) {
			setPathClassifyModel(classifyUrl.getFile());
		}

		URL regressionUrl = AgeRecogniserConfig.class.getResource(
				params.get("age.path.regression").getValue().toString());

		if (regressionUrl != null) {
			setPathClassifyRegression(regressionUrl.getFile());
		}
	}

	public String getPathClassifyModel() {
		return pathClassifyModel;
	}

	public void setPathClassifyModel(String pathClassifyModel) {
		this.pathClassifyModel = pathClassifyModel;
	}

	public String getPathClassifyRegression() {
		return pathClassifyRegression;
	}

	public void setPathClassifyRegression(String pathClassifyRegression) {
		this.pathClassifyRegression = pathClassifyRegression;
	}
    
    
   
}