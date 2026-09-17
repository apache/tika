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
package org.apache.tika.parser.inference;

import java.io.Serializable;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.config.Initializable;
import org.apache.tika.exception.TikaConfigException;

/**
 * The {@code "media"} parse-context block: how audio and video are cut into segments before
 * a MEDIA binding sees them. One grid per document, shared by every binding, so the vectors of
 * one segment land on one chunk. Defaults: 30 s windows, 5 s overlap, 480 segments.
 */
@TikaComponent(name = "media", spi = false)
public class MediaConfig implements Serializable, Initializable {

    private static final long serialVersionUID = 1L;

    public static class Segment implements Serializable {
        private static final long serialVersionUID = 1L;
        private int seconds = 30;
        private int overlap = 5;

        public int getSeconds() {
            return seconds;
        }

        public void setSeconds(int seconds) {
            this.seconds = seconds;
        }

        /** Seconds each segment extends past the next one's start; the longest event kept whole. */
        public int getOverlap() {
            return overlap;
        }

        public void setOverlap(int overlap) {
            this.overlap = overlap;
        }
    }

    private Segment segment = new Segment();
    private int maxSegments = 480;

    public Segment getSegment() {
        return segment;
    }

    public void setSegment(Segment segment) {
        this.segment = segment == null ? new Segment() : segment;
    }

    /** Segments per document; the rest of the file is not embedded. -1 for no limit. */
    public int getMaxSegments() {
        return maxSegments;
    }

    public void setMaxSegments(int maxSegments) {
        this.maxSegments = maxSegments;
    }

    /** Runs at config load and per request; the task checks again for a programmatic block. */
    @Override
    public void initialize() throws TikaConfigException {
        if (segment.seconds <= 0 || segment.overlap < 0 || segment.overlap >= segment.seconds) {
            throw new TikaConfigException("media.segment needs seconds > overlap >= 0, not "
                    + segment.seconds + "/" + segment.overlap);
        }
        if (maxSegments == 0 || maxSegments < -1) {
            throw new TikaConfigException("media.maxSegments must be -1 (no limit) or positive, "
                    + "not " + maxSegments);
        }
    }
}
