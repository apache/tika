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
package org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.onenote.fsshttpb.util.BitReader;

public abstract class StreamObject {

    private static final Set<StreamObjectTypeHeaderStart> compoundTypes = new HashSet<>(
            Arrays.asList(StreamObjectTypeHeaderStart.DataElement,
                    StreamObjectTypeHeaderStart.Knowledge,
                    StreamObjectTypeHeaderStart.CellKnowledge,
                    StreamObjectTypeHeaderStart.DataElementPackage,
                    StreamObjectTypeHeaderStart.ObjectGroupDeclarations,
                    StreamObjectTypeHeaderStart.ObjectGroupData,
                    StreamObjectTypeHeaderStart.WaterlineKnowledge,
                    StreamObjectTypeHeaderStart.ContentTagKnowledge,
                    StreamObjectTypeHeaderStart.Request,
                    StreamObjectTypeHeaderStart.FsshttpbSubResponse,
                    StreamObjectTypeHeaderStart.SubRequest,
                    StreamObjectTypeHeaderStart.ReadAccessResponse,
                    StreamObjectTypeHeaderStart.SpecializedKnowledge,
                    StreamObjectTypeHeaderStart.WriteAccessResponse,
                    StreamObjectTypeHeaderStart.QueryChangesFilter,
                    StreamObjectTypeHeaderStart.ResponseError,
                    StreamObjectTypeHeaderStart.UserAgent,
                    StreamObjectTypeHeaderStart.FragmentKnowledge,
                    StreamObjectTypeHeaderStart.ObjectGroupMetadataDeclarations,
                    StreamObjectTypeHeaderStart.LeafNodeObject,
                    StreamObjectTypeHeaderStart.IntermediateNodeObject,
                    StreamObjectTypeHeaderStart.TargetPartitionId));

    private static final Map<StreamObjectTypeHeaderStart, Class> streamObjectTypeMapping;

    static {
        streamObjectTypeMapping = new HashMap<>();
        for (StreamObjectTypeHeaderStart value : StreamObjectTypeHeaderStart.values()) {
            String className = StreamObject.class.getPackage().getName() + "." + value.name();
            try {
                streamObjectTypeMapping.put(value,
                        Class.forName(className, false, StreamObject.class.getClassLoader()));
            } catch (ClassNotFoundException e) {
                // not every type has a ported class
            }
        }
    }

    StreamObjectHeaderEnd streamObjectHeaderEnd;
    private StreamObjectTypeHeaderStart streamObjectType;
    private int lengthOfItems;
    private StreamObjectHeaderStart streamObjectHeaderStart;

    protected StreamObject(StreamObjectTypeHeaderStart streamObjectType) {
        this.streamObjectType = streamObjectType;
    }

    public static Set<StreamObjectTypeHeaderStart> getCompoundTypes() {
        return compoundTypes;
    }

    public static Map<StreamObjectTypeHeaderStart, Class> getStreamObjectTypeMapping() {
        return streamObjectTypeMapping;
    }

    public static <T extends StreamObject> T getCurrent(byte[] byteArray, AtomicInteger index,
                                                        Class<T> clazz)
            throws TikaException, IOException {
        AtomicInteger tmpIndex = new AtomicInteger(index.get());
        int length;
        AtomicReference<StreamObjectHeaderStart> streamObjectHeader = new AtomicReference<>();
        if ((length =
                StreamObjectHeaderStart.tryParse(byteArray, tmpIndex.get(), streamObjectHeader)) ==
                0) {
            throw new StreamObjectParseErrorException(tmpIndex.get(), clazz.getName(),
                    "Failed to extract either 16bit or 32bit stream object header in the current index.",
                    null);
        }

        tmpIndex.addAndGet(length);

        StreamObject streamObject =
                parseStreamObject(streamObjectHeader.get(), byteArray, tmpIndex);

        if (!streamObject.getClass().equals(clazz)) {
            String destClassName = "(null)";
            if (streamObjectTypeMapping.containsKey(streamObjectHeader.get().type)) {
                destClassName =
                        streamObjectTypeMapping.get(streamObjectHeader.get().type).getName();
            }
            throw new StreamObjectParseErrorException(tmpIndex.get(), clazz.getName(),
                    String.format(Locale.US,
                            "Failed to get stream object as expect type %s, actual type is %s",
                            clazz.getName(), destClassName), null);
        }

        index.set(tmpIndex.get());
        return (T) streamObject;
    }

