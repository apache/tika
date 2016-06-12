/**
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
package org.apache.tika.parser.recognition.tf;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.tika.config.Field;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.recognition.ObjectRecogniser;
import org.apache.tika.parser.recognition.RecognisedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/**
 * This is an implementation of {@link ObjectRecogniser} powered by <a href="http://www.tensorflow.org"> Tensorflow <a/>
 *  convolutional neural network (CNN). This implementation binds to Python API using {@link ExternalParser}.
 * <br/>
 * // NOTE: This is a proof of concept for an efficient implementation using JNI binding to Tensorflow's C++ api.
 *
 * <br/>
 *  <p>
 *      <b>Environment Setup:</b>
 *      <ol>
 *          <li> Python must be available </li>
 *          <li> Tensorflow must be available for import by the python script. <a href="https://www.tensorflow.org/versions/r0.9/get_started/os_setup.html#pip-installation"> Setup Instructions here </a></li>
 *          <li> All dependencies of tensor flow (such as numpy) must also be available. <a href="https://www.tensorflow.org/versions/r0.9/tutorials/image_recognition/index.html#image-recognition">Follow the image recognition guide and make sure it works</a></li>
 *      </ol>
 *  </p>
 * @since Apache Tika 1.14
 */
public class TensorflowImageRecParser extends ExternalParser implements ObjectRecogniser {

    private static final Logger LOG = LoggerFactory.getLogger(TensorflowImageRecParser.class);

    private static final URI defaultScriptUri = URI.create("https://raw.githubusercontent.com/tensorflow/tensorflow/122cdce33e3e0a01a7f82645617317530aa571fb/tensorflow/models/image/imagenet/classify_image.py");
    private static final Set<MediaType> supportedMimes = Collections.singleton(MediaType.image("jpeg"));
    private static final File defaultScriptFile = new File("tensorflow/tf-objectrec.py");
    private static final File defaultModelFile = new File("tensorflow/tf-objectrec-model");
    private static final LineConsumer ignoredLineLogger = new LineConsumer() {
        @Override
        public void consume(String line) {
            LOG.debug(line);
        }
    };

    @Field private URI scriptUri = defaultScriptUri;
    @Field private String executor = "python";
    @Field private File scriptFile = defaultScriptFile;
    @Field private String modelArg = "--model_dir";
    @Field private File modelFile = defaultModelFile;
    @Field private String imageArg = "--image_file";
    @Field private String outPattern = "(.*) \\(score = ([0-9]+\\.[0-9]+)\\)$";
    @Field private String availabilityTestArgs = ""; //when no args are given, the script will test itself!

    private boolean available = false;

    public Set<MediaType> getSupportedMimes() {
        return supportedMimes;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void configure(ParseContext context) throws TikaConfigException {
        super.configure(context);
        try {
            if (!modelFile.exists()) {
                modelFile.getParentFile().mkdirs();
                LOG.warn("Model doesn't exist at {}. Expecting the script to download it.", modelFile);
            }
            if (!scriptFile.exists()) {
                scriptFile.getParentFile().mkdirs();
                LOG.info("GET : {} -> {}", scriptUri, scriptFile);
                DefaultHttpClient httpClient = new DefaultHttpClient();
                HttpGet getMethod = new HttpGet(scriptUri);
                try (BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(scriptFile))) {
                    HttpResponse response = httpClient.execute(getMethod);
                    IOUtils.copy(response.getEntity().getContent(), stream);
                }
                LOG.debug("Downloaded..");
            }
            String[] availabilityCheckArgs = {executor, scriptFile.getAbsolutePath(),
                    modelArg, modelFile.getAbsolutePath(), availabilityTestArgs};
            available = ExternalParser.check(availabilityCheckArgs);
            LOG.debug("Available? {}", available);
            if (!available) {
                return;
            }
            String[] parseCmd = {
                    executor, scriptFile.getAbsolutePath(),
                    modelArg, modelFile.getAbsolutePath(),
                    imageArg, INPUT_FILE_TOKEN,
                    "--out_file", OUTPUT_FILE_TOKEN}; //inserting output token to let external parser parse metadata
            setCommand(parseCmd);
            HashMap<Pattern, String> patterns = new HashMap<>();
            patterns.put(Pattern.compile(outPattern), null);
            setMetadataExtractionPatterns(patterns);
            setIgnoredLineConsumer(ignoredLineLogger);
        } catch (Exception e) {
            throw new TikaConfigException(e.getMessage(), e);
        }
    }

    @Override
    public List<RecognisedObject> recognise(InputStream stream, ContentHandler handler,
                                            Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        Metadata md = new Metadata();
        parse(stream, handler, md, context);
        List<RecognisedObject> objects = new ArrayList<>();
        for (String key: md.names()) {
            double confidence = Double.parseDouble(md.get(key));
            objects.add(new RecognisedObject(key, "eng", key, confidence));
        }
        return objects;
    }
}

