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
package org.apache.tika.parser.ctakes;

import org.apache.ctakes.typesystem.type.textsem.IdentifiedAnnotation;

/**
 * This enumeration includes the properties that an {@link IdentifiedAnnotation} object can provide.
 */
public enum CTAKESAnnotationProperty {
    BEGIN("start"), END("end"), CONDITIONAL("conditional"), CONFIDENCE("confidence"),
    DISCOVERY_TECNIQUE("discoveryTechnique"), GENERIC("generic"), HISTORY_OF("historyOf"), ID("id"),
    ONTOLOGY_CONCEPT_ARR("ontologyConceptArr"), POLARITY("polarity");

    private String name;

    CTAKESAnnotationProperty(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
