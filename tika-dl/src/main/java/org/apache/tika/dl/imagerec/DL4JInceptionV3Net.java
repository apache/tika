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
package org.apache.tika.dl.imagerec;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.tika.config.Field;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.recognition.ObjectRecogniser;
import org.apache.tika.parser.recognition.RecognisedObject;
import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.modelimport.keras.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.modelimport.keras.UnsupportedKerasConfigurationException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * {@link DL4JInceptionV3Net} is an implementation of {@link ObjectRecogniser}.
 * This object recogniser is powered by <a href="http://deeplearning4j.org">Deeplearning4j</a>.
 * This implementation is pre configured to use <a href="https://arxiv.org/abs/1512.00567"> Google's InceptionV3 model </a> pre trained on
 * ImageNet corpus. The models references in default settings are originally trained and exported from <a href="http://keras.io">Keras </a> and imported using DL4J's importer tools.
 * <p>
 * Although this implementation is made to work out of the box without user attention,
 * for advances users who are interested in tweaking the settings, the following fields are configurable:
 * <ul>
 * <li>{@link #modelWeightsPath}</li>
 * <li>{@link #modelJsonPath}</li>
 * <li>{@link #labelFile}</li>
 * <li>{@link #labelLang}</li>
 * <li>{@link #cacheDir}</li>
 * <li>{@link #imgWidth}</li>
 * <li>{@link #imgHeight}</li>
 * <li>{@link #imgChannels}</li>
 * <li>{@link #minConfidence}</li>
 * </ul>
 * </p>
 *
 * @see ObjectRecogniser
 * @see org.apache.tika.parser.recognition.ObjectRecognitionParser
 * @see org.apache.tika.parser.recognition.tf.TensorflowImageRecParser
 * @see org.apache.tika.parser.recognition.tf.TensorflowRESTRecogniser
 * @since Tika 1.15
 */
public class DL4JInceptionV3Net implements ObjectRecogniser {

    private static final Set<MediaType> MEDIA_TYPES
            = Collections.singleton(MediaType.image("jpeg"));
    private static final Logger LOG = LoggerFactory.getLogger(DL4JInceptionV3Net.class);
    private static final String DEF_WEIGHTS_URL = "https://raw.githubusercontent.com/USCDataScience/dl4j-kerasimport-examples/98ec48b56a5b8fb7d54a2994ce9cb23bfefac821/dl4j-import-example/data/inception-model-weights.h5";
    public static final String DEF_MODEL_JSON = "org/apache/tika/dl/imagerec/inceptionv3-model.json";
    public static final String DEF_LABEL_MAPPING = "org/apache/tika/dl/imagerec/imagenet_incpetionv3_class_index.json";

    /**
     * Cache dir to be used for downloading the weights file.
     * This is used to download the model.
     */
    @Field
    private File cacheDir = new File(".tmp-inception");

    /**
     * Path to a HDF5 file that contains weights of the Keras network
     * that was obtained by training the network on a labelled dataset.
     * <br/>
     * Note: when the value is set to &lt;download&gt;, the default model will be
     * downloaded from {@value #DEF_WEIGHTS_URL}
     */
    @Field
    private String modelWeightsPath = DEF_WEIGHTS_URL;

    /**
     * Path to a JSON file that contains network (graph) structure exported from Keras.
     * <p>
     * <br/>
     * Default is retrieved from {@value DEF_MODEL_JSON}
     */
    @Field
    private String modelJsonPath = DEF_MODEL_JSON;
    /***
     * Path to file that tells how to map node index to human readable label names
     * <br/>
     * The default is retrieved from {@value DEF_LABEL_MAPPING}
     */
    @Field
    private String labelFile = DEF_LABEL_MAPPING;

    /**
     * Language name of the labels.
     * <br/>
     * Default is 'en'
     */
    @Field
    private String labelLang = "en";

    @Field
    private int imgHeight = 299;
    @Field
    private int imgWidth = 299;
    @Field
    private int imgChannels = 3;
    /***
     * Ignores the labels that are below this confidence score
     */
    @Field
    private double minConfidence = 0.005;

    private ComputationGraph graph;
    private NativeImageLoader imageLoader;
    private Map<Integer, String> labelMap;

    @Override
    public Set<MediaType> getSupportedMimes() {
        return MEDIA_TYPES;
    }

    /***
     *
     * @param path path to resolve the file
     * @return File or null
     */
    private File retrieveFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            LOG.warn("File {} not found in local file system." +
                    " Asking the classloader", path);
            URL url = getClass().getClassLoader().getResource(path);
            if (url == null) {
                LOG.debug("Classloader does not knows the file {}", path);
                file = null;
            } else {
                LOG.debug("Class loader knows the file {}", path);
                try {
                    file = cachedDownload(cacheDir, url.toURI());
                } catch (URISyntaxException | IOException e) {
                    LOG.warn(e.getMessage(), e);
                }
            }
        }
        return file;
    }

    private InputStream retrieveResource(String path) throws FileNotFoundException {
        File file = new File(path);
        if (file.exists()) {
            return new FileInputStream(file);
        }
        LOG.warn("File {} not found in local file system. Asking the classloader", path);
        return getClass().getClassLoader().getResourceAsStream(path);
    }

    public static synchronized File cachedDownload(File cacheDir, URI uri)
            throws IOException {

        if ("file".equals(uri.getScheme()) || uri.getScheme() == null) {
            return new File(uri);
        }
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        String[] parts = uri.toASCIIString().split("/");
        File cacheFile = new File(cacheDir, parts[parts.length - 1]);
        File successFlag = new File(cacheFile.getAbsolutePath() + ".success");

        if (cacheFile.exists() && successFlag.exists()) {
            LOG.info("Cache exist at {}. Not downloading it", cacheFile.getAbsolutePath());
        } else {
            if (successFlag.exists()) {
                successFlag.delete();
            }
            LOG.info("Cache doesn't exist. Going to make a copy");
            LOG.info("This might take a while! GET {}", uri);
            FileUtils.copyURLToFile(uri.toURL(), cacheFile, 5000, 60000);
            //restore the success flag again
            FileUtils.write(successFlag,
                    "CopiedAt:" + System.currentTimeMillis(),
                    Charset.defaultCharset());
        }
        return cacheFile;
    }

    @Override
    public void initialize(Map<String, Param> params)
            throws TikaConfigException {
        //STEP 1: resolve weights file, download if necessary
        if (modelWeightsPath.startsWith("http://") || modelWeightsPath.startsWith("https://")) {
            LOG.debug("Config instructed to download the weights file, doing so.");
            try {
                modelWeightsPath = cachedDownload(cacheDir, URI.create(modelWeightsPath)).getAbsolutePath();
            } catch (IOException e) {
                throw new TikaConfigException(e.getMessage(), e);
            }
        } else {
            File modelFile = retrieveFile(modelWeightsPath);
            if (!modelFile.exists()) {
                LOG.error("modelWeights does not exist at :: {}", modelWeightsPath);
                return;
            }
            modelWeightsPath = modelFile.getAbsolutePath();
        }

        //STEP 2: resolve model JSON
        File modelJsonFile = retrieveFile(modelJsonPath);
        if (modelJsonFile == null || !modelJsonFile.exists()) {
            LOG.error("Could not locate file {}", modelJsonPath);
            return;
        }
        modelJsonPath = modelJsonFile.getAbsolutePath();

        //STEP 3: Load labels map
        try (InputStream stream = retrieveResource(labelFile)) {
            this.labelMap = loadClassIndex(stream);
        } catch (IOException | ParseException e) {
            LOG.error("Could not load labels map", e);
            return;
        }

        //STEP 4: initialize the graph
        try {
            this.imageLoader = new NativeImageLoader(imgHeight, imgWidth, imgChannels);
            LOG.info("Going to load Inception network...");
            long st = System.currentTimeMillis();
            this.graph = KerasModelImport.importKerasModelAndWeights(modelJsonPath,
                    modelWeightsPath, false);
            long time = System.currentTimeMillis() - st;
            LOG.info("Loaded the Inception model. Time taken={}ms", time);
        } catch (IOException | InvalidKerasConfigurationException
                | UnsupportedKerasConfigurationException e) {
            throw new TikaConfigException(e.getMessage(), e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler problemHandler) throws TikaConfigException {
        //TODO: what do we want to check here?
    }

    @Override
    public boolean isAvailable() {
        return graph != null;
    }

    /**
     * Pre process image to reduce to make it feedable to inception network
     *
     * @param input Input image
     * @return processed image
     */
    public INDArray preProcessImage(INDArray input) {
        // Transform to [-1.0, 1.0] range
        return input.div(255.0).sub(0.5).mul(2.0);
    }

    /**
     * Loads the class to
     *
     * @param stream label index stream
     * @return Map of integer -> label name
     * @throws IOException    when the stream breaks unexpectedly
     * @throws ParseException when the input doesn't contain a valid JSON map
     */
    public Map<Integer, String> loadClassIndex(InputStream stream)
            throws IOException, ParseException {
        String content = IOUtils.toString(stream);
        JSONObject jIndex = (JSONObject) new JSONParser().parse(content);
        Map<Integer, String> classMap = new HashMap<>();
        for (Object key : jIndex.keySet()) {
            JSONArray names = (JSONArray) jIndex.get(key);
            classMap.put(Integer.parseInt(key.toString()),
                    names.get(names.size() - 1).toString());
        }
        return classMap;
    }

    @Override
    public List<RecognisedObject> recognise(
            InputStream stream, ContentHandler handler, Metadata metadata,
            ParseContext context) throws IOException, SAXException,
            TikaException {
        INDArray image = preProcessImage(imageLoader.asMatrix(stream));
        INDArray scores = graph.outputSingle(image);
        List<RecognisedObject> result = new ArrayList<>();
        for (int i = 0; i < scores.length(); i++) {
            if (scores.getDouble(i) > minConfidence) {
                String label = labelMap.get(i);
                String id = i + "";
                result.add(new RecognisedObject(label, labelLang, id,
                        scores.getDouble(i)));
                LOG.debug("Found Object {}", label);
            }
        }
        return result;
    }
}
