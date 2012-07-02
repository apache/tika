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

import org.apache.tika.metadata.Property.PropertyType;
import org.apache.tika.metadata.Property.ValueType;


/**
 * XMP property definition violation exception. This is thrown when
 * you try to set a {@link Property} value with an incorrect type,
 * such as storing an Integer when the property is of type Date.
 *
 * @since Apache Tika 0.8
 */
public final class PropertyTypeException extends IllegalArgumentException {

    public PropertyTypeException(String msg) {
        super(msg);
    }

    public PropertyTypeException(PropertyType expected, PropertyType found) {
        super("Expected a property of type " + expected + ", but received " + found);
    }

    public PropertyTypeException(ValueType expected, ValueType found) {
        super("Expected a property with a " + expected + " value, but received a " + found);
    }

    public PropertyTypeException(PropertyType unsupportedPropertyType) {
        super((unsupportedPropertyType != PropertyType.COMPOSITE)
                ? unsupportedPropertyType + " is not supported"
                : "Composite Properties must not include other Composite"
                   + " Properties as either Primary or Secondary");
    }
}
