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
package org.apache.tika.parser.recognition;

import org.apache.tika.config.Initializable;
import org.apache.tika.config.Param;
import org.apache.tika.exception.TikaConfigException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.recognition.tf.TensorflowImageRecParser;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *  This is a contract for object recognisers used by {@link ObjectRecognitionParser}
 *  @see {@link TensorflowImageRecParser} for an example
 */
public interface ObjectRecogniser  extends Initializable {

    /**
     * The mimes supported by this recogniser
     * @return set of mediatypes
     */
    Set<MediaType> getSupportedMimes();

    /**
     * Is this service available
     * @return {@code true} when the service is available, {@code false} otherwise
     */
    boolean isAvailable();

    /**
     * This is the hook for configuring the recogniser
     * @param params configuration instance in the form of context
     * @throws TikaConfigException when there is an issue with configuration
     */
    void initialize(Map<String, Param> params) throws TikaConfigException;

    /**
     * Recognise the objects in the stream
     * @param stream content stream
     * @param handler tika's content handler
     * @param metadata metadata instance
     * @param context parser context
     * @return List of {@link RecognisedObject}s
     * @throws IOException when an I/O error occurs
     * @throws SAXException when an issue with XML occurs
     * @throws TikaException any generic error
     */
    List<? extends RecognisedObject> recognise(InputStream stream, ContentHandler handler,
                                     Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException;
}
