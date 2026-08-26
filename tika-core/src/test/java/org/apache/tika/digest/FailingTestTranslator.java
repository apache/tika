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
package org.apache.tika.digest;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.tika.extractor.EmbeddedStreamTranslator;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

/**
 * Service-registered for tests only. Translates when the metadata carries {@link #MODE}:
 * "upper" upper-cases the bytes; "fail" writes half of them and then throws, the way a
 * translator meets a truncated container; "silent" claims the stream and writes nothing,
 * like PSTEmailStreamTranslator; "closes" writes everything and then closes the stream it
 * was handed, which it is not supposed to do.
 */
public class FailingTestTranslator implements EmbeddedStreamTranslator {

    public static final Property MODE = Property.internalText("test:translator-mode");

    @Override
    public boolean shouldTranslate(TikaInputStream inputStream, Metadata metadata) {
        return metadata.get(MODE) != null;
    }

    @Override
    public void translate(TikaInputStream inputStream, Metadata metadata, OutputStream os)
            throws IOException {
        String mode = metadata.get(MODE);
        if ("silent".equals(mode)) {
            return;
        }
        byte[] all = inputStream.readAllBytes();
        boolean fail = "fail".equals(mode);
        int n = fail ? all.length / 2 : all.length;
        for (int i = 0; i < n; i++) {
            os.write(Character.toUpperCase((char) all[i]));
        }
        if (fail) {
            throw new IOException("translator gave up half way");
        }
        if ("closes".equals(mode)) {
            os.close();
        }
    }
}
