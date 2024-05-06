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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.apache.tika.config.ServiceLoader;

/** Service Loading and Ordering related utils */
public class ServiceLoaderUtils {
    /**
     * Sorts a list of loaded classes, so that non-Tika ones come before Tika ones, and otherwise in
     * reverse alphabetical order
     */
    public static <T> void sortLoadedClasses(List<T> loaded) {
        loaded.sort(CompareUtils::compareClassName);
    }

    /**
     * Loads a class and instantiates it
     *
     * @param className service class name
     * @param <T> service type
     * @return instance of service
     */
    public static <T> T newInstance(String className) {
        return newInstance(className, ServiceLoader.class.getClassLoader());
    }

    /**
     * Loads a class and instantiates it
     *
     * @param className service class name
     * @param loader class loader
     * @param <T> service type
     * @return instance of service
     */
    public static <T> T newInstance(String className, ClassLoader loader) {
        try {
            return ((Class<T>) Class.forName(className, true, loader))
                    .getDeclaredConstructor()
                    .newInstance();
        } catch (ClassNotFoundException
                | InstantiationException
                | IllegalAccessException
                | NoSuchMethodException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Loads a class and instantiates it. If the class can be initialized with a ServiceLoader, the
     * ServiceLoader constructor is used. Otherwise, a zero arg newInstance() is called.
     *
     * @param klass class to build
     * @param loader service loader
     * @param <T> service type
     * @return instance of service
     */
    public static <T> T newInstance(Class klass, ServiceLoader loader) {
        try {
            try {
                Constructor<T> constructor = klass.getDeclaredConstructor(ServiceLoader.class);
                return constructor.newInstance(loader);
            } catch (NoSuchMethodException e) {
                return (T) klass.getDeclaredConstructor().newInstance();
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        } catch (InstantiationException
                | IllegalAccessException
                | NoSuchMethodException
                | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
