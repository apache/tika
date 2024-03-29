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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.apache.commons.io.ByteOrderMark;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.detect.EncodingDetector;
import org.apache.tika.metadata.Metadata;

public class BOMDetectorTest extends TikaTest {
    @Test
    public void testBasic() throws Exception {
        EncodingDetector detector = new BOMDetector();
        for (ByteOrderMark bom : new ByteOrderMark[]{
                ByteOrderMark.UTF_8, ByteOrderMark.UTF_16BE,
                ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE
        }) {
            UnsynchronizedByteArrayOutputStream bos = createStream(bom);
            try (BOMInputStream bomInputStream =
                         new BOMInputStream(new UnsynchronizedByteArrayInputStream(bos.toByteArray()),
                                 ByteOrderMark.UTF_8, ByteOrderMark.UTF_32BE, ByteOrderMark.UTF_32LE,
                                 ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_16LE)) {
                assertEquals(bom, bomInputStream.getBOM());
            }
            try (UnsynchronizedByteArrayInputStream is =
                         new UnsynchronizedByteArrayInputStream(bos.toByteArray())) {
                assertEquals(Charset.forName(bom.getCharsetName()), detector.detect(is, new Metadata()));
                int cnt = 0;
                int c = is.read();
                while (c > -1) {
                    cnt++;
                    c = is.read();
                }
                assertEquals(100 + bom.getBytes().length, cnt);
            }
        }
    }

    @Test
    public void testShort() throws Exception {
        EncodingDetector detector = new BOMDetector();
        for (ByteOrderMark bom : new ByteOrderMark[] {
                ByteOrderMark.UTF_8, ByteOrderMark.UTF_16BE, ByteOrderMark.UTF_16LE, ByteOrderMark.UTF_16BE,
                ByteOrderMark.UTF_32LE
        }) {
            byte[] bytes = new byte[3];
            System.arraycopy(bom.getBytes(), 0, bytes, 0, 1);
            bytes[1] = (byte)32;
            bytes[2] = (byte)32;
            try (InputStream is = new UnsynchronizedByteArrayInputStream(bytes)) {
                assertNull(detector.detect(is, new Metadata()));
            }
        }
    }

    private UnsynchronizedByteArrayOutputStream createStream(ByteOrderMark bom) throws IOException {
        UnsynchronizedByteArrayOutputStream bos = new UnsynchronizedByteArrayOutputStream();
        IOUtils.write(bom.getBytes(), bos);
        for (int i = 0; i < 100; i++) {
            bos.write(' ');
        }
        return bos;
    }
}
