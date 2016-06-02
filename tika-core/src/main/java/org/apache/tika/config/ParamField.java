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
package org.apache.tika.config;

import org.apache.tika.exception.TikaConfigException;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * This class stores metdata for {@link Field} annotation are used to map them
 * to {@link Param} at runtime
 *
 * @since Apache Tika 1.14
 */
public class ParamField {

    public static final String DEFAULT = "#default";

    //NOTE: since (primitive type) is NOT AssignableFrom (BoxedType),
    // we just use boxed type for everything!
    // Example : short.class.isAssignableFrom(Short.class) ? false
    private static final Map<Class<?>, Class<?>> PRIMITIVE_MAP
            = new HashMap<Class<?>, Class<?>>(){{
        put(int.class, Integer.class);
        put(short.class, Short.class);
        put(boolean.class, Boolean.class);
        put(long.class, Long.class);
        put(float.class, Float.class);
        put(double.class, Double.class);
    }};

    private java.lang.reflect.Field field;
    private Method setter;
    private String name;
    private Class<?> type;
    private boolean required;

    /**
     * Creates a ParamField object
     * @param member a field or method which has {@link Field} annotation
     */
    public ParamField(AccessibleObject member) throws TikaConfigException {
        if (member instanceof java.lang.reflect.Field) {
            field = (java.lang.reflect.Field) member;
        } else {
            setter = (Method) member;
        }

        Field annotation = member.getAnnotation(Field.class);
        required = annotation.required();
        name = retrieveParamName(annotation);
        type = retrieveType();
    }

    public java.lang.reflect.Field getField() {
        return field;
    }

    public Method getSetter() {
        return setter;
    }

    public String getName() {
        return name;
    }

    public Class<?> getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    /**
     * Sets given value to the annotated field of bean
     * @param bean bean with annotation for field
     * @param value value of field
     * @throws IllegalAccessException when it occurs
     * @throws InvocationTargetException when it occurs
     */
    public void assignValue(Object bean, Object value)
            throws IllegalAccessException, InvocationTargetException {
        if (field != null) {
            field.set(bean, value);
        } else {
            setter.invoke(bean, value);
        }
    }

    private Class retrieveType() throws TikaConfigException {
        Class type;
        if (field != null) {
            type = field.getType();
        } else {
            Class[] params = setter.getParameterTypes();
            if (params.length != 1) {
                String msg = "Invalid setter method. Must have one and only one parameter. ";
                if (setter.getName().startsWith("get")) {
                    msg += "Perhaps the annotation is misplaced on " +
                            setter.getName() + " while a set'X' is expected?";
                }
                throw new TikaConfigException(msg);
            }
            type = params[0];
        }
        if (type.isPrimitive() && PRIMITIVE_MAP.containsKey(type)){
            type = PRIMITIVE_MAP.get(type); //primitive types have hard time
        }
        return type;
    }

    private String retrieveParamName(Field annotation) {
        String name;
        if (annotation.name().equals(DEFAULT)) {
            if (field != null) {
                name = field.getName();
            } else {
                String setterName = setter.getName();
                if (setterName.startsWith("set") && setterName.length() > 3) {
                    name = setterName.substring(3, 4).toLowerCase(Locale.ROOT)
                            + setterName.substring(4);
                } else {
                    name = setter.getName();
                }
            }
        } else {
            name = annotation.name();
        }
        return name;
    }

    @Override
    public String toString() {
        return "ParamField{" +
                "name='" + name + '\'' +
                ", type=" + type +
                ", required=" + required +
                '}';
    }
}
