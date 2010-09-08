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
package org.apache.tika.extractor;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.CountingInputStream;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.SecureContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class AutoContainerExtractor implements ContainerExtractor {

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 2261131045580861514L;
    
    private List<ContainerExtractor> extractors;

    /**
     * Creates an auto-detecting parser instance using the default Tika
     * configuration.
     */
    public AutoContainerExtractor() {
        this(TikaConfig.getDefaultConfig());
    }

    public AutoContainerExtractor(TikaConfig config) {
        this.extractors = config.getContainerExtractors();
    }

    public List<ContainerExtractor> getExtractors() {
        return extractors;
    }

    public void setExtractors(List<ContainerExtractor> extractors) {
        this.extractors = extractors;
    }
    
    
    public boolean isSupported(TikaInputStream input) throws IOException {
        for(ContainerExtractor extractor : extractors) {
            if(extractor.isSupported(input)) {
                return true;
            }
        }
        return false;
    }


    public void extract(TikaInputStream stream, ContainerExtractor recurseExtractor,
                    ContainerEmbededResourceHandler handler) 
             throws IOException, TikaException {
       // Find a suitable extractor
       ContainerExtractor extractor = null;
       for(ContainerExtractor e : extractors) {
          if(e.isSupported(stream)) {
             extractor = e;
             break;
          }
       }
       if(extractor == null) {
          throw new TikaException("Not a supported container format - no extractor found");
       }
       
       // Have the extractor process it for us
       extractor.extract(stream, recurseExtractor, handler);
    }
}
