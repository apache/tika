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
package org.apache.tika.parser.microsoft.ooxml;

import java.io.Closeable;
import java.io.IOException;

import org.apache.poi.openxml4j.opc.OPCPackage;

/**
 * This is a wrapper around OPCPackage that calls revert() instead of close().
 * We added this during the upgrade of POI to 5.x to avoid a warning.
 *
 * TIKA-3663
 */
public class OPCPackageWrapper implements Closeable {

    public static final String PERSON_RELATION = "http://schemas.microsoft.com/office/2017/10/relationships/person";
    public static final String THREADED_COMMENT_RELATION = "http://schemas.microsoft.com/office/2017/10/relationships/threadedComment";

    private final OPCPackage opcPackage;

    public OPCPackageWrapper(OPCPackage opcPackage) {
        this.opcPackage = opcPackage;
    }

    @Override
    public void close() throws IOException {
        opcPackage.revert();
    }

    public OPCPackage getOPCPackage() {
        return opcPackage;
    }
}
