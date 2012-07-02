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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * XMP property definition. Each instance of this class defines a single
 * metadata property like "dc:format". In addition to the property name,
 * the {@link ValueType value type} and category (internal or external)
 * of the property are included in the property definition. The available
 * choice values are also stored for open and closed choice value types.
 *
 * @since Apache Tika 0.7
 */
public final class Property implements Comparable<Property> {

    public static enum PropertyType {
        /** A single value */
        SIMPLE, 
        STRUCTURE, 
        /** An un-ordered array */
        BAG, 
        /** An ordered array */
        SEQ, 
        /** An ordered array with some sort of criteria */
        ALT, 
        /** Multiple child properties */
        COMPOSITE
    }

    public static enum ValueType {
        BOOLEAN, OPEN_CHOICE, CLOSED_CHOICE, DATE, INTEGER, LOCALE,
        MIME_TYPE, PROPER_NAME, RATIONAL, REAL, TEXT, URI, URL, XPATH, PROPERTY
    }

    private static final Map<String, Property> properties =
            new HashMap<String, Property>();

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

    private Property(
            String name, boolean internal, PropertyType propertyType,
            ValueType valueType, String[] choices, Property primaryProperty, Property[] secondaryExtractProperties) {
        this.name = name;
        this.internal = internal;
        this.propertyType = propertyType;
        this.valueType = valueType;
        if (choices != null) {
            this.choices = Collections.unmodifiableSet(
                    new HashSet<String>(Arrays.asList(choices.clone())));
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
            synchronized (properties) {
               properties.put(name, this);
           }
        }
    }
    
    private Property(
            String name, boolean internal, PropertyType propertyType,
            ValueType valueType, String[] choices) {
    	this(name, internal, propertyType, valueType, choices, null, null);
    }

    private Property(
            String name, boolean internal,
            ValueType valueType, String[] choices) {
        this(name, internal, PropertyType.SIMPLE, valueType, choices);
    }

    private Property(String name, boolean internal, ValueType valueType) {
        this(name, internal, PropertyType.SIMPLE, valueType, null);
    }

    private Property(
            String name, boolean internal,
            PropertyType propertyType, ValueType valueType) {
        this(name, internal, propertyType, valueType, null);
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

    /**
     * Get the type of a property
     * @param key name of the property
     * @return the type of the property
     */
    public static PropertyType getPropertyType(String key) {
        PropertyType type = null;
        Property prop = properties.get(key);
        if (prop != null) {
            type = prop.getPropertyType();
        }
        return type;
    }

    /**
     * Retrieve the property object that corresponds to the given key
     * @param key the property key or name
     * @return the Property object
     */
    public static Property get(String key) {
        return properties.get(key);
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
    
    public static SortedSet<Property> getProperties(String prefix) {
        SortedSet<Property> set = new TreeSet<Property>();
        String p = prefix + ":";
        synchronized (properties) {
            for (String name : properties.keySet()) {
                if (name.startsWith(p)) {
                    set.add(properties.get(name));
                }
            }
        }
        return set;
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

    public static Property internalIntegerSequence(String name) {
        return new Property(name, true, PropertyType.SEQ, ValueType.INTEGER);
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
    
    public static Property internalTextBag(String name) {
        return new Property(name, true, PropertyType.BAG, ValueType.TEXT);
    }

    public static Property internalURI(String name) {
        return new Property(name, true, ValueType.URI);
    }

    public static Property externalClosedChoise(
            String name, String... choices) {
        return new Property(name, false, ValueType.CLOSED_CHOICE, choices);
    }

    public static Property externalOpenChoise(
            String name, String... choices) {
        return new Property(name, false, ValueType.OPEN_CHOICE, choices);
    }

    public static Property externalDate(String name) {
        return new Property(name, false, ValueType.DATE);
    }

    public static Property externalReal(String name) {
       return new Property(name, false, ValueType.REAL);
   }

    public static Property externalInteger(String name) {
        return new Property(name, false, ValueType.INTEGER);
    }

    public static Property externalBoolean(String name) {
       return new Property(name, false, ValueType.BOOLEAN);
   }

    public static Property externalText(String name) {
        return new Property(name, false, ValueType.TEXT);
    }

    public static Property externalTextBag(String name) {
        return new Property(name, false, PropertyType.BAG, ValueType.TEXT);
    }

    /**
     * Constructs a new composite property from the given primary and array of secondary properties.
     * <p>
     * Note that name of the composite property is taken from its primary property, 
     * and primary and secondary properties must not be composite properties themselves.
     * 
     * @param primaryProperty
     * @param secondaryExtractProperties
     * @return the composite property
     */
    public static Property composite(Property primaryProperty, Property[] secondaryExtractProperties) {
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
            choices = primaryProperty.getChoices().toArray(
                    new String[primaryProperty.getChoices().size()]);
        }
        return new Property(primaryProperty.getName(),
                primaryProperty.isInternal(), PropertyType.COMPOSITE,
                ValueType.PROPERTY, choices, primaryProperty,
                secondaryExtractProperties);
    }

    //----------------------------------------------------------< Comparable >

    public int compareTo(Property o) {
        return name.compareTo(o.name);
    }

    //--------------------------------------------------------------< Object >

    public boolean equals(Object o) {
        return o instanceof Property && name.equals(((Property) o).name);
    }

    public int hashCode() {
        return name.hashCode();
    }

}
