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

class ObjectDeclarationWithRefCount {
    ObjectSpaceObjectPropSet objectRef;
    ObjectDeclarationWithRefCountBody body = new ObjectDeclarationWithRefCountBody();
    long cRef;
    ReadOnly readOnly = new ReadOnly();

    public ObjectSpaceObjectPropSet getObjectRef() {
        return objectRef;
    }

    public ObjectDeclarationWithRefCount setObjectRef(ObjectSpaceObjectPropSet objectRef) {
        this.objectRef = objectRef;
        return this;
    }

    public ObjectDeclarationWithRefCountBody getBody() {
        return body;
    }

    public ObjectDeclarationWithRefCount setBody(ObjectDeclarationWithRefCountBody body) {
        this.body = body;
        return this;
    }

    public long getcRef() {
        return cRef;
    }

    public ObjectDeclarationWithRefCount setcRef(long cRef) {
        this.cRef = cRef;
        return this;
    }

    public ReadOnly getReadOnly() {
        return readOnly;
    }

    public ObjectDeclarationWithRefCount setReadOnly(ReadOnly readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public static class ReadOnly {
        byte[] md5;

        public byte[] getMd5() {
            return md5;
        }

        public ReadOnly setMd5(byte[] md5) {
            this.md5 = md5;
            return this;
        }
    }
}
