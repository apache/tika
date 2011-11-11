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
package org.apache.tika.mime;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.tika.detect.MagicDetector;
import org.apache.tika.metadata.Metadata;

/**
 * Defines a magic match.
 */
class MagicMatch implements Clause {

    private final MediaType mediaType;

    private final String type;

    private final String offset;

    private final String value;

    private final String mask;

    private MagicDetector detector = null;

    MagicMatch(
            MediaType mediaType,
            String type, String offset, String value, String mask) {
        this.mediaType = mediaType;
        this.type = type;
        this.offset = offset;
        this.value = value;
        this.mask = mask;
    }

    private synchronized MagicDetector getDetector() {
        if (detector == null) {
            detector = MagicDetector.parse(mediaType, type, offset, value, mask);
        }
        return detector;
    }

    public boolean eval(byte[] data) {
        try {
            return getDetector().detect(
                    new ByteArrayInputStream(data), new Metadata())
                    != MediaType.OCTET_STREAM;
        } catch (IOException e) {
            // Should never happen with a ByteArrayInputStream
            return false;
        }
    }

    public int size() {
        return getDetector().getLength();
    }

    public String toString() {
        return mediaType.toString()
                + " " + type + " " + offset + " " +  value + " " + mask;
    }

}
