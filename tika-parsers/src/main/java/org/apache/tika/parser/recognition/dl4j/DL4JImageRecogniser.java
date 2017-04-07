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

package org.apache.tika.parser.recognition.dl4j;

import org.apache.tika.config.Field;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.external.ExternalParser;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class DL4JImageRecogniser extends ExternalParser implements ObjectRecogniser {

    private static final Logger LOG = LoggerFactory.getLogger(DL4JImageRecogniser.class);
    public static final Set<MediaType> SUPPORTED_MIMES = Collections.singleton(MediaType.image("jpeg"));
    private static final LineConsumer IGNORED_LINE_LOGGER = new LineConsumer() {
        @Override
        public void consume(String line) {
            LOG.debug(line);
        }
    };
    private static final String HOME_DIR = System.getProperty("user.home");
    private static final String BASE_DIR = ".dl4j/trainedmodels";
    private static String MODEL_DIR = HOME_DIR + File.separator + BASE_DIR;
    private static String MODEL_DIR_PREPROCESSED = MODEL_DIR + File.separator + "tikaPreprocessed" + File.separator;
    @Field
    private String modelType = "VGG16";
    @Field
    private File modelFile;
    @Field
    private String outPattern = "(.*) \\(score = ([0-9]+\\.[0-9]+)\\)$";
    private File locationToSave;
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
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        try {
            if (modelType.equals("VGG16NOTOP")) {
                throw new TikaConfigException("VGG16NOTOP is not supported right now");
                /*# TODO hookup VGGNOTOP by uncommenting following code once the issue is resolved by dl4j team
                modelFile = new File(MODEL_DIR_PREPROCESSED+File.separator+"vgg16_notop.zip");
				locationToSave= new File(MODEL_DIR+File.separator+"tikaPreprocessed"+File.separator+"vgg16.zip");*/
            } else if (modelType.equals("VGG16")) {
                modelFile = new File(MODEL_DIR_PREPROCESSED + File.separator + "vgg16.zip");
                locationToSave = new File(MODEL_DIR + File.separator + "tikaPreprocessed" + File.separator + "vgg16.zip");
            }
            if (!modelFile.exists()) {
                modelFile.getParentFile().mkdirs();
                LOG.warn("Preprocessed Model doesn't exist at {}", modelFile);
                TrainedModelHelper helper;
                switch (modelType) {
                    case "VGG16NOTOP":
                        helper = new TrainedModelHelper(TrainedModels.VGG16NOTOP);
                        break;
                    case "VGG16":
                        helper = new TrainedModelHelper(TrainedModels.VGG16);
                        break;
                    default:
                        throw new TikaConfigException("Unknown or unsupported model");
                }
                model = helper.loadModel();
                LOG.info("Saving the Loaded model for future use. Saved models are more optimised to consume less resource.");
                ModelSerializer.writeModel(model, locationToSave, true);
                available = true;
            } else {
                model = ModelSerializer.restoreComputationGraph(locationToSave);
                LOG.info("Preprocessed Model Loaded from {}", locationToSave);
                available = true;
            }

            if (!available) {
                return;
            }
            HashMap<Pattern, String> patterns = new HashMap<>();
            patterns.put(Pattern.compile(outPattern), null);
            setMetadataExtractionPatterns(patterns);
            setIgnoredLineConsumer(IGNORED_LINE_LOGGER);
        } catch (Exception e) {
            LOG.warn("exception occured");
            throw new TikaConfigException(e.getMessage(), e);
        }
    }

    @Override
    public List<RecognisedObject> recognise(InputStream stream, ContentHandler handler,
                                            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        NativeImageLoader loader = new NativeImageLoader(224, 224, 3);
        INDArray image = loader.asMatrix(stream);
        DataNormalization scaler = new VGG16ImagePreProcessor();
        scaler.transform(image);
        INDArray[] output = model.output(false, image);
        String modelOutput = TrainedModels.VGG16.decodePredictions(output[0]);
        modelOutput = modelOutput.replace("Predictions for batch  :\n", "");
        modelOutput = modelOutput.replace("%", "");
        String objs[] = modelOutput.split("\n");
        List<RecognisedObject> objects = new ArrayList<>();
        for (String obj : objs) {
            String data[];
            data = obj.split(",");
            double confidence = Double.parseDouble(data[0]);
            objects.add(new RecognisedObject(data[1].trim(), "eng", data[1].trim(), confidence));
        }
        return objects;
    }
}
