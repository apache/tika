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

class RootObjectReference {
    CompactID oidRoot = new CompactID();
    RootObjectReferenceBase rootObjectReferenceBase = new RootObjectReferenceBase();

    public CompactID getOidRoot() {
        return oidRoot;
    }

    public RootObjectReference setOidRoot(CompactID oidRoot) {
        this.oidRoot = oidRoot;
        return this;
    }

    public RootObjectReferenceBase getRootObjectReferenceBase() {
        return rootObjectReferenceBase;
    }

    public RootObjectReference setRootObjectReferenceBase(
            RootObjectReferenceBase rootObjectReferenceBase) {
        this.rootObjectReferenceBase = rootObjectReferenceBase;
        return this;
    }
}
