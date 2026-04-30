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
package org.apache.tika.detect;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Collections;
import java.util.List;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.IOUtils;

import org.apache.tika.config.TikaComponent;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * Encoding detector that identifies the character set from a byte-order mark
 * (BOM) at the start of the stream.  Returns a single {@link EncodingResult.ResultType#DECLARATIVE}
 * result when a BOM is found — a BOM is an explicit in-band declaration of encoding
 * and takes priority over all statistical or structural inference.
 *
 * <p>SPI-loaded first in the default encoding-detector chain so that BOM evidence
 * reaches the meta arbiter ({@code JunkFilterEncodingDetector}) before any
 * statistical detector runs.  {@code MojibusterEncodingDetector} strips the BOM
 * from its own probe independently to ensure consistent model inference (BOMs are
 * excluded from training data).</p>
 *
 * @since Apache Tika 0.x (moved to org.apache.tika.detect in 4.0)
 */
@TikaComponent(spi = false)
public class BOMDetector implements EncodingDetector {

    private static final ByteOrderMark[] BOMS =
            //order matters -- have to try the 32 before the 16
            new ByteOrderMark[] {
                    ByteOrderMark.UTF_8,
                    ByteOrderMark.UTF_32BE,
                    ByteOrderMark.UTF_32LE,
                    ByteOrderMark.UTF_16BE,
                    ByteOrderMark.UTF_16LE
            };
    private static final Charset[] CHARSETS = new Charset[BOMS.length];

    private static final int MIN_BYTES = 2;
    private static final int MAX_BYTES = 4;

    static {
        for (int i = 0; i < BOMS.length; i++) {
            try {
                CHARSETS[i] = Charset.forName(BOMS[i].getCharsetName());
            } catch (UnsupportedCharsetException e) {
                //log it
            }
        }
    }

    @Override
    public List<EncodingResult> detect(TikaInputStream tis, Metadata metadata,
                                       ParseContext parseContext) throws IOException {
        tis.mark(MAX_BYTES);
        byte[] bytes = new byte[MAX_BYTES];
        try {
            int numRead = IOUtils.read(tis, bytes);
            if (numRead < MIN_BYTES) {
                return Collections.emptyList();
            } else if (numRead < MAX_BYTES) {
                byte[] tmpBytes = new byte[numRead];
                System.arraycopy(bytes, 0, tmpBytes, 0, numRead);
                bytes = tmpBytes;
            }
        } finally {
            tis.reset();
        }
        for (int i = 0; i < BOMS.length; i++) {
            ByteOrderMark bom = BOMS[i];
            if (startsWith(bom, bytes) && CHARSETS[i] != null) {
                return List.of(new EncodingResult(CHARSETS[i], 1.0f,
                        CHARSETS[i].name(), EncodingResult.ResultType.DECLARATIVE));
            }
        }
        return Collections.emptyList();
    }

    private boolean startsWith(ByteOrderMark bom, byte[] bytes) {
        byte[] bomBytes = bom.getBytes();
        if (bytes.length < bomBytes.length) {
            return false;
        }
        for (int i = 0; i < bomBytes.length; i++) {
            if (bomBytes[i] != bytes[i]) {
                return false;
            }
        }
        return true;
    }
}
