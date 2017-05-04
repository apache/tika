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
package org.apache.tika.parser;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingDetector;


/**
 * Abstract base class for parsers that use the AutoDetectReader and need
 * to use the {@link EncodingDetector} configured by {@link TikaConfig}
 */
public abstract class AbstractEncodingDetectorParser extends AbstractParser {


    private EncodingDetector encodingDetector;

    public AbstractEncodingDetectorParser() {
        encodingDetector = new DefaultEncodingDetector();
    }

    public AbstractEncodingDetectorParser(EncodingDetector encodingDetector) {
        this.encodingDetector = encodingDetector;
    }
    /**
     * Look for an EncodingDetetor in the ParseContext.  If it hasn't been
     * passed in, use the original EncodingDetector from initialization.
     *
     * @param parseContext
     * @return
     */
    protected EncodingDetector getEncodingDetector(ParseContext parseContext) {

        EncodingDetector fromParseContext = parseContext.get(EncodingDetector.class);
        if (fromParseContext != null) {
            return fromParseContext;
        }

        return getEncodingDetector();
    }

    public EncodingDetector getEncodingDetector() {
        return encodingDetector;
    }

    public void setEncodingDetector(EncodingDetector encodingDetector) {
        this.encodingDetector = encodingDetector;
    }
}
