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
package org.apache.tika.parser.html.charsetdetector;

import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Last resort detector, that never returns null.
 * Uses ICU4J for sniffing the charset, and uses standard charset aliases in {@link CharsetAliases}
 * to convert the charset name detected by ICU to a java charset.
 * This detector is stateless and a single instance can be used several times for different streams.
 */
public class StandardIcu4JEncodingDetector implements EncodingDetector {
    public static EncodingDetector STANDARD_ICU4J_ENCODING_DETECTOR = new StandardIcu4JEncodingDetector();

    public Charset detect(InputStream input, Metadata metadata) throws IOException {
        CharsetDetector detector = new CharsetDetector();
        detector.enableInputFilter(true); // enabling input filtering (stripping of HTML tags)
        detector.setText(input);
        for (CharsetMatch match : detector.detectAll()) {
            Charset detected = CharsetAliases.getCharsetByLabel(match.getName());
            if (detected != null) return detected;
        }
        // This detector is meant to be used in last resort. It should never return null
        // So if no charset was found, decode the input as simple ASCII.
        // The ASCII charset is guaranteed to be present in all JVMs.
        return StandardCharsets.US_ASCII;
    }
}
