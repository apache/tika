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

public class NNTrainedModel extends TrainedModel {

	private int numOfInputs;
	private int numOfHidden;
	private int numOfOutputs;
	private float[][] Theta1, Theta2;

	public NNTrainedModel(final int nInput, final int nHidden,
			final int nOutput, final float[] nn_params) {
		this.numOfInputs = nInput;
		this.numOfHidden = nHidden;
		this.numOfOutputs = nOutput;
		this.Theta1 = new float[numOfHidden][numOfInputs + 1];
		this.Theta2 = new float[numOfOutputs][numOfHidden + 1];
		populateThetas(nn_params);
	}

	// convert the vector params to the 2 thetas.
	private void populateThetas(final float[] nn_params) {
		int m = this.Theta1.length;
		int n = this.Theta1[0].length;
		int i, j, k = 0;
		for (i = 0; i < n; i++) {
			for (j = 0; j < m; j++) {
				this.Theta1[j][i] = nn_params[k];
				k++;
			}
		}

		m = this.Theta2.length;
		n = this.Theta2[0].length;
		for (i = 0; i < n; i++) {
			for (j = 0; j < m; j++) {
				this.Theta2[j][i] = nn_params[k];
				k++;
			}
		}
	}

	@Override
	public double predict(double[] unseen) {
		// TODO Auto-generated method stub
		return 0;
	}

	/**
	 * The given input vector of unseen is m=(256 + 1) * n= 1 this returns a
	 * prediction probability
	 */
	@Override
	public float predict(float[] unseen) {
		// please ensure the unseen in size consistent with theta1

		int i, j;
		int m = this.Theta1.length;
		int n = this.Theta1[0].length;
		float[] hh = new float[m + 1];// hidden unit summation
		hh[0] = 1;
		for (i = 0; i < m; i++) {
			double h = 0;
			for (j = 0; j < n; j++) {
				h += this.Theta1[i][j] * unseen[j];
			}
			// apply sigmoid
			h = 1.0 / (1.0 + Math.exp(-h));
			hh[i+1] = (float)h;
		}

		m = this.Theta2.length;
		n = this.Theta2[0].length;
		float[] oo = new float[m];
		for (i = 0; i < m; i++) {
			double o = 0;
			for (j = 0; j < n; j++) {
				o += this.Theta2[i][j] * hh[j];
			}
			// apply sigmoid
			o = 1.0 / (1.0 + Math.exp(-o));
			oo[i] = (float)o;
		}

		return oo[0];
	}
}
