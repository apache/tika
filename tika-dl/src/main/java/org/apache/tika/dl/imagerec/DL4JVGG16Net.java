/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.dl.imagerec;

import org.apache.tika.config.Field;
import org.apache.tika.config.Param;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.recognition.ObjectRecogniser;
import org.apache.tika.parser.recognition.RecognisedObject;
import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.modelimport.keras.trainedmodels.TrainedModelHelper;
import org.deeplearning4j.nn.modelimport.keras.trainedmodels.TrainedModels;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.VGG16ImagePreProcessor;
import org.nd4j.linalg.factory.Nd4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.deeplearning4j.nn.modelimport.keras.trainedmodels.Utils.ImageNetLabels;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DL4JVGG16Net implements ObjectRecogniser {

    private static final Logger LOG = LoggerFactory.getLogger(DL4JVGG16Net.class);
    public static final Set<MediaType> SUPPORTED_MIMES = Collections.singleton(MediaType.image("jpeg"));
    private static final String HOME_DIR = System.getProperty("user.home");
    private static final String BASE_DIR = ".dl4j" + File.separator + "trainedmodels";
    private static String MODEL_DIR = HOME_DIR + File.separator + BASE_DIR;
    private static String MODEL_DIR_PREPROCESSED = MODEL_DIR + File.separator + "tikaPreprocessed" + File.separator;
    private static TrainedModelHelper MODEL_HELPER = new TrainedModelHelper(TrainedModels.VGG16);

    @Field
    private File modelFile = new File(MODEL_DIR_PREPROCESSED + File.separator + "vgg16.zip");

    @Field
    private File locationToSave = new File(MODEL_DIR + File.separator
            + "tikaPreprocessed" + File.separator + "vgg16.zip");

    @Field
    private boolean serialize = true;
    @Field
    private int topN;
    private NativeImageLoader imageLoader = new NativeImageLoader(224, 224, 3);
    private DataNormalization preProcessor = new VGG16ImagePreProcessor();
    private boolean available = false;
    private ComputationGraph model;
    public Set<MediaType> getSupportedMimes() {
        return SUPPORTED_MIMES;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
	public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
	//TODO: what do we want to check here?                                                                                                                                                                                                               
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        try {
            if (serialize) {
                if (locationToSave.exists()) {
                    model = ModelSerializer.restoreComputationGraph(locationToSave);
                    LOG.info("Preprocessed Model Loaded from {}", locationToSave);
                } else {
                    LOG.warn("Preprocessed Model doesn't exist at {}", locationToSave);
                    locationToSave.getParentFile().mkdirs();
                    model = MODEL_HELPER.loadModel();
                    LOG.info("Saving the Loaded model for future use. Saved models are more optimised to consume less resources.");
                    ModelSerializer.writeModel(model, locationToSave, true);
                }
            } else {
                LOG.info("Weight graph model loaded via dl4j Helper functions");
                model = MODEL_HELPER.loadModel();
            }
            available = true;
        } catch (Exception e) {
            available = false;
            LOG.warn(e.getMessage(), e);
            throw new TikaConfigException(e.getMessage(), e);
        }
    }

    @Override
    public List<RecognisedObject> recognise(InputStream stream, ContentHandler handler,
                                            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        INDArray image = imageLoader.asMatrix(stream);
        preProcessor.transform(image);
        INDArray[] output = model.output(false, image);
        return predict(output[0]);
    }
    private List<RecognisedObject> predict(INDArray predictions)
    {
        ArrayList<String> labels;
        labels=ImageNetLabels.getLabels();
        List<RecognisedObject> objects = new ArrayList<>();
        int[] topNPredictions = new int[topN];
        float[] topNProb = new float[topN];
		String outLabels[]=new String[topN];
        //brute force collect top N
        int i = 0;
        for (int batch = 0; batch < predictions.size(0); batch++) {
            INDArray currentBatch = predictions.getRow(batch).dup();
            while (i < topN) {
                topNPredictions[i] = Nd4j.argMax(currentBatch, 1).getInt(0, 0);
                topNProb[i] = currentBatch.getFloat(batch, topNPredictions[i]);
                currentBatch.putScalar(0, topNPredictions[i], 0);
                outLabels[i]= labels.get(topNPredictions[i]);
                objects.add(new RecognisedObject(outLabels[i], "eng", outLabels[i], topNProb[i]));
                i++;
            }
        }
        return objects;
    }
}
