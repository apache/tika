/**
 * Copyright 2007 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.utils;

// JDK imports
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * Provides access to configuration parameters.
 */
public class Configuration {

    private Properties properties;

    /** A new configuration. */
    public Configuration() {
        this.properties = new Properties();
    }

    /** A new configuration with the same settings cloned from another. */
    public Configuration(Properties properties) {
        if (properties != null) {
            this.properties = (Properties) properties.clone();
        } else {
            this.properties = new Properties();
        }
    }

    /**
     * Returns the value of the <code>name</code> property, or null if no such
     * property exists.
     */
    public Object getObject(String name) {
        return properties.get(name);
    }

    /** Sets the value of the <code>name</code> property. */
    public void setObject(String name, Object value) {
        properties.put(name, value);
    }

    /**
     * Returns the value of the <code>name</code> property. If no such
     * property exists, then <code>defaultValue</code> is returned.
     */
    public Object get(String name, Object defaultValue) {
        Object res = getObject(name);
        return (res != null) ? res : defaultValue;
    }

    /**
     * Returns the value of the <code>name</code> property, or null if no such
     * property exists.
     */
    public String get(String name) {
        return properties.getProperty(name);
    }

    /** Sets the value of the <code>name</code> property. */
    public void set(String name, Object value) {
        properties.setProperty(name, value.toString());
    }

    /**
     * Returns the value of the <code>name</code> property. If no such
     * property exists, then <code>defaultValue</code> is returned.
     */
    public String get(String name, String defaultValue) {
        return properties.getProperty(name, defaultValue);
    }

    /**
     * Returns the value of the <code>name</code> property as an integer. If
     * no such property is specified, or if the specified value is not a valid
     * integer, then <code>defaultValue</code> is returned.
     */
    public int getInt(String name, int defaultValue) {
        try {
            return Integer.parseInt(get(name));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /** Sets the value of the <code>name</code> property to an integer. */
    public void setInt(String name, int value) {
        set(name, Integer.toString(value));
    }

    /**
     * Returns the value of the <code>name</code> property as a long. If no
     * such property is specified, or if the specified value is not a valid
     * long, then <code>defaultValue</code> is returned.
     */
    public long getLong(String name, long defaultValue) {
        try {
            return Long.parseLong(get(name));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /** Sets the value of the <code>name</code> property to a long. */
    public void setLong(String name, long value) {
        set(name, Long.toString(value));
    }

    /**
     * Returns the value of the <code>name</code> property as a float. If no
     * such property is specified, or if the specified value is not a valid
     * float, then <code>defaultValue</code> is returned.
     */
    public float getFloat(String name, float defaultValue) {
        try {
            return Float.parseFloat(get(name));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Returns the value of the <code>name</code> property as an boolean. If
     * no such property is specified, or if the specified value is not a valid
     * boolean, then <code>defaultValue</code> is returned. Valid boolean
     * values are "true" and "false".
     */
    public boolean getBoolean(String name, boolean defaultValue) {
        String valueString = get(name);
        if ("true".equals(valueString)) {
            return true;
        } else if ("false".equals(valueString)) {
            return false;
        } else {
            return defaultValue;
        }
    }

    /** Sets the value of the <code>name</code> property to an integer. */
    public void setBoolean(String name, boolean value) {
        set(name, Boolean.toString(value));
    }

    /**
     * Returns the value of the <code>name</code> property as an array of
     * strings. If no such property is specified, then <code>null</code> is
     * returned. Values are comma delimited.
     */
    public String[] getStrings(String name) {
        String valueString = get(name);
        if (valueString == null)
            return null;
        StringTokenizer tokenizer = new StringTokenizer(valueString, ",");
        List values = new ArrayList();
        while (tokenizer.hasMoreTokens()) {
            values.add(tokenizer.nextToken());
        }
        return (String[]) values.toArray(new String[values.size()]);
    }

}
