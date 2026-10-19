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
package org.apache.tika.imageprocessing;

import java.io.IOException;

import org.apache.tika.annotation.TikaComponent;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.enricher.ImageCheck;
import org.apache.tika.parser.enricher.ImageProbe;

/**
 * Refuses an image shorter than {@code minBytes} or longer than {@code maxBytes}, by its
 * measured length: the image is not read, so this is the cheapest check to run first. A setting
 * of 0 turns that limit off, and an image whose length is unknown or only declared is admitted. The reasons
 * are {@code file-too-small} and {@code file-too-large}, as the VLM parsers' own byte limits
 * report them; those limits stay, this one is for any engine.
 *
 * @since Apache Tika 4.2
 */
@TikaComponent(name = "byte-size")
public class ByteSizeCheck implements ImageCheck {

    private static final long serialVersionUID = 1L;

    private long minBytes;
    private long maxBytes;

    @Override
    public Verdict check(ImageProbe image, Metadata metadata, ParseContext context)
            throws IOException {
        long length = image.length();
        if (length < 0) {
            return Verdict.admit();
        }
        if (minBytes > 0 && length < minBytes) {
            return Verdict.skip("file-too-small");
        }
        if (maxBytes > 0 && length > maxBytes) {
            return Verdict.skip("file-too-large");
        }
        return Verdict.admit();
    }

    public long getMinBytes() {
        return minBytes;
    }

    public void setMinBytes(long minBytes) {
        if (minBytes < 0) {
            throw new IllegalArgumentException("minBytes must be 0 or more, got: " + minBytes);
        }
        this.minBytes = minBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(long maxBytes) {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("maxBytes must be 0 or more, got: " + maxBytes);
        }
        this.maxBytes = maxBytes;
    }
}
