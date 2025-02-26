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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.poi.hpsf.ClassID;
import org.apache.poi.hsmf.MAPIMessage;
import org.apache.poi.hsmf.datatypes.ByteChunk;
import org.apache.poi.hsmf.datatypes.Chunk;
import org.apache.poi.hsmf.datatypes.MAPIProperty;
import org.apache.poi.hsmf.datatypes.PropertyValue;
import org.apache.poi.hsmf.datatypes.Types;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.metadata.MAPI;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.utils.StringUtils;

/**
 * This class extracts mapi properties as defined in the props_table.txt, which was generated from MS-OXPROPS.
 * For now, this ignores binary and unknown property types.
 */
public class ExtendedMetadataExtractor {

    static Logger LOGGER = LoggerFactory.getLogger(ExtendedMetadataExtractor.class);
    static Map<Integer, List<TikaMapiProperty>> TIKA_MAPI_PROPERTIES = new ConcurrentHashMap<>();
    static Map<Integer, List<TikaMapiProperty>> TIKA_MAPI_LONG_PROPERTIES = new ConcurrentHashMap<>();

    static {
        loadProperties();
    }

    public static void extract(MAPIMessage msg, Metadata metadata) {
        if (msg.getNameIdChunks() == null) {
            return;
        }
        if (msg.getMainChunks() == null || msg.getMainChunks().getRawProperties() == null) {
            return;
        }
        //prep our custom nameIdChunk handler
        TikaNameIdChunks tikaNameIdChunks = new TikaNameIdChunks();
        //short-circuit for files that have an empty nameIdChunk
        long len = 0;
        for (Chunk chunk : msg
                .getNameIdChunks()
                .getAll()) {
            if (chunk == null) {
                continue;
            }
            tikaNameIdChunks.record(chunk);
            if (chunk instanceof ByteChunk) {
                byte[] value = ((ByteChunk)chunk).getValue();
                if (value != null) {
                    len += value.length;
                }
            }
        }
        if (len == 0) {
            return;
        }
        try {
            tikaNameIdChunks.chunksComplete();
        } catch (IllegalStateException e) {
            LOGGER.warn("bad namechunks stream", e);
        }
        for (Map.Entry<MAPIProperty, PropertyValue> e : msg
                .getMainChunks()
                .getRawProperties()
                .entrySet()) {
            //the mapiproperties from POI are the literal storage id for that particular file.
            //Those storage ids must be mapped via the name chunk ids into a known id
            PropertyValue v = e.getValue();
            if (v == null) {
                continue;
            }
            List<MAPITag> mapiTags = tikaNameIdChunks.getTags(e.getKey().id);
            MAPITagPair pair = null;
            for (MAPITag mapiTag : mapiTags) {
                List<TikaMapiProperty> tikaMapiProperties = TIKA_MAPI_LONG_PROPERTIES.get(mapiTag.tagId);
                if (tikaMapiProperties == null) {
                    tikaMapiProperties = TIKA_MAPI_PROPERTIES.get(mapiTag.tagId);
                }
                pair = findMatch(mapiTag, tikaMapiProperties, v);
                if (pair != null) {
                    break;
                }
            }
            updateMetadata(pair, v, metadata);
        }
    }


    private static MAPITagPair findMatch(MAPITag mapiTag, List<TikaMapiProperty> tikaMapiProperties, PropertyValue propertyValue) {
        if (mapiTag == null || tikaMapiProperties == null || propertyValue == null) {
            return null;
        }
        for (TikaMapiProperty tikaMapiProperty : tikaMapiProperties) {
            if (!mapiTag.classID.equals(tikaMapiProperty.classID)) {
                continue;
            }
            if (tikaMapiProperty.types == null || tikaMapiProperty.types.isEmpty()) {
                continue;
            }
            for (Types.MAPIType type : tikaMapiProperty.types) {
                if (propertyValue
                        .getActualType()
                        .equals(type)) {
                    return new MAPITagPair(mapiTag, tikaMapiProperty);
                }
            }
        }
        return null;
    }


    private static void updateMetadata(MAPITagPair pair, PropertyValue propertyValue, Metadata metadata) {
        if (pair == null || propertyValue == null) {
            return;
        }
        if (!includeType(propertyValue)) {
            return;
        }
        String key = MAPI.PREFIX_MAPI_PROPERTY + pair.tikaMapiProperty.name;
        Types.MAPIType type = propertyValue.getActualType();
        if (type == Types.TIME || type == Types.MV_TIME || type == Types.APP_TIME || type == Types.MV_APP_TIME) {
            Calendar calendar = (Calendar) propertyValue.getValue();
            String calendarString = calendar
                    .toInstant()
                    .truncatedTo(ChronoUnit.SECONDS)
                    .toString();
            metadata.add(key, calendarString);
        } else if (type == Types.BOOLEAN) {
            Boolean val = (Boolean)propertyValue.getValue();
            if (val == null) {
                return;
            }
            metadata.add(key, Boolean.toString(val));
        } else if (! StringUtils.isBlank(propertyValue.toString())) {
            metadata.add(key, propertyValue.toString());
        }

    }

