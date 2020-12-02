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

package org.apache.tika.parser.sentiment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

import opennlp.tools.sentiment.SentimentME;
import opennlp.tools.sentiment.SentimentModel;
import org.apache.commons.io.IOUtils;
import org.apache.tika.config.Field;
import org.apache.tika.config.Initializable;
import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * This parser classifies documents based on the sentiment of document.
 * The classifier is powered by Apache OpenNLP's Maximum Entropy Classifier
 */
public class SentimentAnalysisParser extends AbstractParser implements Initializable {

    private static final Set<MediaType> SUPPORTED_TYPES = Collections
            .singleton(MediaType.application("sentiment"));
    private static final Logger LOG = LoggerFactory.getLogger(SentimentAnalysisParser.class);

    public static final String DEF_MODEL = "https://raw.githubusercontent.com/USCDataScience/SentimentAnalysisParser/master/sentiment-models/src/main/resources/edu/usc/irds/sentiment/en-netflix-sentiment.bin";

    private SentimentME classifier;

    /**
     * Path to model path. Default is {@value DEF_MODEL}
     * <p>
     * <br/>
     * The path could be one of the following:
     * <ul>
     * <li>a HTTP or HTTPS URL (Not recommended for production use since no caching is implemented) </li>
     * <li>an absolute or relative path on local file system (recommended for production use in standalone mode)</li>
     * <li>a relative path known to class loader (Especially useful in distributed environments,
     * recommended for advanced users</li>
     * </ul>
     * Note: on conflict: the model from local file system gets the priority
     * over classpath
     */
    @Field
    private String modelPath = DEF_MODEL;

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        LOG.debug("Initializing...");
        if (modelPath == null) {
            throw new TikaConfigException("Parameter 'modelPath' is required but it is not set");
        }
        try {
            URL resolvedUrl = null;
            if (modelPath.startsWith("http://") || modelPath.startsWith("https://")) {
                resolvedUrl = new URL(modelPath);
            } else {
                resolvedUrl = getClass().getClassLoader().getResource(modelPath);
                File file = new File(modelPath);
                if (file.exists()) { // file on filesystem gets higher priority
                    resolvedUrl = file.toURI().toURL();
                }
            }
            if (resolvedUrl == null) {
                throw new TikaConfigException("Model doesn't exists :" + modelPath);
            }
            LOG.info("Sentiment Model is at {}", resolvedUrl);
            long st = System.currentTimeMillis();
            SentimentModel model = new SentimentModel(resolvedUrl);
            long time = System.currentTimeMillis() - st;
            LOG.debug("time taken to load model {}", time);
            classifier = new SentimentME(model);
        } catch (Exception e) {
            LOG.warn("Failed to load sentiment model from {}" + modelPath);
            throw new TikaConfigException(e.getMessage(), e);
        }
    }

    @Override
    public void checkInitialization(InitializableProblemHandler handler) throws TikaConfigException {
        //TODO -- what do we want to check?
    }
    /**
     * Returns the types supported
     *
     * @param context the parse context
     * @return the set of types supported
     */
    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    /**
     * Performs the parse
     *
     * @param stream   the input
     * @param handler  the content handler
     * @param metadata the metadata passed
     * @param context  the context for the parser
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler,
                      Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        if (classifier == null) {
            LOG.warn(getClass().getSimpleName() + " is not configured properly.");
            return;
        }
        String inputString = IOUtils.toString(stream, "UTF-8");
        String sentiment = classifier.predict(inputString);
        metadata.add("Sentiment", sentiment);
    }
}
