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
package org.apache.tika.parser.image;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.metadata.TikaCoreProperties;

/**
 * Knowns about all declared {@link Metadata} fields.
 * Didn't find this functionality anywhere so it was added for
 * ImageMetadataExtractor, but it can be generalized.
 */
public abstract class MetadataFields {
    
    private static HashSet<String> known;
    
    private static void setKnownForClass(Class<?> clazz) {
        Field[] fields = clazz.getFields();
        for (Field f : fields) {
            int mod = f.getModifiers();
            if (Modifier.isPublic(mod) && Modifier.isStatic(mod) && Modifier.isFinal(mod)) {
                Class<?> c = f.getType();
                if (String.class.equals(c)) {
                    try {
                        String p = (String) f.get(null);
                        if (p != null) {
                            known.add(p);
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
                if (Property.class.isAssignableFrom(c)) {
                    try {
                        Property p = (Property) f.get(null);
                        if (p != null) {
                            known.add(p.getName());
                        }
                    } catch (IllegalArgumentException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
    
    static {
        known = new HashSet<String>();
        setKnownForClass(TikaCoreProperties.class);
        setKnownForClass(Metadata.class);
    }
    
    public static boolean isMetadataField(String name) {
        return known.contains(name);
    }
    
    public static boolean isMetadataField(Property property) {
        return known.contains(property.getName());
    }
    
}
