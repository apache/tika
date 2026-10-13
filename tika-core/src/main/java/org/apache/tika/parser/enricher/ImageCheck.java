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
package org.apache.tika.parser.enricher;

import java.io.IOException;
import java.io.Serializable;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * A test an image passes before a text recognizer is asked to read it: big enough, not blank.
 * Checks run in the order a {@code "text-recognizers"} entry lists them, cheapest first, and
 * the first to refuse ends the chain; the engine never sees the image and the refusal is
 * recorded as {@code skipped:<reason>} in {@link
 * org.apache.tika.metadata.TikaCoreProperties#OCR_WARNINGS}. A check that fails, or cannot
 * judge, admits the image: a gate is never a reason to lose text.
 * <p>
 * Implementations are named {@code @TikaComponent}s, so a deployment lists them by name; keep
 * pixel work out of {@code tika-core} by putting it in the implementing module and sharing
 * what a check decodes through {@link ImageProbe#memo}.
 *
 * @since Apache Tika 4.2
 */
public interface ImageCheck extends Serializable {

    /**
     * @param image    the image and what earlier checks have already learned about it
     * @param metadata the metadata of the image the engine would be handed
     * @param context  the parse context
     */
    Verdict check(ImageProbe image, Metadata metadata, ParseContext context) throws IOException;

    /**
     * Whether the image goes to the engine; a refusal carries a short, stable reason.
     *
     * @param admitted false to keep the image from the engine
     * @param reason   why, when refused: {@code blank}, {@code image-too-small}, ...
     */
    record Verdict(boolean admitted, String reason) {

        private static final Verdict ADMIT = new Verdict(true, null);

        public static Verdict admit() {
            return ADMIT;
        }

        public static Verdict skip(String reason) {
            return new Verdict(false, reason);
        }
    }
}