    public static StreamObject parseStreamObject(StreamObjectHeaderStart header, byte[] byteArray,
                                                 AtomicInteger index) throws IOException, TikaException {
        if (streamObjectTypeMapping.containsKey(header.type)) {
            Class headerTypeClass = streamObjectTypeMapping.get(header.type);
            StreamObject streamObject;
            try {
                streamObject =
                        (StreamObject) headerTypeClass.getDeclaredConstructor().newInstance();
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException |
                     InvocationTargetException e) {
                throw new TikaException("Could not instantiate class " + headerTypeClass, e);
            }

            int res = streamObject.deserializeFromByteArray(header, byteArray, index.get());
            index.addAndGet(res);

            return streamObject;
        }

        int tmpIndex = index.get();
        tmpIndex -=
                header.headerType == StreamObjectHeaderStart.STREAM_OBJECT_HEADER_START_16_BIT ? 2 : 4;
        throw new StreamObjectParseErrorException(tmpIndex, "Unknown", String.format(Locale.US,
                "Failed to create the specified stream object instance, the type %s of stream object " +
                        "header in the current index is not defined", header.type.getIntVal()),
                null);
    }

    public static <T extends StreamObject> boolean tryGetCurrent(byte[] byteArray,
                                                                 AtomicInteger index,
                                                                 AtomicReference<T> streamObject,
                                                                 Class<T> clazz)
            throws TikaException, IOException {
        AtomicInteger tmpIndex = new AtomicInteger(index.get());

        int length = 0;
        AtomicReference<StreamObjectHeaderStart> streamObjectHeader = new AtomicReference<>();
        if ((length =
                StreamObjectHeaderStart.tryParse(byteArray, tmpIndex.get(), streamObjectHeader)) ==
                0) {
            return false;
        }

        tmpIndex.addAndGet(length);
        if (streamObjectTypeMapping.containsKey(streamObjectHeader.get().type) &&
                streamObjectTypeMapping.get(streamObjectHeader.get().type).equals(clazz)) {
            streamObject.set((T) parseStreamObject(streamObjectHeader.get(), byteArray, tmpIndex));
        } else {
            return false;
        }

        index.set(tmpIndex.get());
        return true;
    }

    public int deserializeFromByteArray(StreamObjectHeaderStart header, byte[] byteArray,
                                        int startIndex) throws IOException, TikaException {
        this.streamObjectType = header.type;
        this.lengthOfItems = header.length;

        if (header instanceof StreamObjectHeaderStart32bit) {
            if (header.length == 32767) {
                this.lengthOfItems =
                        (int) ((StreamObjectHeaderStart32bit) header).largeLength.getDecodedValue();
            }
        }

        AtomicInteger index = new AtomicInteger(startIndex);
        this.streamObjectHeaderStart = header;
        this.deserializeItemsFromByteArray(byteArray, index, this.lengthOfItems);

        if (compoundTypes.contains(this.streamObjectType)) {
            StreamObjectHeaderEnd end = null;
            BitReader bitReader = new BitReader(byteArray, index.get());
            int aField = bitReader.readInt32(2);
            if (aField == 0x1) {
                end = BasicObject.parse(byteArray, index, StreamObjectHeaderEnd8bit.class);
            }
            if (aField == 0x3) {
                end = BasicObject.parse(byteArray, index, StreamObjectHeaderEnd16bit.class);
            }

            if (end.type.getIntVal() != this.streamObjectType.getIntVal()) {
                throw new StreamObjectParseErrorException(index.get(), null,
                        "Unexpected the stream header end value " +
                                this.streamObjectType.getIntVal(), null);
            }

            this.streamObjectHeaderEnd = end;
        }

        return index.get() - startIndex;
    }

    /** Advances {@code currentIndex} to {@code index}, which must be the items' end. */
    protected static void endItems(AtomicInteger currentIndex, AtomicInteger index,
                                   int lengthOfItems, String name)
            throws StreamObjectParseErrorException {
        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new StreamObjectParseErrorException(currentIndex.get(), name,
                    "Stream object over-parse error", null);
        }
        currentIndex.set(index.get());
    }

    protected abstract void deserializeItemsFromByteArray(byte[] byteArray,
                                                          AtomicInteger currentIndex,
                                                          int lengthOfItems)
            throws TikaException, IOException;
}
