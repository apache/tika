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

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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
