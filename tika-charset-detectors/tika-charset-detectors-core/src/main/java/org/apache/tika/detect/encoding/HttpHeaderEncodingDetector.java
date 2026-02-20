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
package org.apache.tika.detect.encoding;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.utils.CharsetUtils;

/**
 * Encoding detector that reads the charset from the {@code Content-Type}
 * metadata field, as populated from an HTTP response header or an embedded
 * document header.
 *
 * <p>This detector is intentionally a pass-through: it does not read the
 * byte stream at all.  It should be placed early in the detection chain so
 * that an explicit header declaration wins over statistical inference.</p>
 *
 * <p>The value of {@link HttpHeaders#CONTENT_TYPE} is parsed for a
 * {@code charset} parameter, e.g.:</p>
 * <pre>
 *   Content-Type: text/html; charset=UTF-8   → UTF-8
 *   Content-Type: text/plain; charset=iso-8859-1 → ISO-8859-1
 *   Content-Type: application/json           → null (no charset param)
 * </pre>
 *
 * @since Apache Tika 3.2
 */
@TikaComponent(name = "http-header-encoding-detector", spi = false)
public class HttpHeaderEncodingDetector implements EncodingDetector, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public Charset detect(TikaInputStream input, Metadata metadata,
                          ParseContext context) throws IOException {
        if (metadata == null) {
            return null;
        }
        String contentType = metadata.get(HttpHeaders.CONTENT_TYPE);
        if (contentType == null || contentType.isEmpty()) {
            return null;
        }
        int charsetIdx = contentType.toLowerCase(java.util.Locale.ROOT).indexOf("charset=");
        if (charsetIdx < 0) {
            return null;
        }
        String charsetName = contentType.substring(charsetIdx + "charset=".length()).trim();
        // Strip any trailing parameters (e.g. "; boundary=...")
        int semi = charsetName.indexOf(';');
        if (semi >= 0) {
            charsetName = charsetName.substring(0, semi).trim();
        }
        // Strip surrounding quotes
        if (charsetName.length() >= 2
                && charsetName.charAt(0) == '"'
                && charsetName.charAt(charsetName.length() - 1) == '"') {
            charsetName = charsetName.substring(1, charsetName.length() - 1);
        }
        return CharsetUtils.forName(charsetName);
    }
}