    private static boolean includeType(PropertyValue propertyValue) {
        Types.MAPIType mapiType = propertyValue.getActualType();
        if (mapiType == Types.BINARY || mapiType == Types.UNKNOWN || mapiType == Types.UNSPECIFIED || mapiType == Types.DIRECTORY || mapiType.isPointer()) {
            return false;
        }
        return true;
    }

    private static class TikaMapiProperty {
        String name;
        ClassID classID; // can be null
        List<Types.MAPIType> types;
        String refShort;

        TikaMapiProperty(String name, ClassID classID, List<Types.MAPIType> types, String refShort) {
            this.name = name;
            this.classID = classID;
            this.types = types;
            this.refShort = refShort;
        }
    }

    private static void loadProperties() {
        Map<String, ClassID> knownClassIds = new HashMap<>();
        for (TikaNameIdChunks.PredefinedPropertySet set : TikaNameIdChunks.PredefinedPropertySet.values()) {
            knownClassIds.put(set
                    .getClassID()
                    .toUUIDString(), set.getClassID());
        }
        for (TikaNameIdChunks.PropertySetType setType : TikaNameIdChunks.PropertySetType.values()) {
            knownClassIds.put(setType
                    .getClassID()
                    .toUUIDString(), setType.getClassID());
        }
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(ExtendedMetadataExtractor.class.getResourceAsStream("/org/apache/tika/parser/microsoft/msg/props_table.txt"), UTF_8))) {
            String line = r.readLine();
            while (line != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    line = r.readLine();
                    continue;
                }
                String[] cols = line.split("\\|");
                if (cols.length != 11) {
                    throw new IllegalArgumentException("column count must == 11: " + line);
                }
                String name = cols[1].trim();
                ClassID classID = parseClassId(cols[3], knownClassIds);
                List<Types.MAPIType> types = parseDataTypes(cols[7].split(";"));
                String ref = cols[10];

                String shortId = cols[5];
                String longId = cols[6];
                if (!StringUtils.isBlank(shortId)) {
                    int id = Integer.parseInt(shortId.substring(2), 16);
                    List<TikaMapiProperty> props = TIKA_MAPI_PROPERTIES.computeIfAbsent(id, k -> new ArrayList<>());
                    props.add(new TikaMapiProperty(name, classID, types, ref));
                } else if (!StringUtils.isBlank(longId)) {
                    //remove leading "0x"
                    long id = Long.parseLong(longId.substring(2), 16);
                    if (id > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException("id must actually be within int range");
                    }
                    int intId = (int) id;
                    List<TikaMapiProperty> props = TIKA_MAPI_LONG_PROPERTIES.computeIfAbsent(intId, k -> new ArrayList<>());
                    props.add(new TikaMapiProperty(name, classID, types, ref));
                } else {
                    // some properties don't have an id
                }

                line = r.readLine();
            }
        } catch (IOException e) {
            throw new IllegalStateException("can't find props_table.txt?!");
        }
    }

    private static ClassID parseClassId(String s, Map<String, ClassID> knownClassIDs) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
        int space = s.indexOf(" ");
        if (space < 0) {
            return null;
        }
        s = s
                .substring(space)
                .replaceAll("[\\{\\}]", "")
                .trim();
        if (knownClassIDs.containsKey(s)) {
            return knownClassIDs.get(s);
        }
        LOGGER.warn("Add '{}' to list of known property set IDs", s);
        ClassID classID = new ClassID(s);
        knownClassIDs.put(classID.toUUIDString(), classID);
        return classID;
    }

    private static class MAPITagPair {
        final MAPITag mapiTag;
        final TikaMapiProperty tikaMapiProperty;

        public MAPITagPair(MAPITag mapiTag, TikaMapiProperty tikaMapiProperty) {
            this.mapiTag = mapiTag;
            this.tikaMapiProperty = tikaMapiProperty;
        }
    }


    private static List<Types.MAPIType> parseDataTypes(String[] arr) {
        if (arr.length == 1) {
            Types.MAPIType type = parseDataType(arr[0]);
            if (type != null) {
                return List.of(type);
            }
            return Collections.EMPTY_LIST;
        }
        List<Types.MAPIType> types = new ArrayList<>();
        for (String s : arr) {
            Types.MAPIType type = parseDataType(s);
            if (type != null) {
                types.add(type);
            }
        }
        return types;
    }

    private static Types.MAPIType parseDataType(String s) {
        if (StringUtils.isBlank(s)) {
            return null;
        }
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

}
