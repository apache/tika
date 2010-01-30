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
package org.apache.tika.metadata;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * XMP property definition. Each instance of this class defines a single
 * metadata property like "dc:format". In addition to the property name,
 * the {@link ValueType value type} and category (internal or external)
 * of the property are included in the property definition. The available
 * choice values are also stored for open and closed choice value types.
 *
 * @since Apache Tika 0.7
 */
public final class Property {

    public static enum ValueType {
        BOOLEAN, OPEN_CHOICE, CLOSED_CHOICE, DATE, INTEGER, LOCALE,
        MIME_TYPE, PROPER_NAME, RATIONAL, REAL, TEXT, URI, URL, XPATH
    }

    private final String name;

    private final boolean internal;

    private final ValueType valueType;

    /**
     * The available choices for the open and closed choice value types.
     */
    private final Set<String> choices;

    private Property(
            String name, boolean internal,
            ValueType valueType, String[] choices) {
        this.name = name;
        this.internal = internal;
        this.valueType = valueType;
        if (choices != null) {
            this.choices = Collections.unmodifiableSet(
                    new HashSet<String>(Arrays.asList(choices)));
        } else {
            this.choices = null;
        }
    }

    public String getName() {
        return name;
    }

    public boolean isInternal() {
        return internal;
    }

    public boolean isExternal() {
        return !internal;
    }

    public ValueType getValueType() {
        return valueType;
    }

    /**
     * Returns the (immutable) set of choices for the values of this property.
     * Only defined for {@link ValueType#OPEN_CHOICE open} and
     * {@link ValueType#CLOSED_CHOICE closed choice} value types.
     *
     * @return available choices, or <code>null</code>
     */
    public Set<String> getChoices() {
        return choices;
    }

    private Property( String name, boolean internal, ValueType valueType) {
        this(name, internal, valueType, null);
    }

    public static Property internalBoolean(String name) {
        return new Property(name, true, ValueType.BOOLEAN);
    }

    public static Property internalClosedChoise(
            String name, String... choices) {
        return new Property(name, true, ValueType.CLOSED_CHOICE, choices);
    }

    public static Property internalDate(String name) {
        return new Property(name, true, ValueType.DATE);
    }

    public static Property internalInteger(String name) {
        return new Property(name, true, ValueType.INTEGER);
    }

    public static Property internalRational(String name) {
        return new Property(name, true, ValueType.RATIONAL);
    }

    public static Property internalOpenChoise(
            String name, String... choices) {
        return new Property(name, true, ValueType.OPEN_CHOICE, choices);
    }
    public static Property internalReal(String name) {
        return new Property(name, true, ValueType.REAL);
    }

    public static Property internalText(String name) {
        return new Property(name, true, ValueType.TEXT);
    }

    public static Property internalURI(String name) {
        return new Property(name, true, ValueType.URI);
    }

    public static Property externalClosedChoise(
            String name, String... choices) {
        return new Property(name, false, ValueType.CLOSED_CHOICE, choices);
    }

    public static Property externalDate(String name) {
        return new Property(name, false, ValueType.DATE);
    }

    public static Property externalInteger(String name) {
        return new Property(name, false, ValueType.INTEGER);
    }

    public static Property externalText(String name) {
        return new Property(name, false, ValueType.TEXT);
    }

}
