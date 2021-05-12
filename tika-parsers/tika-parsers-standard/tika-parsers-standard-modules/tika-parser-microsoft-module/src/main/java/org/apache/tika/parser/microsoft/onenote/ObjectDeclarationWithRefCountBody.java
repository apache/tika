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
    JCID jcid = new JCID(); // if this is a ObjectDeclarationWithRefCountBody, jci = 0x01
    boolean fHasOidReferences;
    boolean hasOsidReferences;
    // the obj is a GUID in the file_data_store_reference
    // for a ObjectDeclarationFileData3RefCountFND
    boolean file_data_store_reference;

    public CompactID getOid() {
        return oid;
    }

    public ObjectDeclarationWithRefCountBody setOid(CompactID oid) {
        this.oid = oid;
        return this;
    }

    public JCID getJcid() {
        return jcid;
    }

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

    public boolean isHasOsidReferences() {
        return hasOsidReferences;
    }

    public ObjectDeclarationWithRefCountBody setHasOsidReferences(boolean hasOsidReferences) {
        this.hasOsidReferences = hasOsidReferences;
        return this;
    }

    public boolean isFile_data_store_reference() {
        return file_data_store_reference;
    }

    public ObjectDeclarationWithRefCountBody setFile_data_store_reference(
            boolean file_data_store_reference) {
        this.file_data_store_reference = file_data_store_reference;
        return this;
    }
}
