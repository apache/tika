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

public class ObjectSpaceObjectPropSet {
    ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs oids =
            new ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
    ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs osids =
            new ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
    ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs contextIDs =
            new ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs();
    PropertySet body = new PropertySet();

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs getOids() {
        return oids;
    }

    public ObjectSpaceObjectPropSet setOids(ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs oids) {
        this.oids = oids;
        return this;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs getOsids() {
        return osids;
    }

    public ObjectSpaceObjectPropSet setOsids(ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs osids) {
        this.osids = osids;
        return this;
    }

    public ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs getContextIDs() {
        return contextIDs;
    }

    public ObjectSpaceObjectPropSet setContextIDs(
            ObjectSpaceObjectStreamOfOIDsOSIDsOrContextIDs contextIDs) {
        this.contextIDs = contextIDs;
        return this;
    }

    public PropertySet getBody() {
        return body;
    }

    public ObjectSpaceObjectPropSet setBody(PropertySet body) {
        this.body = body;
        return this;
    }
}
