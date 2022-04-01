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
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.tika.metadata.Property.PropertyType;
import org.apache.tika.metadata.writefilter.MetadataWriteFilter;
import org.apache.tika.utils.DateUtils;

/**
 * A multi-valued metadata container.
 */
public class Metadata
        implements CreativeCommons, Geographic, HttpHeaders, Message, ClimateForcast, TIFF,
        TikaMimeKeys, Serializable {


    private static final MetadataWriteFilter ACCEPT_ALL = new MetadataWriteFilter() {
        @Override
        public void filterExisting(Map<String, String[]> data) {
            //no-op
        }

        @Override
        public void add(String field, String value, Map<String, String[]> data) {
            String[] values = data.get(field);
            if (values == null) {
                set(field, value, data);
            } else {
                data.put(field, appendValues(values, value));
            }
        }

        //legacy behavior -- remove the field if value is null
        @Override
        public void set(String field, String value, Map<String, String[]> data) {
            if (value != null) {
                data.put(field, new String[]{ value });
            } else {
                data.remove(field);
            }
        }

        private String[] appendValues(String[] values, final String value) {
            if (value == null) {
                return values;
            }
            String[] newValues = new String[values.length + 1];
            System.arraycopy(values, 0, newValues, 0, values.length);
            newValues[newValues.length - 1] = value;
            return newValues;
        }
    };

    /**
     * Serial version UID
     */
    private static final long serialVersionUID = 5623926545693153182L;
    /**
     * Some parsers will have the date as a ISO-8601 string
     * already, and will set that into the Metadata object.
     */
    private static final DateUtils DATE_UTILS = new DateUtils();
    /**
     * A map of all metadata attributes.
     */
    private Map<String, String[]> metadata = null;


    private MetadataWriteFilter writeFilter = ACCEPT_ALL;
    /**
     * Constructs a new, empty metadata.
     */
    public Metadata() {
        metadata = new HashMap<>();
    }

    private static DateFormat createDateFormat(String format, TimeZone timezone) {
        SimpleDateFormat sdf = new SimpleDateFormat(format, new DateFormatSymbols(Locale.US));
        if (timezone != null) {
            sdf.setTimeZone(timezone);
        }
        return sdf;
    }

    /**
     * Parses the given date string. This method is synchronized to prevent
     * concurrent access to the thread-unsafe date formats.
     *
     * @param date date string
     * @return parsed date, or <code>null</code> if the date can't be parsed
     * @see <a href="https://issues.apache.org/jira/browse/TIKA-495">TIKA-495</a>
     */
    private static synchronized Date parseDate(String date) {
        return DATE_UTILS.tryToParse(date);
    }

    /**
     * Returns true if named value is multivalued.
     *
     * @param property metadata property
     * @return true is named value is multivalued, false if single value or null
     */
    public boolean isMultiValued(final Property property) {
        return metadata.get(property.getName()) != null &&
                metadata.get(property.getName()).length > 1;
    }

    /**
     * Returns true if named value is multivalued.
     *
     * @param name name of metadata
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
        return metadata.keySet().toArray(new String[0]);
    }

    /**
     * Get the value associated to a metadata name. If many values are assiociated
     * to the specified name, then the first one is returned.
     *
     * @param name of the metadata.
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
     * Sets the writeFilter that is called before {@link #set(String, String)}
     * {@link #set(String, String[])}, {@link #add(String, String)},
     * {@link #add(String, String[])}.  The default is {@link #ACCEPT_ALL}.
     *
     * This is intended for expert use only.  Some parsers rely on metadata
     * during the parse, and if the metadata they need is excluded, they
     * will not function properly.
     *
     * @param writeFilter
     * @since 2.4.0
     */
    public void setMetadataWriteFilter(MetadataWriteFilter writeFilter) {
        this.writeFilter = writeFilter;
        this.writeFilter.filterExisting(metadata);
    }

    /**
     * Returns the value (if any) of the identified metadata property.
     *
     * @param property property definition
     * @return property value, or <code>null</code> if the property is not set
     * @since Apache Tika 0.7
     */
    public String get(Property property) {
        return get(property.getName());
    }

    /**
     * Returns the value of the identified Integer based metadata property.
     *
     * @param property simple integer property definition
     * @return property value as a Integer, or <code>null</code> if the property is not set, or
     * not a valid Integer
     * @since Apache Tika 0.8
     */
    public Integer getInt(Property property) {
        if (property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            return null;
        }
        if (property.getPrimaryProperty().getValueType() != Property.ValueType.INTEGER) {
            return null;
        }

        String v = get(property);
        if (v == null) {
            return null;
        }
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Returns the value of the identified Date based metadata property.
     *
     * @param property simple date property definition
     * @return property value as a Date, or <code>null</code> if the property is not set, or not
     * a valid Date
     * @since Apache Tika 0.8
     */
    public Date getDate(Property property) {
        if (property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            return null;
        }
        if (property.getPrimaryProperty().getValueType() != Property.ValueType.DATE) {
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
     * @param property of the metadata.
     * @return the values associated to a metadata name.
     */
    public String[] getValues(final Property property) {
        return _getValues(property.getName());
    }

    /**
     * Get the values associated to a metadata name.
     *
     * @param name of the metadata.
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

    /**
     * Add a metadata name/value mapping. Add the specified value to the list of
     * values associated to the specified metadata name.
     *
     * @param name  the metadata name.
     * @param value the metadata value.
     */
    public void add(final String name, final String value) {
        writeFilter.add(name, value, metadata);
    }

    /**
     * Add a metadata name/value mapping. Add the specified value to the list of
     * values associated to the specified metadata name.
     *
     * @param name  the metadata name.
     * @param newValues the metadata values
     */
    protected void add(final String name, final String[] newValues) {
        String[] values = metadata.get(name);
        if (values == null) {
            set(name, newValues);
        } else {
            for (String val : newValues) {
                add(name, val);
            }
        }
    }

    /**
     * Add a metadata property/value mapping. Add the specified value to the list of
     * values associated to the specified metadata property.
     *
     * @param property the metadata property.
     * @param value    the metadata value.
     */
    public void add(final Property property, final String value) {

        if (property == null) {
            throw new NullPointerException("property must not be null");
        }
        if (property.getPropertyType() == PropertyType.COMPOSITE) {
            add(property.getPrimaryProperty(), value);
            if (property.getSecondaryExtractProperties() != null) {
                for (Property secondaryExtractProperty : property.getSecondaryExtractProperties()) {
                    add(secondaryExtractProperty, value);
                }
            }
        } else {
            String[] values = metadata.get(property.getName());

            if (values == null) {
                set(property, value);
            } else {
                if (property.isMultiValuePermitted()) {
                    add(property.getName(), value);
                } else {
                    throw new PropertyTypeException(
                            property.getName() + " : " + property.getPropertyType());
                }
            }
        }
    }

    /**
     * Copy All key-value pairs from properties.
     *
     * @param properties properties to copy from
     */
    @SuppressWarnings("unchecked")
    public void setAll(Properties properties) {
        Enumeration<String> names = (Enumeration<String>) properties.propertyNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            metadata.put(name, new String[]{properties.getProperty(name)});
        }
    }

    /**
     * Set metadata name/value. Associate the specified value to the specified
     * metadata name. If some previous values were associated to this name,
     * they are removed. If the given value is <code>null</code>, then the
     * metadata entry is removed.
     *
     * @param name  the metadata name.
     * @param value the metadata value, or <code>null</code>
     */
    public void set(String name, String value) {
        writeFilter.set(name, value, metadata);
    }

    protected void set(String name, String[] values) {
        //TODO: optimize this to not copy if all
        //values are to be included "as is"
        if (values != null) {
            metadata.remove(name);
            for (String v : values) {
                add(name, v);
            }
        } else {
            metadata.remove(name);
        }
    }

    /**
     * Sets the value of the identified metadata property.
     *
     * @param property property definition
     * @param value    property value
     * @since Apache Tika 0.7
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
     * @param property property definition
     * @param values   property values
     * @since Apache Tika 1.2
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
            set(property.getName(), values);
        }
    }

    /**
     * Sets the integer value of the identified metadata property.
     *
     * @param property simple integer property definition
     * @param value    property value
     * @since Apache Tika 0.8
     */
    public void set(Property property, int value) {
        if (property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE,
                    property.getPrimaryProperty().getPropertyType());
        }
        if (property.getPrimaryProperty().getValueType() != Property.ValueType.INTEGER) {
            throw new PropertyTypeException(Property.ValueType.INTEGER,
                    property.getPrimaryProperty().getValueType());
        }
        set(property, Integer.toString(value));
    }

    /**
     * Sets the integer value of the identified metadata property.
     *
     * @param property simple integer property definition
     * @param value    property value
     * @since Apache Tika 0.8
     */
    public void set(Property property, long value) {
        if (property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE,
                    property.getPrimaryProperty().getPropertyType());
        }
        if (property.getPrimaryProperty().getValueType() != Property.ValueType.REAL) {
            throw new PropertyTypeException(Property.ValueType.REAL,
                    property.getPrimaryProperty().getValueType());
        }
        set(property, Long.toString(value));
    }
    /**
     * Sets the integer value of the identified metadata property.
     *
     * @param property simple integer property definition
     * @param value    property value
     * @since Apache Tika 2.1.1
     */
    public void set(Property property, boolean value) {
        if (property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE,
                    property.getPrimaryProperty().getPropertyType());
        }
        if (property.getPrimaryProperty().getValueType() != Property.ValueType.BOOLEAN) {
            throw new PropertyTypeException(Property.ValueType.BOOLEAN,
                    property.getPrimaryProperty().getValueType());
        }
        set(property, Boolean.toString(value));
    }

    /**
     * Adds the integer value of the identified metadata property.
     *
     * @param property seq integer property definition
     * @param value    property value
     * @since Apache Tika 1.21
     */
    public void add(Property property, int value) {
        if (property.getPrimaryProperty().getPropertyType() != PropertyType.SEQ) {
            throw new PropertyTypeException(PropertyType.SEQ,
                    property.getPrimaryProperty().getPropertyType());
        }
        if (property.getPrimaryProperty().getValueType() != Property.ValueType.INTEGER) {
            throw new PropertyTypeException(Property.ValueType.INTEGER,
                    property.getPrimaryProperty().getValueType());
        }
        add(property, Integer.toString(value));
    }

    /**
     * Gets the array of ints of the identified "seq" integer metadata property.
     *
     * @param property seq integer property definition
     * @return array of ints
     * @since Apache Tika 1.21
     */
    public int[] getIntValues(Property property) {
        if (property.getPrimaryProperty().getPropertyType() != PropertyType.SEQ) {
            throw new PropertyTypeException(PropertyType.SEQ,
                    property.getPrimaryProperty().getPropertyType());
        }
        if (property.getPrimaryProperty().getValueType() != Property.ValueType.INTEGER) {
            throw new PropertyTypeException(Property.ValueType.INTEGER,
                    property.getPrimaryProperty().getValueType());
        }
        String[] vals = getValues(property);
        int[] ret = new int[vals.length];
        for (int i = 0; i < vals.length; i++) {
            ret[i] = Integer.parseInt(vals[i]);
        }
        return ret;
    }

    /**
     * Sets the real or rational value of the identified metadata property.
     *
     * @param property simple real or simple rational property definition
     * @param value    property value
     * @since Apache Tika 0.8
     */
    public void set(Property property, double value) {
        if (property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE,
                    property.getPrimaryProperty().getPropertyType());
        }
        if (property.getPrimaryProperty().getValueType() != Property.ValueType.REAL &&
                property.getPrimaryProperty().getValueType() != Property.ValueType.RATIONAL) {
            throw new PropertyTypeException(Property.ValueType.REAL,
                    property.getPrimaryProperty().getValueType());
        }
        set(property, Double.toString(value));
    }

    /**
     * Sets the date value of the identified metadata property.
     *
     * @param property simple integer property definition
     * @param date     property value
     * @since Apache Tika 0.8
     */
    public void set(Property property, Date date) {
        if (property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE,
                    property.getPrimaryProperty().getPropertyType());
        }
        if (property.getPrimaryProperty().getValueType() != Property.ValueType.DATE) {
            throw new PropertyTypeException(Property.ValueType.DATE,
                    property.getPrimaryProperty().getValueType());
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
     * @param property simple integer property definition
     * @param date     property value
     * @since Apache Tika 0.8
     */
    public void set(Property property, Calendar date) {
        if (property.getPrimaryProperty().getPropertyType() != Property.PropertyType.SIMPLE) {
            throw new PropertyTypeException(Property.PropertyType.SIMPLE,
                    property.getPrimaryProperty().getPropertyType());
        }
        if (property.getPrimaryProperty().getValueType() != Property.ValueType.DATE) {
            throw new PropertyTypeException(Property.ValueType.DATE,
                    property.getPrimaryProperty().getValueType());
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
     * @param name metadata name to remove
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
        for (Entry<String, String[]> stringEntry : metadata.entrySet()) {
            h += getMetadataEntryHashCode(stringEntry);
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
        for (String name : names) {
            String[] otherValues = other._getValues(name);
            String[] thisValues = _getValues(name);
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
        for (String name : names) {
            String[] values = _getValues(name);
            for (String value : values) {
                if (buf.length() > 0) {
                    buf.append(" ");
                }
                buf.append(name).append("=").append(value);
            }
        }
        return buf.toString();
    }
}
