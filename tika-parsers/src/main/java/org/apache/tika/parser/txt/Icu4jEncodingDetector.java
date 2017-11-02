/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.txt;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.tika.config.Field;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.CharsetUtils;

public class Icu4jEncodingDetector implements EncodingDetector {

    @Field
    private boolean stripMarkup = false;

    @Field
    private int markLimit = CharsetDetector.DEFAULT_MARK_LIMIT;

    public Charset detect(InputStream input, Metadata metadata)
            throws IOException {
        if (input == null) {
            return null;
        }

        CharsetDetector detector = new CharsetDetector(markLimit);

        String incomingCharset = metadata.get(Metadata.CONTENT_ENCODING);
        String incomingType = metadata.get(Metadata.CONTENT_TYPE);
        if (incomingCharset == null && incomingType != null) {
            // TIKA-341: Use charset in content-type
            MediaType mt = MediaType.parse(incomingType);
            if (mt != null) {
                incomingCharset = mt.getParameters().get("charset");
            }
        }

        if (incomingCharset != null) {
            String cleaned = CharsetUtils.clean(incomingCharset);
            if (cleaned != null) {
                detector.setDeclaredEncoding(cleaned);
            } else {
                // TODO: log a warning?
            }
        }

        // TIKA-341 without enabling input filtering (stripping of tags)
        // short HTML tests don't work well
        detector.enableInputFilter(true);

        detector.setText(input);

        for (CharsetMatch match : detector.detectAll()) {
            try {
                return CharsetUtils.forName(match.getName());
            } catch (Exception e) {
                // ignore
            }
        }

        return null;
    }

    /**
     * Whether or not to attempt to strip html-ish markup
     * from the stream before sending it to the underlying
     * detector.
     *
     * The underlying detector may still apply its own stripping
     * if this is set to <code>false</code>.
     *
     * @param stripMarkup whether or not to attempt to strip markup before
     *                    sending the stream to the underlying detector
     */
    @Field
    public void setStripMarkup(boolean stripMarkup) {
        this.stripMarkup = stripMarkup;
    }

    public boolean getStripMarkup() {
        return stripMarkup;
    }

    /**
     * How far into the stream to read for charset detection.
     * Default is 12000.
     *
     * @param markLimit
     */
    @Field
    public void setMarkLimit(int markLimit) {
        this.markLimit = markLimit;
    }

    public int getMarkLimit() {
        return markLimit;
    }
}
