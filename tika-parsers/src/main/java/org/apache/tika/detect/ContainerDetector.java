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

import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypes;


/**
 * A detector that knows about the container formats that we support 
 *  (eg POIFS, Zip), and is able to peek inside them to better figure 
 *  out the contents.
 * Delegates to another {@link Detector} (normally {@link MimeTypes})
 *  to handle detection for non container formats. 
 * Should normally be used with a {@link TikaInputStream} to minimise 
 *  the memory usage.
 */
public interface ContainerDetector extends Detector {
    /** 
     * What is the default type returned by this detector, 
     *  when it can't figure out anything more specific?
     */
    public MediaType getDefault();
    
    /**
     * Detect on the generic input stream, if possible. This will
     *  generally just return the default, as normally a 
     *  {@link TikaInputStream} is needed for proper detection.
     */
    public MediaType detect(InputStream input, Metadata metadata)
            throws IOException;
    
    /**
     * Does full, container aware detection for the file of
     *  the appropriate container type.
     */
    public MediaType detect(TikaInputStream input, Metadata metadata)
            throws IOException;
}

