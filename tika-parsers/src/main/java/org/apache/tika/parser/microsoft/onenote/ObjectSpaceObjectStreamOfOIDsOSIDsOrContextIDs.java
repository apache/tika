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

class ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs {
    long count; // 24 bits
    long extendedStreamsPresent;
    long osidsStreamNotPresent;
    List<CompactID> data = new ArrayList<>();

    public long getCount() {
        return count;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs setCount(long count) {
        this.count = count;
        return this;
    }

    public long getExtendedStreamsPresent() {
        return extendedStreamsPresent;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs setExtendedStreamsPresent(
            long extendedStreamsPresent) {
        this.extendedStreamsPresent = extendedStreamsPresent;
        return this;
    }

    public long getOsidsStreamNotPresent() {
        return osidsStreamNotPresent;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs setOsidsStreamNotPresent(
            long osidsStreamNotPresent) {
        this.osidsStreamNotPresent = osidsStreamNotPresent;
        return this;
    }

    public List<CompactID> getData() {
        return data;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs setData(List<CompactID> data) {
        this.data = data;
        return this;
    }
}
