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
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * XMP property definition. Each instance of this class defines a single
 * metadata property like "dc:format". In addition to the property name,
 * the {@link ValueType value type} and category (internal or external)
 * of the property are included in the property definition. The available
 * choice values are also stored for open and closed choice value types.
 * <p>
 * Every public factory method below throws {@link IllegalArgumentException} if {@code name}
 * is in the reserved Tika-native namespace ({@code tk:}/{@code X-TIKA:}); see
 * {@link ReservedNamespaces}.
 * <p>
 * <strong>The public factories intern every Property in a static, JVM-global registry,
 * permanently</strong> (first-wins: a name collision keeps the earlier registration and WARNs
 * on a shape mismatch). They are for declaration-time constants — a bounded population. Never
 * feed them runtime, per-document, or user-supplied names: each distinct name grows the
 * registry forever, and a collision can shadow a curated constant JVM-wide. Document-derived
 * names go through {@link Metadata#add(KeyPrefix, String, String)} with no Property at all.
 *
 * @since Apache Tika 0.7
 */
public final class Property implements Comparable<Property> {

    private static final Logger LOG = LoggerFactory.getLogger(Property.class);
    private static final Map<String, Property> PROPERTIES = new ConcurrentHashMap<>();
    private final String name;
    private final boolean internal;
    private final PropertyType propertyType;
    private final ValueType valueType;
    private final Property primaryProperty;
    private final Property[] secondaryExtractProperties;
    /**
     * The available choices for the open and closed choice value types.
     */
    private final Set<String> choices;

    private Property(String name, boolean internal, PropertyType propertyType, ValueType valueType,
                     String[] choices, Property primaryProperty,
                     Property[] secondaryExtractProperties) {
        this(name, internal, propertyType, valueType, choices, primaryProperty,
                secondaryExtractProperties, true);
    }

    /**
     * @param register whether to intern this Property in the static registry. Composites
     *                  (non-null primaryProperty) never register, regardless. Non-composite
     *                  registration is first-wins: a name collision keeps the
     *                  earlier-registered Property and this one is not stored; a
     *                  same-name-different-shape collision also logs a WARN.
     */
    private Property(String name, boolean internal, PropertyType propertyType, ValueType valueType,
                     String[] choices, Property primaryProperty,
                     Property[] secondaryExtractProperties, boolean register) {
        this.name = name;
        this.internal = internal;
        this.propertyType = propertyType;
        this.valueType = valueType;
        if (choices != null) {
            this.choices = Collections
                    .unmodifiableSet(new HashSet<>(Arrays.asList(choices.clone())));
        } else {
            this.choices = null;
        }

        if (primaryProperty != null) {
            this.primaryProperty = primaryProperty;
            this.secondaryExtractProperties = secondaryExtractProperties;
        } else {
            this.primaryProperty = this;
            this.secondaryExtractProperties = null;

            // Only store primary properties for lookup, not composites
            if (register) {
                synchronized (PROPERTIES) {
                    Property incumbent = PROPERTIES.putIfAbsent(name, this);
                    // same-shape re-mints (e.g. class re-init) stay quiet; a shape mismatch
                    // is a real bug (two differently-typed Properties claim the same name)
                    if (incumbent != null && (incumbent.propertyType != propertyType
                            || incumbent.valueType != valueType)) {
                        LOG.warn(
                                "Property registration collision for '{}': keeping {}/{}, " +
                                        "dropping {}/{} -- two declarations claim this name; " +
                                        "check for duplicate Property constants across jars, " +
                                        "or runtime code minting via a public factory",
                                name, incumbent.propertyType, incumbent.valueType, propertyType,
                                valueType, new Exception("registration site of the dropped mint"));
                    }
                }
            }
        }
    }

    private Property(String name, boolean internal, PropertyType propertyType, ValueType valueType,
                     String[] choices) {
        this(name, internal, propertyType, valueType, choices, null, null);
    }

    private Property(String name, boolean internal, ValueType valueType, String[] choices) {
        this(name, internal, PropertyType.SIMPLE, valueType, choices);
    }

    private Property(String name, boolean internal, ValueType valueType) {
        this(name, internal, PropertyType.SIMPLE, valueType, null);
    }

    private Property(String name, boolean internal, PropertyType propertyType,
                     ValueType valueType) {
        this(name, internal, propertyType, valueType, null);
    }

    /**
     * Package-private, non-registering, lock-free minting path: skips {@code PROPERTIES}
     * entirely, no interning and no lock. For call sites that must construct Properties
     * from runtime-supplied names, where interning would grow the static registry without
     * bound. Never composite. Document-derived names normally take
     * {@link Metadata#add(KeyPrefix, String, String)} instead of any Property at all.
     */
    static Property mintUnregistered(String name, boolean internal, PropertyType propertyType,
                                     ValueType valueType, String[] choices) {
        return new Property(name, internal, propertyType, valueType, choices, null, null, false);
    }

    /**
     * Guards the public factories: a {@code tk:}/{@code X-TIKA:} name can never be minted
     * through them. Curated reserved constants go through the package-private
     * {@code reservedInternal*}/{@code reservedExternal*} family below instead, so any live
     * {@code Property} carrying a reserved name is, by construction, curated.
     */
    private static void requireNotReserved(String name) {
        if (ReservedNamespaces.isTikaNative(name)) {
            throw new IllegalArgumentException("'" + name + "' is in the reserved Tika-native "
                    + "namespace (tk:/X-TIKA:); it cannot be minted via a public Property "
                    + "factory. Curated tk: constants belong in org.apache.tika.metadata and "
                    + "mint via the package-private reserved* factories. If you are declaring "
                    + "your own Property constant, use your own namespace instead of tk:/X-TIKA:.");
        }
    }

    /** Fails loud if a reserved-path factory is fed a name that isn't actually reserved. */
    private static void requireReserved(String name) {
        if (!ReservedNamespaces.isTikaNative(name)) {
            throw new IllegalArgumentException("'" + name + "' is not in the reserved "
                    + "Tika-native namespace (tk:/X-TIKA:); use a public Property factory "
                    + "instead of the package-private reserved* path.");
        }
    }

    /**
     * Get the type of a property
     *
     * @param key name of the property
     * @return the type of the property
     */
    public static PropertyType getPropertyType(String key) {
        PropertyType type = null;
        Property prop = PROPERTIES.get(key);
        if (prop != null) {
            type = prop.getPropertyType();
        }
        return type;
    }

    /**
     * Retrieve the property object that corresponds to the given key. Only registered
     * Properties resolve here: document-derived keys (written via
     * {@link Metadata#add(KeyPrefix, String, String)} or minted through
     * {@code Property#mintUnregistered}) are never interned, so this always returns
     * {@code null} for them, even after a write. This is load-bearing for
     * {@link Metadata#reconstruct}, which falls back to the trusted String path when lookup
     * misses.
     *
     * @param key the property key or name
     * @return the Property object, or {@code null} if unregistered
     */
    public static Property get(String key) {
        return PROPERTIES.get(key);
    }

    /** Like {@link #get(String)}, registered Properties only: never returns a
     * {@code KeyPrefix}-minted Property. */
    public static SortedSet<Property> getProperties(String prefix) {
        SortedSet<Property> set = new TreeSet<>();
        String p = prefix + ":";
        synchronized (PROPERTIES) {
            for (Map.Entry<String, Property> entry : PROPERTIES.entrySet()) {
                if (entry.getKey().startsWith(p)) {
                    set.add(entry.getValue());
                }
            }
        }
        return set;
    }

    public static Property internalBoolean(String name) {
        requireNotReserved(name);
        return new Property(name, true, ValueType.BOOLEAN);
    }

    public static Property internalClosedChoice(String name, String... choices) {
        requireNotReserved(name);
        return new Property(name, true, ValueType.CLOSED_CHOICE, choices);
    }

    public static Property internalDate(String name) {
        requireNotReserved(name);
        return new Property(name, true, ValueType.DATE);
    }

    public static Property internalDateBag(String name) {
        requireNotReserved(name);
        return new Property(name, true, PropertyType.BAG, ValueType.DATE);
    }

    public static Property internalInteger(String name) {
        requireNotReserved(name);
        return new Property(name, true, ValueType.INTEGER);
    }

    public static Property internalIntegerSequence(String name) {
        requireNotReserved(name);
        return new Property(name, true, PropertyType.SEQ, ValueType.INTEGER);
    }

    public static Property internalRational(String name) {
        requireNotReserved(name);
        return new Property(name, true, ValueType.RATIONAL);
    }

    public static Property internalOpenChoice(String name, String... choices) {
        requireNotReserved(name);
        return new Property(name, true, ValueType.OPEN_CHOICE, choices);
    }

    public static Property internalReal(String name) {
        requireNotReserved(name);
        return new Property(name, true, ValueType.REAL);
    }

    public static Property internalText(String name) {
        requireNotReserved(name);
        return new Property(name, true, ValueType.TEXT);
    }

    public static Property internalTextBag(String name) {
        requireNotReserved(name);
        return new Property(name, true, PropertyType.BAG, ValueType.TEXT);
    }

    public static Property internalURI(String name) {
        requireNotReserved(name);
        return new Property(name, true, ValueType.URI);
    }

    public static Property externalClosedChoice(String name, String... choices) {
        requireNotReserved(name);
        return new Property(name, false, ValueType.CLOSED_CHOICE, choices);
    }

    public static Property externalOpenChoice(String name, String... choices) {
        requireNotReserved(name);
        return new Property(name, false, ValueType.OPEN_CHOICE, choices);
    }

    public static Property externalDate(String name) {
        requireNotReserved(name);
        return new Property(name, false, ValueType.DATE);
    }

    public static Property externalReal(String name) {
        requireNotReserved(name);
        return new Property(name, false, ValueType.REAL);
    }

    public static Property externalRealSeq(String name) {
        requireNotReserved(name);
        return new Property(name, false, PropertyType.SEQ, ValueType.REAL);
    }

    public static Property externalIntegerSequence(String name) {
        requireNotReserved(name);
        return new Property(name, false, PropertyType.SEQ, ValueType.INTEGER);
    }

    public static Property externalInteger(String name) {
        requireNotReserved(name);
        return new Property(name, false, ValueType.INTEGER);
    }

    public static Property externalBoolean(String name) {
        requireNotReserved(name);
        return new Property(name, false, ValueType.BOOLEAN);
    }

    public static Property externalBooleanSeq(String name) {
        requireNotReserved(name);
        return new Property(name, false, PropertyType.SEQ, ValueType.BOOLEAN);
    }

    public static Property externalText(String name) {
        requireNotReserved(name);
        return new Property(name, false, ValueType.TEXT);
    }

    public static Property externalTextBag(String name) {
        requireNotReserved(name);
        return new Property(name, false, PropertyType.BAG, ValueType.TEXT);
    }

    // ---- Package-private mirrors for curated tk:/X-TIKA: constants -------------------
    // Same shapes as the public factories above, but (a) assert the name IS reserved
    // (b) still register (curated constants must stay resolvable via Property.get /
    // Metadata.reconstruct). Callers: TikaCoreProperties, TikaPagedText, Rendering — all
    // in-package. Not for document-derived names; those go through
    // Metadata#add(KeyPrefix, String, String) with no Property at all.

    static Property reservedInternalBoolean(String name) {
        requireReserved(name);
        return new Property(name, true, ValueType.BOOLEAN);
    }

    static Property reservedInternalClosedChoice(String name, String... choices) {
        requireReserved(name);
        return new Property(name, true, ValueType.CLOSED_CHOICE, choices);
    }

    static Property reservedInternalDate(String name) {
        requireReserved(name);
        return new Property(name, true, ValueType.DATE);
    }

    static Property reservedInternalDateBag(String name) {
        requireReserved(name);
        return new Property(name, true, PropertyType.BAG, ValueType.DATE);
    }

    static Property reservedInternalInteger(String name) {
        requireReserved(name);
        return new Property(name, true, ValueType.INTEGER);
    }

    static Property reservedInternalIntegerSequence(String name) {
        requireReserved(name);
        return new Property(name, true, PropertyType.SEQ, ValueType.INTEGER);
    }

    static Property reservedInternalRational(String name) {
        requireReserved(name);
        return new Property(name, true, ValueType.RATIONAL);
    }

    static Property reservedInternalText(String name) {
        requireReserved(name);
        return new Property(name, true, ValueType.TEXT);
    }

    static Property reservedInternalTextBag(String name) {
        requireReserved(name);
        return new Property(name, true, PropertyType.BAG, ValueType.TEXT);
    }

    static Property reservedExternalReal(String name) {
        requireReserved(name);
        return new Property(name, false, ValueType.REAL);
    }

    static Property reservedExternalRealSeq(String name) {
        requireReserved(name);
        return new Property(name, false, PropertyType.SEQ, ValueType.REAL);
    }

    static Property reservedExternalInteger(String name) {
        requireReserved(name);
        return new Property(name, false, ValueType.INTEGER);
    }

    static Property reservedExternalBoolean(String name) {
        requireReserved(name);
        return new Property(name, false, ValueType.BOOLEAN);
    }

    static Property reservedExternalText(String name) {
        requireReserved(name);
        return new Property(name, false, ValueType.TEXT);
    }

    static Property reservedExternalTextBag(String name) {
        requireReserved(name);
        return new Property(name, false, PropertyType.BAG, ValueType.TEXT);
    }

    /**
     * Constructs a new composite property from the given primary and array of secondary properties.
     * <p>
     * Note that name of the composite property is taken from its primary property,
     * and primary and secondary properties must not be composite properties themselves.
     * <p>
     * No reserved-name check here: {@code primaryProperty} was already validated at its own mint.
     *
     * @param primaryProperty
     * @param secondaryExtractProperties
     * @return the composite property
     */
    public static Property composite(Property primaryProperty,
                                     Property[] secondaryExtractProperties) {
        if (primaryProperty == null) {
            throw new NullPointerException("primaryProperty must not be null");
        }
        if (primaryProperty.getPropertyType() == PropertyType.COMPOSITE) {
            throw new PropertyTypeException(primaryProperty.getPropertyType());
        }
        if (secondaryExtractProperties != null) {
            for (Property secondaryExtractProperty : secondaryExtractProperties) {
                if (secondaryExtractProperty.getPropertyType() == PropertyType.COMPOSITE) {
                    throw new PropertyTypeException(secondaryExtractProperty.getPropertyType());
                }
            }
        }
        String[] choices = null;
        if (primaryProperty.getChoices() != null) {
            choices = primaryProperty.getChoices().toArray(new String[0]);
        }
        return new Property(primaryProperty.getName(), primaryProperty.isInternal(),
                PropertyType.COMPOSITE, ValueType.PROPERTY, choices, primaryProperty,
                secondaryExtractProperties);
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

    /**
     * Is the PropertyType one which accepts multiple values?
     */
    public boolean isMultiValuePermitted() {
        if (propertyType == PropertyType.BAG || propertyType == PropertyType.SEQ ||
                propertyType == PropertyType.ALT) {
            return true;
        } else if (propertyType == PropertyType.COMPOSITE) {
            // Base it on the primary property's behaviour
            return primaryProperty.isMultiValuePermitted();
        }
        return false;
    }

    public PropertyType getPropertyType() {
        return propertyType;
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

    /**
     * Gets the primary property for a composite property
     *
     * @return the primary property
     */
    public Property getPrimaryProperty() {
        return primaryProperty;
    }

    /**
     * Gets the secondary properties for a composite property
     *
     * @return the secondary properties
     */
    public Property[] getSecondaryExtractProperties() {
        return secondaryExtractProperties;
    }

    public int compareTo(Property o) {
        return name.compareTo(o.name);
    }

    public boolean equals(Object o) {
        return o instanceof Property && name.equals(((Property) o).name);
    }

    //----------------------------------------------------------< Comparable >

    public int hashCode() {
        return name.hashCode();
    }

    //--------------------------------------------------------------< Object >

    /** The property name, so a String→Property constant flip doesn't turn existing
     * logging/concatenation into {@code Property@<hex>} with no compile signal. */
    public String toString() {
        return name;
    }

    public enum PropertyType {
        /**
         * A single value
         */
        SIMPLE,
        /**
         * An un-ordered array
         */
        BAG,
        /**
         * An ordered array
         */
        SEQ,
        /**
         * An ordered array with some sort of criteria
         */
        ALT,
        /**
         * Multiple child properties
         */
        COMPOSITE
    }

    public enum ValueType {
        BOOLEAN, OPEN_CHOICE, CLOSED_CHOICE, DATE, INTEGER,
        RATIONAL, REAL, TEXT, URI, PROPERTY
    }

}
