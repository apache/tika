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

/**
 * This class records metadata about embedded parts that exists in the xml
 * of the main document.
 */
public class EmbeddedPartMetadata {

    private final String emfRelationshipId;
    private String renderedName;
    private String fullName;

    private String progId;

    //This is the rId of the EMF file that is associated with
    //the embedded object

    /**
     *
     * @param emfRelationshipId relationship id of the EMF file
     */
    public EmbeddedPartMetadata(String emfRelationshipId) {
        this.emfRelationshipId = emfRelationshipId;
    }

    public String getEmfRelationshipId() {
        return emfRelationshipId;
    }

    public String getRenderedName() {
        return renderedName;
    }

    public String getFullName() {
        return fullName;
    }

    public String getProgId() {
        return progId;
    }

    public void setRenderedName(String renderedName) {
        this.renderedName = renderedName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setProgId(String progId) {
        this.progId = progId;
    }
}
