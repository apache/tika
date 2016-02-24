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
package org.apache.tika.language.detect;

import java.io.IOException;

import org.apache.tika.sax.WriteOutContentHandler;

/**
 * SAX content handler that updates a language detector based on all the
 * received character content.
 *
 * @since Apache Tika 0.10
 */
public class LanguageHandler extends WriteOutContentHandler {

    private final LanguageWriter writer;

    public LanguageHandler() throws IOException {
    	this(new LanguageWriter(LanguageDetector.getDefaultLanguageDetector().loadModels()));
    }
    
    public LanguageHandler(LanguageWriter writer) {
        super(writer);
        
        this.writer = writer;
    }

    public LanguageHandler(LanguageDetector detector) {
        this(new LanguageWriter(detector));
    }

    /**
     * Returns the language detector used by this content handler.
     * Note that the returned detector gets updated whenever new SAX events
     * are received by this content handler.
     *
     * @return language detector
     */
    public LanguageDetector getDetector() {
        return writer.getDetector();
    }

    /**
     * Returns the detected language based on text handled thus far.
     * 
     * @return LanguageResult
     */
    public LanguageResult getLanguage() {
    	return writer.getLanguage();
    }
}
