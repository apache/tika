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

import static org.apache.tika.parser.html.charsetdetector.PreScanner.getEncodingFromMeta;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * A class to process the attributes of an HTML meta tag in order to extract a character set.
 * The user should repeatedly call {@link #processAttribute} on each attributes of the tag,
 * then update its current detection result with
 * {@link #updateDetectedCharset(CharsetDetectionResult)}
 * <p>
 * The algorithm implemented is meant to match the one described by the W3C here:
 * https://html.spec.whatwg.org/multipage/parsing.html#prescan-a-byte-stream-to-determine-its-encoding
 */
class MetaProcessor {
    private Set<String> attributeNames = new HashSet<>();
    private boolean gotPragma = false;
    private Boolean needPragma = null; // needPragma can be null, true, or false
    private CharsetDetectionResult detectionResult = CharsetDetectionResult.notFound();

    void updateDetectedCharset(CharsetDetectionResult currentDetectionResult) {
        if (detectionResult.isFound() && needPragma != null && !(needPragma && !gotPragma)) {
            currentDetectionResult.setCharset(detectionResult.getCharset());
        }
    }

    void processAttribute(Map.Entry<String, String> attribute) {
        // Ignore duplicate attributes
        if (attributeNames.contains(attribute.getKey())) {
            return;
        }

        attributeNames.add(attribute.getKey());

        // Handle charset-related attributes
        switch (attribute.getKey()) {
            case "http-equiv":
                if (attribute.getValue().equals("content-type")) {
                    gotPragma = true;
                }
                break;
            case "content":
                String charsetName = getEncodingFromMeta(attribute.getValue());
                if (!detectionResult.isFound() && charsetName != null) {
                    detectionResult.find(charsetName);
                    needPragma = true;
                }
                break;
            case "charset":
                detectionResult.find(attribute.getValue());
                needPragma = false;
                break;
            default: // Ignore non-charset related attributes
        }
    }
}
