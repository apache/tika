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
package org.apache.tika.ml.junkdetect.tools;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.tika.detect.CompositeEncodingDetector;
import org.apache.tika.detect.DefaultEncodingDetector;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.detect.EncodingDetectorContext;
import org.apache.tika.detect.EncodingResult;
import org.apache.tika.detect.MetaEncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.ml.junkdetect.JunkFilterEncodingDetector;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.quality.TextQualityDetector;

/**
 * Runs the real encoding-detector chain on some bytes and returns what the junk filter would
 * arbitrate ({@link JunkFilterEncodingDetector#candidates}), without arbitrating.  Thread-safe.
 */
final class ChainProbe {

    private static final ThreadLocal<JunkFilterEncodingDetector.Candidates> LAST = new ThreadLocal<>();

    private final CompositeEncodingDetector chain;

    ChainProbe() {
        List<EncodingDetector> ds = new ArrayList<>();
        for (EncodingDetector d : new DefaultEncodingDetector().getDetectors()) {
            if (!(d instanceof JunkFilterEncodingDetector)) {
                ds.add(d);
            }
        }
        if (ds.size() == new DefaultEncodingDetector().getDetectors().size()) {
            throw new IllegalStateException("no junk filter in the default chain; wrong classpath?");
        }
        ds.add(new Recorder());
        chain = new CompositeEncodingDetector(ds);
    }

    /** The detector classes the chain runs before the recorder, for provenance. */
    String describe() {
        StringBuilder sb = new StringBuilder();
        for (EncodingDetector d : chain.getDetectors()) {
            if (!(d instanceof Recorder)) {
                sb.append(sb.length() == 0 ? "" : ",").append(d.getClass().getSimpleName());
            }
        }
        return sb.toString();
    }

    /** The junk filter's candidates for {@code bytes}, or {@code null} if it would see none. */
    JunkFilterEncodingDetector.Candidates candidates(byte[] bytes) throws IOException {
        LAST.remove();
        try (TikaInputStream tis = TikaInputStream.get(bytes)) {
            chain.detect(tis, new Metadata(), new ParseContext());
        }
        JunkFilterEncodingDetector.Candidates c = LAST.get();
        LAST.remove();
        return c;
    }

    private static final class Recorder implements MetaEncodingDetector {
        private static final long serialVersionUID = 1L;
        private final JunkFilterEncodingDetector filter =
                new JunkFilterEncodingDetector((TextQualityDetector) null);

        @Override
        public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                           ParseContext parseContext) throws IOException {
            EncodingDetectorContext ctx = parseContext.get(EncodingDetectorContext.class);
            if (ctx != null && !ctx.getResults().isEmpty()) {
                LAST.set(filter.candidates(tis, ctx));
            }
            return Collections.emptyList();
        }
    }
}
