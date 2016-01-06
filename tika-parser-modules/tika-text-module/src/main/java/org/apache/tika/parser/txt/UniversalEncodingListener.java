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
package org.apache.tika.parser.txt;

import java.nio.charset.Charset;

import org.apache.tika.detect.TextStatistics;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.utils.CharsetUtils;
import org.mozilla.universalchardet.CharsetListener;
import org.mozilla.universalchardet.Constants;
import org.mozilla.universalchardet.UniversalDetector;

/**
 * Helper class used by {@link UniversalEncodingDetector} to access the
 * <code>juniversalchardet</code> detection logic.
 */
class UniversalEncodingListener implements CharsetListener {

    private static final String CHARSET_ISO_8859_1 = "ISO-8859-1";

    private static final String CHARSET_ISO_8859_15 = "ISO-8859-15";

    private final TextStatistics statistics = new TextStatistics();

    private final UniversalDetector detector = new UniversalDetector(this);

    private String hint = null;

    private Charset charset = null;

    public UniversalEncodingListener(Metadata metadata) {
        MediaType type = MediaType.parse(metadata.get(Metadata.CONTENT_TYPE));
        if (type != null) {
            hint = type.getParameters().get("charset");
        }
        if (hint == null) {
            hint = metadata.get(Metadata.CONTENT_ENCODING);
        }
    }

    public void report(String name) {
        if (Constants.CHARSET_WINDOWS_1252.equals(name)) {
            if (hint != null) {
                // Use the encoding hint when available
                name = hint;
            } else if (statistics.count('\r') == 0) {
                // If there are no CR(LF)s, then the encoding is more
                // likely to be ISO-8859-1(5) than windows-1252
                if (statistics.count(0xa4) > 0) { // currency/euro sign
                    // The general currency sign is hardly ever used in
                    // ISO-8859-1, so it's more likely that we're dealing
                    // with ISO-8859-15, where the character is used for
                    // the euro symbol, which is more commonly used.
                    name = CHARSET_ISO_8859_15;
                } else {
                    name = CHARSET_ISO_8859_1;
                }
            }
        }
        try {
            this.charset = CharsetUtils.forName(name);
        } catch (Exception e) {
            // ignore
        }
    }

    public boolean isDone() {
        return detector.isDone();
    }

    public void handleData(byte[] buf, int offset, int length) {
        statistics.addData(buf, offset, length);
        detector.handleData(buf, offset, length);
    }

    public Charset dataEnd() {
        detector.dataEnd();
        if (charset == null && statistics.isMostlyAscii()) {
            report(Constants.CHARSET_WINDOWS_1252);
        }
        return charset;
    }

}