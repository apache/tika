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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.io.TemporaryResources;

public abstract class TrainedModelDetector implements Detector {
	private final Map<MediaType, TrainedModel> MODEL_MAP = new HashMap<MediaType, TrainedModel>();

	private static final long serialVersionUID = 1L;

	public TrainedModelDetector() {
		loadDefaultModels(getClass().getClassLoader());
	}

	public int getMinLength() {
		return Integer.MAX_VALUE;
	}

	public MediaType detect(InputStream input, Metadata metadata)
			throws IOException {
		// convert to byte-histogram
		if (input != null) {
			input.mark(getMinLength());
			float[] histogram = readByteFrequencies(input);
			// writeHisto(histogram); //on testing purpose
			/*
			 * iterate the map to find out the one that gives the higher
			 * prediction value.
			 */
			Iterator<MediaType> iter = MODEL_MAP.keySet().iterator();
			float threshold = 0.5f;// probability threshold, any value below the
									// threshold will be considered as
									// MediaType.OCTET_STREAM
			float maxprob = threshold;
			MediaType maxType = MediaType.OCTET_STREAM;
			while (iter.hasNext()) {
				MediaType key = iter.next();
				TrainedModel model = MODEL_MAP.get(key);
				float prob = model.predict(histogram);
				if (maxprob < prob) {
					maxprob = prob;
					maxType = key;
				}
			}
			input.reset();
			return maxType;
		}
		return null;
	}

	/**
	 * read the inputstream and build a byte frequence histogram
	 * 
	 * @param input
	 * @return
	 * @throws IOException
	 */
	protected float[] readByteFrequencies(final InputStream input)
			throws IOException {

		ReadableByteChannel inputChannel = null;
		try {
			inputChannel = Channels.newChannel(input);
			// long inSize = inputChannel.size();
			float histogram[] = new float[257];
			histogram[0] = 1;

			// create buffer with capacity of maxBufSize bytes
			ByteBuffer buf = ByteBuffer.allocate(1024 * 5);
			int bytesRead = inputChannel.read(buf); // read into buffer.

			float max = -1;
			while (bytesRead != -1) {

				buf.flip(); // make buffer ready for read

				while (buf.hasRemaining()) {
					byte byt = buf.get();
					int idx = byt;
					idx++;
					if (byt < 0) {
						idx = 256 + idx;
						histogram[idx]++;
					} else {
						histogram[idx]++;
					}
					max = max < histogram[idx] ? histogram[idx] : max;
				}

				buf.clear(); // make buffer ready for writing
				bytesRead = inputChannel.read(buf);
			}

			int i;
			for (i = 1; i < histogram.length; i++) {
				histogram[i] /= max;
				histogram[i] = (float) Math.sqrt(histogram[i]);
			}

			return histogram;
		} finally {
			// inputChannel.close();
		}

	}

	/**
	 * on testing purpose; this method write the histogram vector to a file.
	 * 
	 * @param histogram
	 * @throws IOException
	 */
	private synchronized void writeHisto(final float[] histogram)
			throws IOException {
	        String histPath = new TemporaryResources().createTemporaryFile().getAbsolutePath();
	        Writer writer = new OutputStreamWriter(new FileOutputStream(histPath),"UTF-8");
		int n = histogram.length;// excluding the last one for storing the
									// max value
		for (int i = 0; i < n; i++) {
			writer.write(new StringBuffer().append(histogram[i]).append("\t")
					.toString());
			// writer.write(i + "\t");
		}

		writer.write("\r\n");
		writer.flush();
	}

	public void loadDefaultModels(final File modelFile) {
		FileInputStream in = null;
		try {
			in = new FileInputStream(modelFile);
			loadDefaultModels(in);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(
					"Unable to read the default media type registry", e);
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				throw new RuntimeException(
						"Unable to read the default media type registry", e);
			}
		}
	}

	public abstract void loadDefaultModels(final InputStream modelStream);

	public abstract void loadDefaultModels(final ClassLoader classLoader);

	protected void registerModels(MediaType type, TrainedModel model) {
		MODEL_MAP.put(type, model);
	}

}
