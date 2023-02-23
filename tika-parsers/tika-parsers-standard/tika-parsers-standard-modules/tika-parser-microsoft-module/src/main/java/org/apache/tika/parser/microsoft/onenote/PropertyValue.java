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

package org.apache.tika.parser.microsoft.onenote;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.exception.TikaException;

class PropertyValue {

    private static final Logger LOG = LoggerFactory.getLogger(PropertyValue.class);

    OneNotePropertyId propertyId = new OneNotePropertyId();
    // union of one of these things based on the type of the corresponding PropertyID
    long scalar; // holds a boolean value if type = 0x2, retrieved from header
    // either ObjectID or ObjectSpaceID or ContextID (single value in array)
    // either ArrayOfObjectIDs or ArrayOfObjectSpaceIDs or ArrayOfContextID
    List<CompactID> compactIDs = new ArrayList<>();
    PropertySet propertySet = new PropertySet(); // or used to house a single value
    FileChunkReference rawData = new FileChunkReference(); // FourBytesOfLengthFollowedByData

    public void print(OneNoteDocument document, OneNotePtr pointer, int indentLevel)
            throws IOException, TikaException {
        boolean isRawText =
                true; //std::string(get_property_id_name(propertyId.id)).find("TextE")!=-1;

        long type = propertyId.type;

        if (isRawText) {
            LOG.debug("{}<{}", IndentUtil.getIndent(indentLevel + 1), propertyId);
        }
        if (type > 0 && type <= 6) {
            if (isRawText) {
                LOG.debug("(%d)", scalar);
            }
        } else if (type == 7) {
            OneNotePtr content = new OneNotePtr(pointer);
            content.reposition(rawData);
            if (isRawText) {
                LOG.debug(" [");
                content.dumpHex();
                LOG.debug("]");
            }
        } else if (type == 0x9 || type == 0x8 || type == 0xb || type == 0xc || type == 0xa ||
                type == 0xd) {
            String xtype = "contextID";
            if (type == 0x8 || type == 0x9) {
                xtype = "OIDs";
            }
            if (type == 0xa || type == 0xb) {
                xtype = "OSIDS";
            }
            if (isRawText) {
                if (!compactIDs.isEmpty()) {
                    LOG.debug("");
                }
                for (CompactID compactID : compactIDs) {
                    LOG.debug("{}{}[{}]", IndentUtil.getIndent(indentLevel + 1), xtype, compactID);
                    FileNodePtr where = document.guidToObject.get(compactID.guid);
                    if (where != null) {
                        where.dereference(document).print(document, pointer, indentLevel + 1);
                    }
                }
            }
        } else if (type == 0x10 || type == 0x11) {
            if (isRawText) {
                LOG.debug("SubProperty");
            }
            propertySet.print(document, pointer, indentLevel + 1);
        }
        if (isRawText) {
            LOG.debug(">");
        }
    }

    public OneNotePropertyId getPropertyId() {
        return propertyId;
    }

    public PropertyValue setPropertyId(OneNotePropertyId propertyId) {
        this.propertyId = propertyId;
        return this;
    }

    public long getScalar() {
        return scalar;
    }

    public PropertyValue setScalar(long scalar) {
        this.scalar = scalar;
        return this;
    }

    public List<CompactID> getCompactIDs() {
        return compactIDs;
    }

    public PropertyValue setCompactIDs(List<CompactID> compactIDs) {
        this.compactIDs = compactIDs;
        return this;
    }

    public PropertySet getPropertySet() {
        return propertySet;
    }

    public PropertyValue setPropertySet(PropertySet propertySet) {
        this.propertySet = propertySet;
        return this;
    }

    public FileChunkReference getRawData() {
        return rawData;
    }

    public PropertyValue setRawData(FileChunkReference rawData) {
        this.rawData = rawData;
        return this;
    }

    @Override
    public String toString() {
        return "PropertyValue{" +
                "propertyId=" + propertyId +
                ", scalar=" + scalar +
                ", compactIDs=" + compactIDs +
                ", propertySet=" + propertySet +
                ", rawData=" + rawData +
                '}';
    }
}