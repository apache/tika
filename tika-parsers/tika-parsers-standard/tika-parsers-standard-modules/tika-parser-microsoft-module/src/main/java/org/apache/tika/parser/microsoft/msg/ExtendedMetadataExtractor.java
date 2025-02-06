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
package org.apache.tika.parser.microsoft.msg;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.apache.poi.hsmf.datatypes.PropertyValue;
import org.apache.poi.hsmf.datatypes.Types;

import org.apache.tika.metadata.MAPI;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;

/**
 * This class is intended to handle the metadata that is typically not
 * included in "Note" types. This focuses on Appointments, Tasks, etc.
 */
public class ExtendedMetadataExtractor {

    static Map<Integer, Property> PROPERTIES = new ConcurrentHashMap<>();

    static {
        //TODO -- figure out how these differ and how they overlap with other types
        PROPERTIES.put(0x8003, MAPI.APPT_START_TIME);
        PROPERTIES.put(0x8005, MAPI.APPT_START_TIME);
        PROPERTIES.put(0x8007, MAPI.APPT_START_TIME);
        PROPERTIES.put(0x8009, MAPI.APPT_START_TIME);
        PROPERTIES.put(0x801b, MAPI.APPT_START_TIME);

        PROPERTIES.put(0x8004, MAPI.APPT_END_TIME);
        PROPERTIES.put(0x8006, MAPI.APPT_END_TIME);
        PROPERTIES.put(0x801c, MAPI.APPT_END_TIME);
        PROPERTIES.put(0x8015, MAPI.APPT_END_REPEAT_TIME);
    }

    public static void extract(MAPIMessage msg, Metadata metadata) {
        //
        for (Map.Entry<MAPIProperty, List<PropertyValue>> e : msg
                .getMainChunks()
                .getMessageProperties()
                .getProperties()
                .entrySet()) {
            if (PROPERTIES.containsKey(e.getKey().id)) {
                Property p = PROPERTIES.get(e.getKey().id);
                List<PropertyValue> values = e.getValue();
                if (p.getValueType() == Property.ValueType.DATE) {
                    if (!e.getValue()
                            .isEmpty() && values
                            .get(0)
                            .getActualType() == Types.TIME) {
                        metadata.set(p, (Calendar) values
                                .get(0)
                                .getValue());
                    }
                }
            }
            /*
            Metadata tmp = new Metadata();
            for (PropertyValue v : e.getValue()) {
                if (v instanceof PropertyValue.TimePropertyValue) {
                    MAPIProperty k = e.getKey();
                    //System.out.println(k.name + " " + Integer.toHexString(k.id) +
                    //      " " + k.mapiProperty + " :" + v.getValue());
                    tmp.set(Property.internalDate(Integer.toHexString(k.id)), (Calendar) v.getValue());
                }
            }
            for (String n : tmp.names()) {
                System.out.println(n + " " + tmp.get(n));
            }*/
        }
    }
}
