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

    static CSVResult TEXT = new CSVResult(1.0, MediaType.TEXT_PLAIN, null);

    private final double confidence;
    private final MediaType mediaType;
    private final Character delimiter;

    public CSVResult(double confidence, MediaType mediaType, Character delimiter) {
        this.confidence = confidence;
        this.mediaType = mediaType;
        this.delimiter = delimiter;
    }

    public MediaType getMediaType() {
        return mediaType;
    }

    /**
     * @return returns the delimiter or <code>null</code> if the mediatype=text/plain
     */
    public Character getDelimiter() {
        return delimiter;
    }

    /**
     * Sorts in descending order of confidence
     *
     * @param o
     * @return
     */
    @Override
    public int compareTo(CSVResult o) {
        return Double.compare(o.confidence, this.confidence);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CSVResult csvResult = (CSVResult) o;
        return Double.compare(csvResult.confidence, confidence) == 0 &&
                mediaType.equals(csvResult.mediaType) &&
                Objects.equals(delimiter, csvResult.delimiter);
    }

    @Override
    public int hashCode() {
        return Objects.hash(confidence, mediaType, delimiter);
    }

    @Override
    public String toString() {
        return "CSVResult{" + "confidence=" + confidence + ", mediaType=" + mediaType +
                ", delimiter=" + delimiter + '}';
    }

    public double getConfidence() {
        return confidence;
    }
}
