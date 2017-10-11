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

import static org.apache.tika.utils.DateUtils.formatDate;

import java.io.Serializable;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.tika.metadata.Property.PropertyType;
import org.apache.tika.utils.DateUtils;

/**
 * A multi-valued metadata container.
 */
public class Metadata implements CreativeCommons, Geographic, HttpHeaders,
        Message, MSOffice, ClimateForcast, TIFF, TikaMetadataKeys, TikaMimeKeys,
        Serializable {

    /** Serial version UID */
    private static final long serialVersionUID = 5623926545693153182L;

    /**
     * A map of all metadata attributes.
     */
    private Map<String, String[]> metadata = null;

    /**
     * The common delimiter used between the namespace abbreviation and the property name
     */
    public static final String NAMESPACE_PREFIX_DELIMITER = ":";

    /** @deprecated use TikaCoreProperties#FORMAT */
    public static final String FORMAT = "format";
    /** @deprecated use TikaCoreProperties#IDENTIFIER */
    public static final String IDENTIFIER = "identifier";
    /** @deprecated use TikaCoreProperties#MODIFIED */
    public static final String MODIFIED = "modified";
    /** @deprecated use TikaCoreProperties#CONTRIBUTOR */
    public static final String CONTRIBUTOR = "contributor";
    /** @deprecated use TikaCoreProperties#COVERAGE */
    public static final String COVERAGE = "coverage";
    /** @deprecated use TikaCoreProperties#CREATOR */
    public static final String CREATOR = "creator";
    /** @deprecated use TikaCoreProperties#CREATED */
    public static final Property DATE = Property.internalDate("date");
    /** @deprecated use TikaCoreProperties#DESCRIPTION */
    public static final String DESCRIPTION = "description";
    /** @deprecated use TikaCoreProperties#LANGUAGE */
    public static final String LANGUAGE = "language";
    /** @deprecated use TikaCoreProperties#PUBLISHER */
    public static final String PUBLISHER = "publisher";
    /** @deprecated use TikaCoreProperties#RELATION */
    public static final String RELATION = "relation";
    /** @deprecated use TikaCoreProperties#RIGHTS */
    public static final String RIGHTS = "rights";
    /** @deprecated use TikaCoreProperties#SOURCE */
    public static final String SOURCE = "source";
    /** @deprecated use TikaCoreProperties#KEYWORDS */
    public static final String SUBJECT = "subject";
    /** @deprecated use TikaCoreProperties#TITLE */
    public static final String TITLE = "title";
    /** @deprecated use TikaCoreProperties#TYPE */
    public static final String TYPE = "type";

    /**
     * Some parsers will have the date as a ISO-8601 string
     *  already, and will set that into the Metadata object.
     */
    private static final DateUtils DATE_UTILS = new DateUtils();

    private static DateFormat createDateFormat(String format, TimeZone timezone) {
        SimpleDateFormat sdf =
            new SimpleDateFormat(format, new DateFormatSymbols(Locale.US));
        if (timezone != null) {
            sdf.setTimeZone(timezone);
        }
        return sdf;
    }

    /**
     * Parses the given date string. This method is synchronized to prevent
     * concurrent access to the thread-unsafe date formats.
     *
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-495">TIKA-495</a>
     * @param date date string
     * @return parsed date, or <code>null</code> if the date can't be parsed
     */
    private static synchronized Date parseDate(String date) {
        return DATE_UTILS.tryToParse(date);
    }

    /**
     * Constructs a new, empty metadata.
     */
    public Metadata() {
        metadata = new HashMap<String, String[]>();
    }

    /**
     * Returns true if named value is multivalued.
     * 
     * @param property
     *          metadata property
     * @return true is named value is multivalued, false if single value or null
     */
    public boolean isMultiValued(final Property property) {
        return metadata.get(property.getName()) != null && metadata.get(property.getName()).length > 1;
    }
    
    /**
     * Returns true if named value is multivalued.
     * 
     * @param name
     *          name of metadata
     * @return true is named value is multivalued, false if single value or null
     */
    public boolean isMultiValued(final String name) {
        return metadata.get(name) != null && metadata.get(name).length > 1;
    }

    /**
     * Returns an array of the names contained in the metadata.
     * 
     * @return Metadata names
     */
    public String[] names() {
        return metadata.keySet().toArray(new String[metadata.keySet().size()]);
    }

    /**
     * Get the value associated to a metadata name. If many values are assiociated
     * to the specified name, then the first one is returned.
     * 
     * @param name
     *          of the metadata.
     * @return the value associated to the specified metadata name.
     */
    public String get(final String name) {
        String[] values = metadata.get(name);
        if (values == null) {
            return null;
        } else {
            return values[0];
        }
    }

    /**
     * Returns the value (if any) of the identified metadata property.
     *
     * @since Apache Tika 0.7
     * @param property property definition
     * @return property value, or <code>null</code> if the property is not set
     */
    public String get(Property property) {
        return get(property.getName());
    }
    
    /**
     * Returns the value of the identified Integer based metadata property.
     * 
     * @since Apache Tika 0.8
     * @param property simple integer property definition
     * @return property value as a Integer, or <code>null</code> if the property is not set, or not a valid Integer
     */
    public Integer getInt(Property property) {
        if(property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            return null;
        }
        if(property.getPrimaryProperty().getValueType() != Property.ValueType.INTEGER) {
            return null;
        }
        
        String v = get(property);
        if(v == null) {
            return null;
        }
        try {
            return Integer.valueOf(v);
        } catch(NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the value of the identified Date based metadata property.
     * 
     * @since Apache Tika 0.8
     * @param property simple date property definition
     * @return property value as a Date, or <code>null</code> if the property is not set, or not a valid Date
     */
    public Date getDate(Property property) {
        if(property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            return null;
        }
        if(property.getPrimaryProperty().getValueType() != Property.ValueType.DATE) {
            return null;
        }
        
        String v = get(property);
        if (v != null) {
            return parseDate(v);
        } else {
            return null;
        }
    }
    
    /**
     * Get the values associated to a metadata name.
     * 
     * @param property
     *          of the metadata.
     * @return the values associated to a metadata name.
     */
    public String[] getValues(final Property property) {
        return _getValues(property.getName());
    }

    /**
     * Get the values associated to a metadata name.
     * 
     * @param name
     *          of the metadata.
     * @return the values associated to a metadata name.
     */
    public String[] getValues(final String name) {
        return _getValues(name);
    }

    private String[] _getValues(final String name) {
        String[] values = metadata.get(name);
        if (values == null) {
            values = new String[0];
        }
        return values;
    }
    
    private String[] appendedValues(String[] values, final String value) {
        String[] newValues = new String[values.length + 1];
        System.arraycopy(values, 0, newValues, 0, values.length);
        newValues[newValues.length - 1] = value;
        return newValues;
    }

    /**
     * Add a metadata name/value mapping. Add the specified value to the list of
     * values associated to the specified metadata name.
     * 
     * @param name
     *          the metadata name.
     * @param value
     *          the metadata value.
     */
    public void add(final String name, final String value) {
        String[] values = metadata.get(name);
        if (values == null) {
            set(name, value);
        } else {
            metadata.put(name, appendedValues(values, value));
        }
    }
    
    /**
     * Add a metadata property/value mapping. Add the specified value to the list of
     * values associated to the specified metadata property.
     * 
     * @param property
     *          the metadata property.
     * @param value
     *          the metadata value.
     */
    public void add(final Property property, final String value) {
        String[] values = metadata.get(property.getName());
        if (values == null) {
            set(property, value);
        } else {
             if (property.isMultiValuePermitted()) {
                 set(property, appendedValues(values, value));
             } else {
                 throw new PropertyTypeException(property.getName() +
                         " : " + property.getPropertyType());
             }
        }
    }

    /**
     * Copy All key-value pairs from properties.
     * 
     * @param properties
     *          properties to copy from
     */
    @SuppressWarnings("unchecked")
    public void setAll(Properties properties) {
        Enumeration<String> names =
            (Enumeration<String>) properties.propertyNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            metadata.put(name, new String[] { properties.getProperty(name) });
        }
    }

    /**
     * Set metadata name/value. Associate the specified value to the specified
     * metadata name. If some previous values were associated to this name,
     * they are removed. If the given value is <code>null</code>, then the
     * metadata entry is removed.
     *
     * @param name the metadata name.
     * @param value  the metadata value, or <code>null</code>
     */
    public void set(String name, String value) {
        if (value != null) {
            metadata.put(name, new String[] { value });
        } else {
            metadata.remove(name);
        }
    }

    /**
     * Sets the value of the identified metadata property.
     *
     * @since Apache Tika 0.7
     * @param property property definition
     * @param value    property value
     */
    public void set(Property property, String value) {
        if (property == null) {
            throw new NullPointerException("property must not be null");
        }
        if (property.getPropertyType() == PropertyType.COMPOSITE) {
            set(property.getPrimaryProperty(), value);
            if (property.getSecondaryExtractProperties() != null) {
                for (Property secondaryExtractProperty : property.getSecondaryExtractProperties()) {
                    set(secondaryExtractProperty, value);
                }
            }
        } else {
            set(property.getName(), value);
        }
    }
    
    /**
     * Sets the values of the identified metadata property.
     *
     * @since Apache Tika 1.2
     * @param property property definition
     * @param values    property values
     */
    public void set(Property property, String[] values) {
        if (property == null) {
            throw new NullPointerException("property must not be null");
        }
        if (property.getPropertyType() == PropertyType.COMPOSITE) {
            set(property.getPrimaryProperty(), values);
            if (property.getSecondaryExtractProperties() != null) {
                for (Property secondaryExtractProperty : property.getSecondaryExtractProperties()) {
                    set(secondaryExtractProperty, values);
                }
            }
        } else {
            metadata.put(property.getName(), values);
        }
    }

    /**
     * Sets the integer value of the identified metadata property.
     *
     * @since Apache Tika 0.8
     * @param property simple integer property definition
     * @param value    property value
     */
    public void set(Property property, int value) {
        if(property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE, property.getPrimaryProperty().getPropertyType());
        }
        if(property.getPrimaryProperty().getValueType() != Property.ValueType.INTEGER) {
            throw new PropertyTypeException(Property.ValueType.INTEGER, property.getPrimaryProperty().getValueType());
        }
        set(property, Integer.toString(value));
    }

    /**
     * Sets the real or rational value of the identified metadata property.
     *
     * @since Apache Tika 0.8
     * @param property simple real or simple rational property definition
     * @param value    property value
     */
    public void set(Property property, double value) {
        if(property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE, property.getPrimaryProperty().getPropertyType());
        }
        if(property.getPrimaryProperty().getValueType() != Property.ValueType.REAL &&
              property.getPrimaryProperty().getValueType() != Property.ValueType.RATIONAL) {
            throw new PropertyTypeException(Property.ValueType.REAL, property.getPrimaryProperty().getValueType());
        }
        set(property, Double.toString(value));
    }

    /**
     * Sets the date value of the identified metadata property.
     *
     * @since Apache Tika 0.8
     * @param property simple integer property definition
     * @param date     property value
     */
    public void set(Property property, Date date) {
        if(property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE, property.getPrimaryProperty().getPropertyType());
        }
        if(property.getPrimaryProperty().getValueType() != Property.ValueType.DATE) {
            throw new PropertyTypeException(Property.ValueType.DATE, property.getPrimaryProperty().getValueType());
        }
        String dateString = null;
        if (date != null) {
            dateString = formatDate(date);
        }
        set(property, dateString);
    }

    /**
     * Sets the date value of the identified metadata property.
     *
     * @since Apache Tika 0.8
     * @param property simple integer property definition
     * @param date     property value
     */
    public void set(Property property, Calendar date) {
        if(property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE, property.getPrimaryProperty().getPropertyType());
        }
        if(property.getPrimaryProperty().getValueType() != Property.ValueType.DATE) {
            throw new PropertyTypeException(Property.ValueType.DATE, property.getPrimaryProperty().getValueType());
        }
        String dateString = null;
        if (date != null) {
            dateString = formatDate(date);
        }
        set(property, dateString);
    }

    /**
     * Remove a metadata and all its associated values.
     * 
     * @param name
     *          metadata name to remove
     */
    public void remove(String name) {
        metadata.remove(name);
    }

    /**
     * Returns the number of metadata names in this metadata.
     * 
     * @return number of metadata names
     */
    public int size() {
        return metadata.size();
    }

    public int hashCode() {
        int h = 0;
        for (Iterator<Entry<String,String[]>> i = metadata.entrySet().iterator();
             i.hasNext();) {
            h += getMetadataEntryHashCode(i.next());
        }
        return h;
    }

    private int getMetadataEntryHashCode(Entry<String, String[]> e) {
    	return Objects.hashCode(e.getKey()) ^ Arrays.hashCode(e.getValue());
	}

	public boolean equals(Object o) {

        if (o == null) {
            return false;
        }

        Metadata other = null;
        try {
            other = (Metadata) o;
        } catch (ClassCastException cce) {
            return false;
        }

        if (other.size() != size()) {
            return false;
        }

        String[] names = names();
        for (int i = 0; i < names.length; i++) {
            String[] otherValues = other._getValues(names[i]);
            String[] thisValues = _getValues(names[i]);
            if (otherValues.length != thisValues.length) {
                return false;
            }
            for (int j = 0; j < otherValues.length; j++) {
                if (!otherValues[j].equals(thisValues[j])) {
                    return false;
                }
            }
        }
        return true;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        String[] names = names();
        for (int i = 0; i < names.length; i++) {
            String[] values = _getValues(names[i]);
            for (int j = 0; j < values.length; j++) {
            	if (buf.length() > 0) {
            		buf.append(" ");
            	}
                buf.append(names[i]).append("=").append(values[j]);
            }
        }
        return buf.toString();
    }

}
