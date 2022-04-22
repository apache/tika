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
package org.apache.tika.parser.mp4;

import java.io.IOException;

import com.drew.imaging.mp4.Mp4Handler;
import com.drew.lang.annotations.NotNull;
import com.drew.lang.annotations.Nullable;
import com.drew.metadata.Metadata;
import com.drew.metadata.mp4.Mp4BoxHandler;
import com.drew.metadata.mp4.Mp4Context;
import org.xml.sax.SAXException;

import org.apache.tika.parser.mp4.boxes.TikaUserDataBox;
import org.apache.tika.sax.XHTMLContentHandler;

public class TikaMp4BoxHandler extends Mp4BoxHandler {

    org.apache.tika.metadata.Metadata tikaMetadata;
    final XHTMLContentHandler xhtml;
    public TikaMp4BoxHandler(Metadata metadata, org.apache.tika.metadata.Metadata tikaMetadata,
                             XHTMLContentHandler xhtml) {
        super(metadata);
        this.tikaMetadata = tikaMetadata;
        this.xhtml = xhtml;
    }

    @Override
    public boolean shouldAcceptBox(@NotNull String type) {
        if (type.equals("udta")) {
            return true;
        }
        return super.shouldAcceptBox(type);
    }

    @Override
    public boolean shouldAcceptContainer(@NotNull String type) {
        return super.shouldAcceptContainer(type);
    }

    @Override
    public Mp4Handler<?> processBox(@NotNull String type, @Nullable byte[] payload, long boxSize,
                                    Mp4Context context)
            throws IOException {
        if (type.equals("udta")) {
            return processUserData(type, payload, boxSize);
        }

        return super.processBox(type, payload, boxSize, context);
    }


    private Mp4Handler<?> processUserData(String type, byte[] payload, long length) throws IOException {
        if (payload == null) {
            return this;
        }
        try {
            new TikaUserDataBox(type, payload, tikaMetadata, xhtml).addMetadata(directory);
        } catch (SAXException e) {
            throw new IOException(e);
        }
        return this;
    }
}
