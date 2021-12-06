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

package org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic;

/**
 * The enumeration of request type.
 */
public enum RequestTypes {
    /**
     * Query access.
     */
    QueryAccess(1),

    /**
     * Query changes.
     */
    QueryChanges(2),

    /**
     * Query knowledge.
     */
    QueryKnowledge(3),

    /**
     * Put changes.
     */
    PutChanges(5),

    /**
     * Query raw storage.
     */
    QueryRawStorage(6),

    /**
     * Put raw storage.
     */
    PutRawStorage(7),

    /**
     * Query diagnostic store info.
     */
    QueryDiagnosticStoreInfo(8),

    /**
     * Allocate extended Guid range .
     */
    AllocateExtendedGuidRange(11);

    private final int intVal;

    RequestTypes(int intVal) {
        this.intVal = intVal;
    }

    public int getIntVal() {
        return intVal;
    }
}
    