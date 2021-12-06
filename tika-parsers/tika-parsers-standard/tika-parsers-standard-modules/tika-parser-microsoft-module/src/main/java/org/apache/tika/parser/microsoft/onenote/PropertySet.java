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
import java.util.Objects;

import org.apache.tika.exception.TikaMemoryLimitException;

/**
 * A property set is a collection of properties that specify the attributes of an object (section
 * 2.1.5).
 * <p>
 * The PropertySet structure specifies the format of a property set and is contained by an
 * ObjectSpaceObjectPropSet structure
 * (section 2.6.1). The meaning of each property in the set is specified
 * in [MS-ONE] section 2.1.12.
 * <p>
 * A PropertySet structure can contain references to other objects.
 * <p>
 * The data for a property that is not an object reference is contained in the PropertySet
 * .rgData stream field. The rgData stream is read
 * sequentially beginning with the first property in a PropertySet.rgPrids array until every
 * property has been read.
 * <p>
 * The number of bytes read for each property is specified by the PropertyID.type field.
 * <p>
 * The data for a property that is a reference to one or more objects (section 2.1.5) is
 * contained in the streams within an
 * ObjectSpaceObjectPropSet structure (OIDs.body, OSIDs.body, ContextIDs.body).
 * <p>
 * The streams are read sequentially beginning with the first property in a PropertySet.rgPrids
 * array.
 * <p>
 * If the PropertyID.type field specifies a single object (0x8, 0xA, 0xC), a single CompactID (4
 * bytes) is read from the corresponding
 * stream in the ObjectSpaceObjectPropSet structure.
 * <p>
 * If the PropertyID.type field specifies an array of objects (0x9, 0xB, 0xD), an unsigned
 * integer (4 bytes) is read from the
 * PropertySet.rgDatastream and specifies the number of CompactID structures (section 2.2.2) to
 * read from the corresponding stream in the
 * ObjectSpaceObjectPropSet structure.
 * <p>
 * The streams for each PropertyID.type field are given by the following table.
 * <p>
 * 0x8 (ObjectID, section 2.6.6) - ObjectSpaceObjectPropSet.OIDs.body
 * 0x9 (ArrayOfObjectIDs, section 2.6.6) - ObjectSpaceObjectPropSet.OIDs.body
 * 0xA (ObjectSpaceID, section 2.6.6) - ObjectSpaceObjectPropSet.OSIDs.body
 * 0xB (ArrayOfObjectSpaceIDs, section 2.6.6) - ObjectSpaceObjectPropSet.OSIDs.body
 * 0xC (ContextID, section 2.6.6) - ObjectSpaceObjectPropSet.ContextIDs.body
 * 0xD (ArrayOfContextIDs, section 2.6.6) - ObjectSpaceObjectPropSet.ContextIDs.body
 */

class PropertySet {
    List<PropertyValue> rgPridsData = new ArrayList<>();

    public void print(OneNoteDocument document, OneNotePtr pointer, int indentLevel)
            throws IOException, TikaMemoryLimitException {
        for (PropertyValue child : rgPridsData) {
            child.print(document, pointer, indentLevel);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PropertySet that = (PropertySet) o;
        return Objects.equals(rgPridsData, that.rgPridsData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rgPridsData);
    }

    public List<PropertyValue> getRgPridsData() {
        return rgPridsData;
    }

    public PropertySet setRgPridsData(List<PropertyValue> rgPridsData) {
        this.rgPridsData = rgPridsData;
        return this;
    }
}
