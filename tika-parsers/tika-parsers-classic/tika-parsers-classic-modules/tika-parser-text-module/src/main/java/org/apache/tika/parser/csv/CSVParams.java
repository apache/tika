/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.csv;

import java.nio.charset.Charset;

import org.apache.tika.mime.MediaType;

public class CSVParams {

    private MediaType mediaType = null;
    private Character delimiter = null;
    private Charset charset = null;

    CSVParams() {
    }

    CSVParams(MediaType mediaType, Charset charset) {
        this.mediaType = mediaType;
        this.charset = charset;
    }

    CSVParams(MediaType mediaType, Charset charset, Character delimiter) {
        this.mediaType = mediaType;
        this.charset = charset;
        this.delimiter = delimiter;
    }

    public boolean isEmpty() {
        return mediaType == null && delimiter == null && charset == null;
    }

    public boolean isComplete() {
        return mediaType != null && delimiter != null && charset != null;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public void setMediaType(MediaType mediaType) {
        this.mediaType = mediaType;
    }

    public Character getDelimiter() {
        return delimiter;
    }

    public void setDelimiter(Character delimiter) {
        this.delimiter = delimiter;
    }

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }
}
