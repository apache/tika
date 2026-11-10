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
import java.util.Objects;


/**
 * Properties of an object, contained by an ObjectSpaceObjectPropSet (section 2.6.1); their
 * meanings are in [MS-ONE] 2.1.12. Non-reference data is read sequentially from rgData, sized by
 * PropertyID.type. Object references (section 2.1.5) are read sequentially from the PropSet's
 * streams: OIDs.body for 0x8/0x9, OSIDs.body for 0xA/0xB, ContextIDs.body for 0xC/0xD. Single
 * types (0x8, 0xA, 0xC) read one CompactID; array types first read a 4-byte count from rgData.
 */

class PropertySet {
    List<PropertyValue> rgPridsData = new ArrayList<>();

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
}
