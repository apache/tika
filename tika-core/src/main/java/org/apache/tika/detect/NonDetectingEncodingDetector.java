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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.tika.config.Field;
import org.apache.tika.metadata.Metadata;

/**
 * Always returns the charset passed in via the initializer
 */
public class NonDetectingEncodingDetector implements EncodingDetector {
    //would have preferred final, but need mutability for
    //loading via TikaConfig
    private Charset charset = StandardCharsets.UTF_8;

    /**
     * Sets charset to UTF-8.
     */
    public NonDetectingEncodingDetector() {

    }

    public NonDetectingEncodingDetector(Charset charset) {
        this.charset = charset;
    }

    @Override
    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        return charset;
    }

    @Field
    private void setCharset(String charsetName) {
        this.charset = Charset.forName(charsetName);
    }

    public Charset getCharset() {
        return charset;
    }
}
