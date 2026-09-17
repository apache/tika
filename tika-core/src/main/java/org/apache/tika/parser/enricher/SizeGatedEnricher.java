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

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TIFF;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;

/**
 * A {@code text-recognizers} entry's {@code _min-width}/{@code _min-height}: an image whose
 * recorded dimensions ({@code tiff:ImageWidth}, {@code tiff:ImageLength}) fall short in either
 * is not handed to the engine. An image of unknown size is. The engine's own byte gates
 * ({@code minFileSizeToOcr}) are unchanged.
 *
 * @since Apache Tika 4.1
 */
public class SizeGatedEnricher extends ParserDecorator {

    private static final long serialVersionUID = 1L;

    private final int minWidth;
    private final int minHeight;

    public SizeGatedEnricher(Parser parser, int minWidth, int minHeight) {
        super(parser);
        this.minWidth = minWidth;
        this.minHeight = minHeight;
    }

    /** True when the metadata records a width or height below the minimum. */
    public static boolean tooSmall(Metadata metadata, int minWidth, int minHeight) {
        Integer width = metadata.getInt(TIFF.IMAGE_WIDTH);
        Integer height = metadata.getInt(TIFF.IMAGE_LENGTH);
        return (width != null && width < minWidth) || (height != null && height < minHeight);
    }

    public int getMinWidth() {
        return minWidth;
    }

    public int getMinHeight() {
        return minHeight;
    }

    @Override
    public void parse(TikaInputStream tis, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {
        if (tooSmall(metadata, minWidth, minHeight)) {
            return;
        }
        super.parse(tis, handler, metadata, context);
    }

    @Override
    public String getDecorationName() {
        return "Size-gated (" + minWidth + "x" + minHeight + ")";
    }
}
