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
package org.apache.tika.parser.microsoft.ooxml;

import java.util.Map;

import org.xml.sax.SAXException;

/**
 * Tracks the lifecycle of picture elements (PIC, PICT, BLIP, IMAGEDATA, cNvPr)
 * during OOXML SAX parsing and emits embeddedPicRef callbacks when the picture
 * scope closes.
 * <p>
 * This class has no Tika dependencies and could be contributed to POI.
 */
class OOXMLPictureTracker {

    private final Map<String, String> linkedRelationships;
    private final XWPFBodyContentsHandler bodyContentsHandler;

    private boolean inPic = false;
    private boolean inPict = false;
    private String picDescription = null;
    private String picRId = null;

    OOXMLPictureTracker(Map<String, String> linkedRelationships,
            XWPFBodyContentsHandler bodyContentsHandler) {
        this.linkedRelationships = linkedRelationships;
        this.bodyContentsHandler = bodyContentsHandler;
    }

    boolean isInPic() {
        return inPic;
    }

    boolean isInPict() {
        return inPict;
    }

    void startPic() {
        inPic = true;
    }

    void startPict() {
        inPict = true;
    }

    void setBlipRId(String rId) {
        picRId = rId;
    }

    void setDescription(String description) {
        picDescription = description;
    }

    void setImageDataRId(String rId) {
        picRId = rId;
    }

    void setImageDataDescription(String description) {
        picDescription = description;
    }

    /**
     * Called at end of PIC or PICT element. Resolves the filename from
     * the relationship map and emits the embeddedPicRef callback.
     */
    void endPicture() throws SAXException {
        String picFileName = null;
        if (picRId != null) {
            picFileName = linkedRelationships.get(picRId);
        }
        bodyContentsHandler.embeddedPicRef(picFileName, picDescription);
        picDescription = null;
        picRId = null;
        inPic = false;
        inPict = false;
    }
}
