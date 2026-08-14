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

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;

/**
 * Dummy detector that always reports {@code application/octet-stream} without even
 * reading the given document stream. Useful as a sentinel default: reporting an honest
 * "unknown" is preferable to a partially-informed guess from an unconfigured detector.
 */
@TikaComponent(spi = false)
public class NoOpDetector implements Detector {

    public static final NoOpDetector INSTANCE = new NoOpDetector();

    private static final long serialVersionUID = 1L;

    @Override
    public MediaType detect(TikaInputStream tis, Metadata metadata, ParseContext parseContext)
            throws IOException {
        // Honor the Detector contract (mark before reading, reset before returning) even though
        // nothing is read: callers may rely on the mark still being valid for their own reset().
        if (tis != null) {
            tis.mark(1);
            tis.reset();
        }
        return MediaType.OCTET_STREAM;
    }
}
