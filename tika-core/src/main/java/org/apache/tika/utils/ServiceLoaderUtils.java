/**
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

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.tika.config.ServiceLoader;

/**
 * Service Loading and Ordering related utils
 */
public class ServiceLoaderUtils {
    /**
     * Sorts a list of loaded classes, so that non-Tika ones come
     *  before Tika ones, and otherwise in reverse alphabetical order
     */
    public static <T> void sortLoadedClasses(List<T> loaded) {
        Collections.sort(loaded, new Comparator<T>() {
            public int compare(T c1, T c2) {
                String n1 = c1.getClass().getName();
                String n2 = c2.getClass().getName();
                boolean t1 = n1.startsWith("org.apache.tika.");
                boolean t2 = n2.startsWith("org.apache.tika.");
                if (t1 == t2) {
                    return n1.compareTo(n2);
                } else if (t1) {
                    return -1;
                } else {
                    return 1;
                }
            }
        });
    }

    /**
     * Loads a class and instantiates it
     * @param className service class name
     * @param <T> service type
     * @return instance of service
     */
    public static <T> T newInstance(String className) {
        return newInstance(className, ServiceLoader.class.getClassLoader());
    }

    /**
     * Loads a class and instantiates it
     * @param className service class name
     * @param loader class loader
     * @param <T> service type
     * @return instance of service
     */
    public static <T> T newInstance(String className, ClassLoader loader){
        try {
            Class loadedClass = Class.forName(className, true, loader);
            Class<T> castedClass = loadedClass;
            T instance = castedClass.newInstance();
            return instance;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
