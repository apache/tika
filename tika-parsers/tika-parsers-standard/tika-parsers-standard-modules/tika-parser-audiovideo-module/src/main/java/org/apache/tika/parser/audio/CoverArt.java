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

import java.util.List;

import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Picks the picture that stands for an audio file among its embedded
 * pictures. That picture is emitted as a
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

    private CoverArt() {
    }

    /**
     * Returns the index of the picture to mark as the thumbnail: the first
     * front cover, or the first picture if there is no front cover.
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
        return front >= 0 ? front : 0;
    }

    /**
     * The resource type of the picture at the given index.
     */
    public static TikaCoreProperties.EmbeddedResourceType resourceType(int index,
                                                                       int thumbnailIndex) {
        return index == thumbnailIndex ? TikaCoreProperties.EmbeddedResourceType.THUMBNAIL
                : TikaCoreProperties.EmbeddedResourceType.INLINE;
    }
}
