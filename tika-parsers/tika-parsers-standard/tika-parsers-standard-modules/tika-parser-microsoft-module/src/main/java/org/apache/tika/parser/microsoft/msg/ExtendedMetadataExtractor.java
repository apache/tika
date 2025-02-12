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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import org.apache.tika.parser.microsoft.OutlookExtractor;
import org.apache.tika.utils.StringUtils;

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
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(OutlookExtractor.class.getResourceAsStream("/org/apache/tika/parser/microsoft/msg/PIDShortID.csv"), UTF_8))) {
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
            return col
                    .substring(1, col.length() - 1)
                    .trim();
        } else {
            throw new IllegalArgumentException("cell must start and end with a quote: '" + col + "'");
        }
    }


    static Map<Integer, Property> PROPERTIES = new ConcurrentHashMap<>();

    static {
        //PidLidAppointmentStartWhole
        PROPERTIES.put(0x820D, MAPI.APPT_START_TIME);
        //PidLidAppointmentProposedStartWhole
        PROPERTIES.put(0x8250, MAPI.APPT_PROPOSED_START_TIME);
        //PidLidAppointmentEndWhole
        PROPERTIES.put(0x820E, MAPI.APPT_END_TIME);
        //PidLidAppointmentProposedEndWhole
        PROPERTIES.put(0x8251, MAPI.APPT_PROPOSED_END_TIME);

        PROPERTIES.put(0x8005, MAPI.REMINDER_TIME);
        PROPERTIES.put(0x8006, MAPI.REMINDER_SIGNAL_TIME);

        //there are other values for this key see
        PROPERTIES.put(0x8009, MAPI.APPT_LOCATION);
    }

    public static void extract(MAPIMessage msg, Metadata metadata) {
        //TODO -- we should map properties to message class types so that we're not
        //reporting contact metadata for an appointment etc...
        //I started down this path with PIDShortID.csv's "area" field,
        //but that requires quite a bit of work.
        //perhaps we could map by Defining Reference?
        for (Map.Entry<MAPIProperty, List<PropertyValue>> e : msg
                .getMainChunks()
                .getMessageProperties()
                .getProperties()
                .entrySet()) {
            List<PropertyValue> props = e.getValue();

            if (props == null || props.isEmpty()) {
                continue;
            }
            //we could allow user configured levels for extended properties
            //small, medium, large...
            MAPIProperty mapiProperty = e.getKey();
            boolean added = false;
            if (PROPERTIES.containsKey(mapiProperty.id)) {
                PropertyValue propertyValue = props.get(0);
                added = addKnownProperty(PROPERTIES.get(mapiProperty.id), propertyValue, metadata);
            }

            if (!added && TIKA_MAPI_PROPERTIES.containsKey(mapiProperty.id)) {
                List<TikaMapiProperty> tikaMapiProperties = TIKA_MAPI_PROPERTIES.get(mapiProperty.id);
                for (TikaMapiProperty tikaMapiProperty : tikaMapiProperties) {
                    for (PropertyValue propertyValue : props) {
                        if (tikaMapiProperty.containsType(propertyValue.getActualType())) {
                            added = updateMetadata(tikaMapiProperty, propertyValue, metadata);
                        }
                    }
                }
            }
            if (!added) {
                for (PropertyValue propertyValue : e.getValue()) {
                    //narrowly scoped to current interests...maybe broaden out?
                    if (propertyValue.getActualType() == Types.TIME) {
                        String key = MAPI.PREFIX_MAPI_RAW_META + "unknown-date-prop:" +
                                StringUtils.leftPad(Integer.toHexString(propertyValue.getProperty().id), 4, '0');
                        Calendar cal = (Calendar) propertyValue.getValue();
                        //truncate to seconds? toInstant().truncatedTo(ChronoUnit.SECONDS)....
                        metadata.add(key, cal
                                .toInstant()
                                .toString());
                    }
                }
            }
        }
    }

    private static boolean addKnownProperty(Property property, PropertyValue propertyValue, Metadata metadata) {
        //this is quite limited.
        if (propertyValue.getActualType() == Types.TIME && property.getValueType() == Property.ValueType.DATE) {
            metadata.set(property, (Calendar) propertyValue.getValue());
            return true;
        } else if (isString(propertyValue) && property.getValueType() == Property.ValueType.TEXT) {
            metadata.set(property, propertyValue.toString());
            return true;
        }
        return false;
    }


    private static boolean updateMetadata(TikaMapiProperty tikaMapiProperty, PropertyValue propertyValue, Metadata metadata) {
        String key = MAPI.PREFIX_MAPI_RAW_META + tikaMapiProperty.name;
        if (propertyValue.getActualType() == Types.TIME) {
            Calendar calendar = (Calendar) propertyValue.getValue();
            String calendarString = calendar
                    .toInstant()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .toString();
            metadata.add(key, calendarString);
            return true;
        } else if (shouldIncludeUnknownType(propertyValue)) {
            metadata.add(key, propertyValue.toString());
            return true;
        }
        return false;
    }

    private static boolean shouldIncludeUnknownType(PropertyValue propertyValue) {
        Types.MAPIType mapiType = propertyValue.getActualType();
        if (mapiType == Types.BINARY || mapiType == Types.UNKNOWN || mapiType == Types.UNSPECIFIED || mapiType == Types.DIRECTORY || mapiType.isPointer()) {
            return false;
        }
        return true;
    }

    private static boolean isString(PropertyValue propertyValue) {
        Types.MAPIType mapiType = propertyValue.getActualType();
        return mapiType == Types.ASCII_STRING || mapiType == Types.MV_ASCII_STRING || mapiType == Types.MV_UNICODE_STRING || mapiType == Types.UNICODE_STRING;
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
