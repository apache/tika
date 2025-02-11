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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.apache.poi.hsmf.datatypes.PropertyValue;
import org.apache.poi.hsmf.datatypes.Types;

import org.apache.tika.metadata.MAPI;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Property;
import org.apache.tika.parser.microsoft.OutlookExtractor;

/**
 * This class is intended to handle the metadata that is typically not
 * included in "Note" types. This focuses on Appointments, Tasks, etc.
 */
public class ExtendedMetadataExtractor {

    static Map<Integer, List<TikaMapiProperty>> TIKA_MAPI_PROPERTIES = new ConcurrentHashMap<>();

    static {
        loadProperties();
    }

    private static void loadProperties() {
        Set<String> areas = new TreeSet<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(
                        OutlookExtractor.class.getResourceAsStream("/org/apache/tika/parser/microsoft/msg/PIDShortID.csv"), UTF_8))) {
            String line = r.readLine();
            while (line != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    line = r.readLine();
                    continue;
                }
                String[] cols = line.split(";");
                if (cols.length < 6 || cols.length > 7) {
                    throw new IllegalArgumentException("column count must be >=6 and <= 7");
                }
                String idString = unquote(cols[0]);
                //ignore intial "0X"
                int id = Integer.parseInt(idString, 2, idString.length(), 16);
                String pIdName = unquote(cols[1]);

                List<Types.MAPIType> types = parseDataTypes(unquote(cols[3]).split(":"));

                List<TikaMapiProperty> props = TIKA_MAPI_PROPERTIES.computeIfAbsent(id, k -> new ArrayList<>());
                props.add(new TikaMapiProperty(id, pIdName, types));

                line = r.readLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("can't find PIDShortID.csv?!");
        }
    }

    private static List<Types.MAPIType> parseDataTypes(String[] arr) {
        if (arr.length == 1) {
            return List.of(parseDataType(arr[0]));
        }
        List<Types.MAPIType> types = new ArrayList<>();
        for (String s : arr) {
            types.add(parseDataType(s));
        }
        return types;
    }

    private static Types.MAPIType parseDataType(String s) {
        String[] parts = s.split(", ");
        if (parts.length != 2) {
            throw new IllegalArgumentException("expected two parts: " + s);
        }
        String num = parts[1];
        if (num.startsWith("0x")) {
            num = num.substring(2);
        }
        int id = Integer.parseInt(num, 16);
        Types.MAPIType type = Types.getById(id);
        if (type == null) {
            //TODO:
            /*
                PtypRestriction, 0x00FD
                PtypRuleAction, 0x00FE
                PtypServerId, 0x00FB
             */
            return Types.createCustom(id);
        }
        return type;
    }

    private static String unquote(String col) {
        if (col.startsWith("\"") && col.endsWith("\"")) {
            //this is not robust, but we're running it
            // on known data.
            return col.substring(1, col.length() - 1).trim();
        } else {
            throw new IllegalArgumentException("cell must start and end with a quote: '" + col + "'");
        }
    }


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
//        PROPERTIES.put(0x8015, MAPI.APPT_END_REPEAT_TIME);
    }

    public static void extract(MAPIMessage msg, Metadata metadata) {

        for (Map.Entry<MAPIProperty, List<PropertyValue>> e : msg
                .getMainChunks()
                .getMessageProperties()
                .getProperties()
                .entrySet()) {
            if (e.getValue().isEmpty()) {
                continue;
            }
            MAPIProperty mapiProperty = e.getKey();
            if (TIKA_MAPI_PROPERTIES.containsKey(mapiProperty.id)) {
                List<TikaMapiProperty> tikaMapiProperties = TIKA_MAPI_PROPERTIES.get(mapiProperty.id);
                for (TikaMapiProperty tikaMapiProperty : tikaMapiProperties) {
                    for (PropertyValue propertyValue : e.getValue()) {
                        if (tikaMapiProperty.containsType(propertyValue.getActualType())) {
                            updateMetadata(tikaMapiProperty, propertyValue, metadata);
                        }
                    }
                }
            }
        }
    }

    private static void updateMetadata(TikaMapiProperty tikaMapiProperty, PropertyValue propertyValue, Metadata metadata) {
        String key = "mapi-raw:" + tikaMapiProperty.name;
        if (propertyValue.getActualType() == Types.TIME) {
            Calendar calendar = (Calendar) propertyValue.getValue();
            String calendarString = calendar.toInstant().truncatedTo(ChronoUnit.SECONDS).toString();
            metadata.add(key, calendarString);
        } else {
            metadata.add(key, propertyValue.toString());
        }
    }

    private static class TikaMapiProperty {
        int id;
        String name;
        List<Types.MAPIType> types;

        public TikaMapiProperty(int id, String name, List<Types.MAPIType> types) {
            this.id = id;
            this.name = name;
            this.types = types;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public List<Types.MAPIType> getTypes() {
            return types;
        }

        public boolean containsType(Types.MAPIType type) {
            return types.contains(type);
        }
    }
}
