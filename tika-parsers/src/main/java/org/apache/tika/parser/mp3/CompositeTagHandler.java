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
package org.apache.tika.parser.mp3;

/**
 * Takes an array of {@link ID3Tags} in preference order, and when asked for
 * a given tag, will return it from the first {@link ID3Tags} that has it.
 */
public class CompositeTagHandler implements ID3Tags {

    private ID3Tags[] tags;

    public CompositeTagHandler(ID3Tags[] tags) {
        this.tags = tags;
    }

    public boolean getTagsPresent() {
        for (ID3Tags tag : tags) {
            if (tag.getTagsPresent()) {
                return true;
            }
        }
        return false;
    }

    public String getTitle() {
        for (ID3Tags tag : tags) {
            if (tag.getTitle() != null) {
                return tag.getTitle();
            }
        }
        return null;
    }

    public String getArtist() {
        for (ID3Tags tag : tags) {
            if (tag.getArtist() != null) {
                return tag.getArtist();
            }
        }
        return null;
    }

    public String getAlbum() {
        for (ID3Tags tag : tags) {
            if (tag.getAlbum() != null) {
                return tag.getAlbum();
            }
        }
        return null;
    }

    public String getYear() {
        for (ID3Tags tag : tags) {
            if (tag.getYear() != null) {
                return tag.getYear();
            }
        }
        return null;
    }

    public String getComment() {
        for (ID3Tags tag : tags) {
            if (tag.getComment() != null) {
                return tag.getComment();
            }
        }
        return null;
    }

    public String getGenre() {
        for (ID3Tags tag : tags) {
            if (tag.getGenre() != null) {
                return tag.getGenre();
            }
        }
        return null;
    }

    public String getTrackNumber() {
        for (ID3Tags tag : tags) {
            if (tag.getTrackNumber() != null) {
                return tag.getTrackNumber();
            }
        }
        return null;
    }

}
