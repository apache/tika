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
package org.apache.tika.parser.video;

import java.io.IOException;

import org.xml.sax.SAXException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Pulls the text subtitle tracks out of a media container, each as an embedded
 * {@code text/vtt} document (see {@link SubtitleTrack}). Called by the container's parser
 * alongside its own metadata extraction.
 */
public interface SubtitleExtractor {

    /**
     * Must not disturb the read position of {@code tis}: the caller parses it afterwards.
     *
     * @param containerMetadata the container's metadata, for warnings about skipped tracks
     */
    void extract(TikaInputStream tis, Metadata containerMetadata, XHTMLContentHandler xhtml,
                 ParseContext context) throws IOException, SAXException, TikaException;
}
