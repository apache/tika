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
package org.apache.tika.utils;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.tika.config.Field;
import org.apache.tika.config.Param;
import org.apache.tika.config.ParamField;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class contains utilities for dealing with tika annotations
 *
 * @since Apache Tika 1.14
 */
public class AnnotationUtils {
    private static final Logger LOG = LoggerFactory.getLogger(AnnotationUtils.class);

    /** Cache for annotations for Bean classes which have {@link Field} */
    private static final Map<Class<?>, List<ParamField>> PARAM_INFO = new HashMap<>();

    /**
     * Collects all the fields and methods for an annotation
     *
     * @param clazz bean class with annotations
     * @param annotation annotation class
     * @return list of accessible objects such as fields and methods
     */
    private static List<AccessibleObject> collectInfo(
            Class<?> clazz, Class<? extends Annotation> annotation) {

        Class superClazz = clazz;
        List<AccessibleObject> members = new ArrayList<>();
        List<AccessibleObject> annotatedMembers = new ArrayList<>();
        // walk through the inheritance chain
        while (superClazz != null && superClazz != Object.class) {
            members.addAll(Arrays.asList(superClazz.getDeclaredFields()));
            members.addAll(Arrays.asList(superClazz.getDeclaredMethods()));
            superClazz = superClazz.getSuperclass();
        }

        for (final AccessibleObject member : members) {
            if (member.isAnnotationPresent(annotation)) {
                member.setAccessible(true);
                annotatedMembers.add(member);
            }
        }
        return annotatedMembers;
    }

    /**
     * Assigns the param values to bean
     *
     * @throws TikaConfigException when an error occurs while assigning params
     */
    public static void assignFieldParams(Object bean, Map<String, Param> params)
            throws TikaConfigException {
        Class<?> beanClass = bean.getClass();
        if (!PARAM_INFO.containsKey(beanClass)) {
            synchronized (TikaConfig.class) {
                if (!PARAM_INFO.containsKey(beanClass)) {
                    List<AccessibleObject> aObjs =
                            collectInfo(beanClass, org.apache.tika.config.Field.class);
                    List<ParamField> fields = new ArrayList<>(aObjs.size());

                    for (AccessibleObject aObj : aObjs) {
                        fields.add(new ParamField(aObj));
                    }
                    PARAM_INFO.put(beanClass, fields);
                }
            }
        }

        List<ParamField> fields = PARAM_INFO.get(beanClass);

        for (ParamField field : fields) {
            Param<?> param = params.get(field.getName());
            if (param != null) {
                if (field.getType().isAssignableFrom(param.getType())) {
                    try {
                        field.assignValue(bean, param.getValue());
                    } catch (InvocationTargetException e) {
                        LOG.error(
                                "Error assigning value '{}' to '{}'",
                                param.getValue(),
                                param.getName());
                        final Throwable cause = e.getCause() == null ? e : e.getCause();
                        throw new TikaConfigException(cause.getMessage(), cause);
                    } catch (IllegalAccessException e) {
                        LOG.error(
                                "Error assigning value '{}' to '{}'",
                                param.getValue(),
                                param.getName());
                        throw new TikaConfigException(e.getMessage(), e);
                    }
                } else {
                    String msg =
                            String.format(
                                    Locale.ROOT,
                                    "Value '%s' of type '%s' can't be"
                                            + " assigned to field '%s' of defined type '%s'",
                                    param.getValue(),
                                    param.getValue().getClass(),
                                    field.getName(),
                                    field.getType());
                    throw new TikaConfigException(msg);
                }
            } else if (field.isRequired()) {
                // param not supplied but field is declared as required?
                String msg =
                        String.format(
                                Locale.ROOT,
                                "Param %s is required for %s," + " but it is not given in config.",
                                field.getName(),
                                bean.getClass().getName());
                throw new TikaConfigException(msg);
            } else {
                LOG.debug("Param not supplied, field is not mandatory");
            }
        }
    }
}
