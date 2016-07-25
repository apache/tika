/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.parser.recognition.tf;

import org.apache.tika.config.Field;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.recognition.ObjectRecogniser;
import org.apache.tika.parser.recognition.RecognisedObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tensor Flow image recogniser which has high performance.
 * This implementation takes addon jar and binds it using reflections without
 * without corrupting classpath with incompatible version of dependencies.
 * <p>
 * The addon jar can be built from https://github.com/thammegowda/tensorflow-grpc-java
 *
 * @since Apache Tika 1.14
 */
public class TensorflowGrpcRecogniser implements ObjectRecogniser, Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(TensorflowGrpcRecogniser.class);
    private static final String LABEL_LANG = "en";
    private static ClassLoader PARENT_CL = TensorflowGrpcRecogniser.class.getClassLoader();

    static {
        while (PARENT_CL.getParent() != null) {
            PARENT_CL = PARENT_CL.getParent(); //move up the heighrarchy until we get the JDK classloader
        }
    }

    @Field
    private String recogniserClass = "edu.usc.irds.tensorflow.grpc.TensorflowObjectRecogniser";

    @Field
    private String host = "localhost";

    @Field
    private int port = 9000;

    @Field(name = "addon", required = true)
    private File addon;

    private boolean available;

    private Object instance;
    private Method recogniseMethod;
    private Method closeMethod;

    @Override
    public Set<MediaType> getSupportedMimes() {
        return TensorflowImageRecParser.SUPPORTED_MIMES;
    }

    @Override
    public boolean isAvailable() {
        return available;
    }

    @Override
    public void initialize(Map<String, Param> params) throws TikaConfigException {
        try {
            if (!addon.exists()) {
                throw new TikaConfigException("File " + addon + " doesnt exists");
            }
            URL[] urls = {addon.getAbsoluteFile().toURI().toURL()};
            URLClassLoader loader = new URLClassLoader(urls, PARENT_CL);
            Class<?> clazz = Class.forName(recogniserClass, true, loader);
            instance = clazz.getConstructor(String.class, int.class)
                    .newInstance(host, port);
            recogniseMethod = clazz.getMethod("recognise", InputStream.class);
            closeMethod = clazz.getMethod("close");
            available = true;
        } catch (Exception e) {
            throw new TikaConfigException(e.getMessage(), e);
        }
    }

    @Override
    public List<RecognisedObject> recognise(InputStream stream,
                                            ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {
        List<RecognisedObject> recObjs = new ArrayList<>();
        try {
            Object result = recogniseMethod.invoke(instance, stream);
            if (result != null) {
                List<Map.Entry<String, Double>> objects = (List<Map.Entry<String, Double>>) result;
                for (Map.Entry<String, Double> object : objects) {
                    RecognisedObject recObj = new RecognisedObject(object.getKey(),
                            LABEL_LANG, object.getKey(), object.getValue());
                    recObjs.add(recObj);
                }
            } else {
                LOG.warn("Result is null");
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            LOG.debug(e.getMessage(), e);
        }
        return recObjs;
    }

    @Override
    public void close() throws IOException {
        if (closeMethod != null) {
            try {
                closeMethod.invoke(instance);
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOG.debug(e.getMessage(), e);
            }
        }
    }
}
