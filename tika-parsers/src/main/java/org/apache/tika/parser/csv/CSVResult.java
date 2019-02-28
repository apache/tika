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

import java.util.Objects;

import org.apache.tika.mime.MediaType;

public class CSVResult implements Comparable<CSVResult> {

    static CSVResult TEXT = new CSVResult(1.0, MediaType.TEXT_PLAIN, '\n');

    private final double confidence;
    private final MediaType mediaType;
    private final char delimiter;

    public CSVResult(double confidence, MediaType mediaType, char delimiter) {
        this.confidence = confidence;
        this.mediaType = mediaType;
        this.delimiter = delimiter;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    public Character getDelimiter() {
        return delimiter;
    }

    /**
     * Sorts in descending order of confidence
     * @param o
     * @return
     */
    @Override
    public int compareTo(CSVResult o) {
        return Double.compare(o.confidence, this.confidence);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CSVResult csvResult = (CSVResult) o;
        return Double.compare(csvResult.confidence, confidence) == 0 &&
                delimiter == csvResult.delimiter &&
                mediaType.equals(csvResult.mediaType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(confidence, mediaType, delimiter);
    }

    public double getConfidence() {
        return confidence;
    }
}
