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
package org.apache.tika.parser.audio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xml.sax.SAXException;

import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.EmbeddedDocumentUtil;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.mp3.ID3Tags;
import org.apache.tika.sax.XHTMLContentHandler;

/**
 * Picks the picture that stands for an audio file among its embedded
 * pictures and sends them all to the embedded document extractor. That
 * picture is emitted as a
 * {@link TikaCoreProperties.EmbeddedResourceType#THUMBNAIL}, like the
 * preview image of the document container formats, so a client can find
 * the representative image of any file the same way; the other pictures
 * are {@link TikaCoreProperties.EmbeddedResourceType#INLINE}. See TIKA-4850.
 */
public final class CoverArt {

    /**
     * The ID3v2 APIC picture type of the front cover, shared by the FLAC
     * and Vorbis picture blocks.
     */
    public static final int FRONT_COVER = 3;

    /**
     * The ID3v2 APIC picture type "Other". Many taggers store the main
     * cover art with this type instead of marking it a front cover.
     */
    public static final int OTHER = 0;

    private CoverArt() {
    }

    /**
     * One embedded picture of an audio file. The type is the ID3v2 APIC
     * picture type, shared by the FLAC and Vorbis picture blocks; mime type
     * and description may be null or empty.
     */
    public record Picture(int type, String mimeType, String description, byte[] data) {

        /**
         * The type as {@link #thumbnailIndex(List)} sees it: a value beyond
         * the ID3 picture type table counts as unknown.
         */
        int normalizedType() {
            return type >= ID3Tags.PICTURE_TYPES.length ? -1 : type;
        }
    }

    /**
     * Returns the index of the picture to mark as the thumbnail: the first
     * front cover; else the first picture whose type is "Other" or unknown,
     * which is where taggers put the main art when they do not classify it,
     * rather than e.g. a back cover or a leaflet that happens to come first;
     * else the first picture.
     *
     * @param pictureTypes the picture types in file order; a negative value
     *                     for a picture whose type is unknown
     * @return the index, or -1 if there are no pictures
     */
    public static int thumbnailIndex(List<Integer> pictureTypes) {
        if (pictureTypes.isEmpty()) {
            return -1;
        }
        int front = pictureTypes.indexOf(FRONT_COVER);
        if (front >= 0) {
            return front;
        }
        for (int i = 0; i < pictureTypes.size(); i++) {
            if (pictureTypes.get(i) <= OTHER) {
                return i;
            }
        }
        return 0;
    }

    /**
     * The resource type of the picture at the given index.
     */
    public static TikaCoreProperties.EmbeddedResourceType resourceType(int index,
                                                                       int thumbnailIndex) {
        return index == thumbnailIndex ? TikaCoreProperties.EmbeddedResourceType.THUMBNAIL
                : TikaCoreProperties.EmbeddedResourceType.INLINE;
    }

    /**
     * Sends the pictures of one audio file to the embedded document
     * extractor: the one {@link #thumbnailIndex(List)} picks as the
     * THUMBNAIL, the others as INLINE pictures. The pictures only become
     * embedded documents, no metadata is recorded on the audio document
     * itself. Call once per file, with all of its pictures, so exactly one
     * of them is the thumbnail.
     */
    public static void extractPictures(List<Picture> pictures, XHTMLContentHandler xhtml,
                                       ParseContext context) throws IOException, SAXException {
        if (pictures.isEmpty()) {
            return;
        }
        List<Integer> pictureTypes = new ArrayList<>();
        for (Picture picture : pictures) {
            pictureTypes.add(picture.normalizedType());
        }
        int thumbnailIndex = thumbnailIndex(pictureTypes);
        EmbeddedDocumentExtractor extractor =
                EmbeddedDocumentUtil.getEmbeddedDocumentExtractor(context);
        for (int i = 0; i < pictures.size(); i++) {
            Picture picture = pictures.get(i);
            Metadata pictureMetadata = Metadata.newInstance(context);
            pictureMetadata.set(TikaCoreProperties.EMBEDDED_RESOURCE_TYPE,
                    resourceType(i, thumbnailIndex).name());
            if (picture.mimeType() != null && !picture.mimeType().isEmpty()) {
                pictureMetadata.set(HttpHeaders.CONTENT_TYPE, picture.mimeType());
            }
            if (picture.description() != null && !picture.description().isEmpty()) {
                pictureMetadata.set(TikaCoreProperties.TITLE, picture.description());
            }
            if (picture.type() >= 0 && picture.type() < ID3Tags.PICTURE_TYPES.length) {
                pictureMetadata.set(TikaCoreProperties.DESCRIPTION,
                        ID3Tags.PICTURE_TYPES[picture.type()]);
            }
            if (extractor.shouldParseEmbedded(pictureMetadata, context)) {
                //the metadata takes the length of the picture from the stream
                try (TikaInputStream pictureStream =
                        TikaInputStream.get(picture.data(), pictureMetadata)) {
                    extractor.parseEmbedded(pictureStream, xhtml, pictureMetadata, context, true);
                }
            }
        }
    }
}
