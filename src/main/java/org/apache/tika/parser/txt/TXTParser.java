/**
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
package org.apache.tika.parser.txt;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.Parser;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;

/**
 * Text parser
 */
public class TXTParser implements Parser {

    public String parse(InputStream stream, Metadata metadata)
            throws IOException, TikaException {
        CharsetDetector detector = new CharsetDetector();

        // Use the declared character encoding, if available
        String encoding = metadata.get(Metadata.CONTENT_ENCODING);
        if (encoding != null) {
            detector.setDeclaredEncoding(encoding);
        }

        // CharsetDetector expects a stream to support marks
        if (!stream.markSupported()) {
            stream = new BufferedInputStream(stream);
        }

        detector.setText(stream);

        CharsetMatch match = detector.detect();
        if (match == null) {
            throw new TikaException("Unable to detect character encoding");
        }

        metadata.set(Metadata.CONTENT_TYPE, "text/plain");
        metadata.set(Metadata.CONTENT_ENCODING, match.getName());
        String language = match.getLanguage();
        if (language != null) {
            metadata.set(Metadata.CONTENT_LANGUAGE, match.getLanguage());
            metadata.set(Metadata.LANGUAGE, match.getLanguage());
        }

        return match.getString();
    }

}
