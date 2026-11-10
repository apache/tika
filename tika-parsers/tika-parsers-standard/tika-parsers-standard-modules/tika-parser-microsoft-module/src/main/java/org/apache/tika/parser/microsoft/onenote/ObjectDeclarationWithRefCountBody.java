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

class ObjectDeclarationWithRefCountBody {
    CompactID oid;
    JCID jcid = new JCID(); // spec: jci MUST be 0x01
    boolean fHasOidReferences;
    boolean hasOsidReferences;
    // the object is a file data store GUID (ObjectDeclarationFileData3RefCountFND)
    boolean file_data_store_reference;

    public ObjectDeclarationWithRefCountBody setJcid(JCID jcid) {
        this.jcid = jcid;
        return this;
    }

    public boolean isfHasOidReferences() {
        return fHasOidReferences;
    }

    public ObjectDeclarationWithRefCountBody setfHasOidReferences(boolean fHasOidReferences) {
        this.fHasOidReferences = fHasOidReferences;
        return this;
    }
}
