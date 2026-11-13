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

import java.util.List;


class PropertyValue {

    OneNotePropertyId propertyId;
    // a union: which field is set depends on propertyId's type; the others stay null
    long scalar; // for type 0x2, the bool from the property id
    // ObjectID/ObjectSpaceID/ContextID (one element) or an array of them
    List<CompactID> compactIDs;
    PropertySet propertySet;
    FileChunkReference rawData; // FourBytesOfLengthFollowedByData

    public long getScalar() {
        return scalar;
    }

    public FileChunkReference getRawData() {
        return rawData;
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
