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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tika.mime.MediaType;

import static java.nio.charset.StandardCharsets.UTF_8;

public class NNExampleModelDetector extends TrainedModelDetector {
    private static final String EXAMPLE_NNMODEL_FILE = "tika-example.nnmodel";

    private static final long serialVersionUID = 1L;

    private static final Logger log = Logger.getLogger(NNExampleModelDetector.class.getName());

    public NNExampleModelDetector() {
        super();
    }

    public NNExampleModelDetector(final Path modelFile) {
        loadDefaultModels(modelFile);
    }

    public NNExampleModelDetector(final File modelFile) {
        loadDefaultModels(modelFile);
    }

    @Override
    public void loadDefaultModels(InputStream modelStream) {
        BufferedReader bReader = new BufferedReader(new InputStreamReader(modelStream, UTF_8));

        NNTrainedModelBuilder nnBuilder = new NNTrainedModelBuilder();
        String line;
        try {
            while ((line = bReader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("#")) {
                    readDescription(nnBuilder, line);
                } else {
                    readNNParams(nnBuilder, line);
                    // add this model into map of trained models.
                    super.registerModels(nnBuilder.getType(), nnBuilder.build());
                }

            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the default media type registry", e);
        }
    }

    /**
     * this method gets overwritten to register load neural network models
     */
    @Override
    public void loadDefaultModels(ClassLoader classLoader) {
        if (classLoader == null) {
            classLoader = TrainedModelDetector.class.getClassLoader();
        }

        // This allows us to replicate class.getResource() when using
        // the classloader directly
        String classPrefix = TrainedModelDetector.class.getPackage().getName()
                .replace('.', '/')
                + "/";

        // Get the core URL, and all the extensions URLs
        URL modelURL = classLoader.getResource(classPrefix + EXAMPLE_NNMODEL_FILE);
        Objects.requireNonNull(modelURL, "required resource " + classPrefix + EXAMPLE_NNMODEL_FILE + " not found");
        try (InputStream stream = modelURL.openStream()) {
            loadDefaultModels(stream);
        } catch (IOException e) {
            throw new RuntimeException("Unable to read the default media type registry", e);
        }

    }

    /**
     * read the comments where the model configuration is written, e.g the
     * number of inputs, hiddens and output please ensure the first char in the
     * given string is # In this example grb model file, there are 4 elements 1)
     * type 2) number of input units 3) number of hidden units. 4) number of
     * output units.
     */
    private void readDescription(final NNTrainedModelBuilder builder,
                                 final String line) {
        int numInputs;
        int numHidden;
        int numOutputs;
        String[] sarr = line.split("\t");

        try {
            MediaType type = MediaType.parse(sarr[1]);
            numInputs = Integer.parseInt(sarr[2]);
            numHidden = Integer.parseInt(sarr[3]);
            numOutputs = Integer.parseInt(sarr[4]);
            builder.setNumOfInputs(numInputs);
            builder.setNumOfHidden(numHidden);
            builder.setNumOfOutputs(numOutputs);
            builder.setType(type);
        } catch (Exception e) {
            if (log.isLoggable(Level.WARNING)) {
                log.log(Level.WARNING, "Unable to parse the model configuration", e);
            }
            throw new RuntimeException("Unable to parse the model configuration", e);
        }
    }

    /**
     * Read the next line for the model parameters and populate the build which
     * later will be used to instantiate the instance of TrainedModel
     *
     * @param builder
     * @param line
     */
    private void readNNParams(final NNTrainedModelBuilder builder,
                              final String line) {
        String[] sarr = line.split("\t");
        int n = sarr.length;
        float[] params = new float[n];
        try {
            int i = 0;
            for (String fstr : sarr) {
                params[i] = Float.parseFloat(fstr);
                i++;
            }
            builder.setParams(params);
        } catch (Exception e) {
            if (log.isLoggable(Level.WARNING)) {
                log.log(Level.WARNING, "Unable to parse the model configuration", e);
            }
            throw new RuntimeException("Unable to parse the model configuration", e);
        }
    }
}
