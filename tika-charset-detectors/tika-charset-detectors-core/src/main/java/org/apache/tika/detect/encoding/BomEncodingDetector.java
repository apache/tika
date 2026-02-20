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
import java.io.InputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Encoding detector that identifies a character encoding from a leading
 * byte-order mark (BOM) in the input stream.
 *
 * <p>Recognised BOMs:</p>
 * <ul>
 *   <li>UTF-8: {@code EF BB BF}</li>
 *   <li>UTF-32 LE: {@code FF FE 00 00}</li>
 *   <li>UTF-32 BE: {@code 00 00 FE FF}</li>
 *   <li>UTF-16 LE: {@code FF FE}</li>
 *   <li>UTF-16 BE: {@code FE FF}</li>
 * </ul>
 *
 * <p>Returns {@code null} when no BOM is present.  The stream mark/reset
 * mechanism is used so the BOM bytes remain available for subsequent
 * processing.</p>
 *
 * @since Apache Tika 3.2
 */
@TikaComponent(name = "bom-encoding-detector", spi = false)
public class BomEncodingDetector implements EncodingDetector, Serializable {

    private static final long serialVersionUID = 1L;

    @Override
    public Charset detect(TikaInputStream input, Metadata metadata,
                          ParseContext context) throws IOException {
        if (input == null) {
            return null;
        }

        InputStream stream = input;
        if (!stream.markSupported()) {
            return null;
        }

        stream.mark(4);
        try {
            byte[] bom = new byte[4];
            int n = stream.read(bom, 0, 4);
            if (n < 2) {
                return null;
            }

            int b0 = bom[0] & 0xFF;
            int b1 = bom[1] & 0xFF;
            int b2 = n > 2 ? bom[2] & 0xFF : -1;
            int b3 = n > 3 ? bom[3] & 0xFF : -1;

            // UTF-32 must be checked before UTF-16 (shares the FF FE prefix)
            if (b0 == 0xFF && b1 == 0xFE && b2 == 0x00 && b3 == 0x00) {
                return Charset.forName("UTF-32LE");
            }
            if (b0 == 0x00 && b1 == 0x00 && b2 == 0xFE && b3 == 0xFF) {
                return Charset.forName("UTF-32BE");
            }
            if (b0 == 0xEF && b1 == 0xBB && b2 == 0xBF) {
                return StandardCharsets.UTF_8;
            }
            if (b0 == 0xFF && b1 == 0xFE) {
                return StandardCharsets.UTF_16LE;
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return StandardCharsets.UTF_16BE;
            }
            return null;
        } finally {
            stream.reset();
        }
    }
}
