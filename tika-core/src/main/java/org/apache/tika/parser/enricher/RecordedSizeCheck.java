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

import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;

/**
 * The {@code _min-width}/{@code _min-height} of a {@code "text-recognizers"} entry as a check:
 * an image whose recorded dimensions fall short in either is refused; one of unknown size is
 * admitted. Reads metadata only.
 *
 * @since Apache Tika 4.2
 */
public class RecordedSizeCheck implements ImageCheck {

    private static final long serialVersionUID = 1L;

    private final int minWidth;
    private final int minHeight;

    public RecordedSizeCheck(int minWidth, int minHeight) {
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    public int getMinWidth() {
        return minWidth;
    }

    public int getMinHeight() {
        return minHeight;
    }

    @Override
    public Verdict check(ImageProbe image, Metadata metadata, ParseContext context) {
        return SizeGatedEnricher.tooSmall(metadata, minWidth, minHeight)
                ? Verdict.skip("image-too-small") : Verdict.admit();
    }
}
