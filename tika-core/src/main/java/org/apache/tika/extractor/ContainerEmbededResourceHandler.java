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
package org.apache.tika.extractor;

import java.io.InputStream;

import org.apache.tika.mime.MediaType;

/**
 * Tika container extractor callback interface.
 * To work with a {@link ContainerExtractor}, your code needs
 *  to implement this interface.
 */
public interface ContainerEmbededResourceHandler {
    /**
     * Called to process an embeded resource within the container.
     * This will be called once per embeded resource within the
     *  container, along with whatever details are available on
     *  the embeded resource.
     *  
     * TODO Don't pass in the input stream, so that if the entry
     *  isn't desired then work isn't done to extract it
     * 
     * @since Apache Tika 0.8
     * @param filename The filename of the embeded resource, if known
     * @param mediaType The media type of the embeded resource, if known
     * @param stream The contents of the embeded resource
     */
    void handle(String filename, MediaType mediaType, InputStream stream);
}
