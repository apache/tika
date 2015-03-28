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


package org.apache.tika.detect;

import org.apache.tika.mime.MediaType;

public class NNTrainedModelBuilder {
	private MediaType type;

	private int numOfInputs;
	private int numOfHidden;
	private int numOfOutputs;

	private float[] params;

	public MediaType getType() {
		return this.type;
	}

	public int getNumOfInputs() {
		return numOfInputs;
	}

	public int getNumOfHidden() {
		return numOfHidden;
	}

	public int getNumOfOutputs() {
		return numOfOutputs;
	}

	public float[] getParams() {
		return params;
	}

	public void setType(final MediaType type) {
		this.type = type;
	}

	public void setNumOfInputs(final int numOfInputs) {
		this.numOfInputs = numOfInputs;
	}

	public void setNumOfHidden(final int numOfHidden) {
		this.numOfHidden = numOfHidden;
	}

	public void setNumOfOutputs(final int numOfOutputs) {
		this.numOfOutputs = numOfOutputs;
	}

	public void setParams(float[] params) {
		this.params = params;
	}

	public NNTrainedModel build() {
		return new NNTrainedModel(numOfInputs, numOfHidden, numOfOutputs,
				params);
	}
}
