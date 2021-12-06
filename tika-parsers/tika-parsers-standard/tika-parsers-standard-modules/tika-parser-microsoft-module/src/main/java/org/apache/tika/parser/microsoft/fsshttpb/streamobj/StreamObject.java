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
package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.tika.parser.microsoft.fsshttpb.IFSSHTTPBSerializable;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.fsshttpb.util.BitReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StreamObject implements IFSSHTTPBSerializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamObject.class);

    /**
     * Hash set contains the StreamObjectTypeHeaderStart type.
     */
    private static Set<StreamObjectTypeHeaderStart> compoundTypes = new HashSet<>(Arrays.asList(
            StreamObjectTypeHeaderStart.DataElement,
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

    /**
     * The dictionary of StreamObjectTypeHeaderStart and type.
     */
    private static Map<StreamObjectTypeHeaderStart, Class> streamObjectTypeMapping;

    static {
        streamObjectTypeMapping = new HashMap<>();
        for (StreamObjectTypeHeaderStart value : StreamObjectTypeHeaderStart.values()) {
            String className = StreamObject.class.getPackage().getName() + "." + value.name();
            try {
                streamObjectTypeMapping.put(value,
                        Class.forName(className));
            } catch (ClassNotFoundException e) {
                //   LOGGER.info("Missing {}", className);
            }
        }
    }

    /**
     * Initializes a new instance of the StreamObject class.
     *
     * @param streamObjectType The instance of StreamObjectTypeHeaderStart.
     */
    protected StreamObject(StreamObjectTypeHeaderStart streamObjectType) {
        this.streamObjectType = streamObjectType;
    }

    /**
     * Gets the StreamObjectTypeHeaderStart
     */
    public static Set<StreamObjectTypeHeaderStart> getCompoundTypes() {
        return compoundTypes;
    }

    /**
     * Gets the StreamObjectTypeMapping
     */
    public static Map<StreamObjectTypeHeaderStart, Class> getStreamObjectTypeMapping() {
        return streamObjectTypeMapping;
    }

    /**
     * Gets the StreamObjectTypeHeaderStart.
     */
    private StreamObjectTypeHeaderStart streamObjectType;

    /**
     * Gets the length of items.
     */
    private int lengthOfItems;

    /**
     * Gets or sets the stream object header start.
     */
    private StreamObjectHeaderStart streamObjectHeaderStart;

    /**
     * Gets or sets the stream object header end.
     */
    StreamObjectHeaderEnd streamObjectHeaderEnd;

    /**
     * Get current stream object.
     *
     * @param byteArray The byte array which contains message.
     * @param index     The position where to start.
     * @return The current object instance.
     */
    public static <T extends StreamObject> T GetCurrent(byte[] byteArray, AtomicInteger index, Class<T> clazz) {
        AtomicInteger tmpIndex = new AtomicInteger(index.get());
        int length;
        AtomicReference<StreamObjectHeaderStart> streamObjectHeader = new AtomicReference<>();
        if ((length = StreamObjectHeaderStart.TryParse(byteArray, tmpIndex.get(), streamObjectHeader)) == 0) {
            throw new StreamObjectParseErrorException(tmpIndex.get(), clazz.getName(),
                    "Failed to extract either 16bit or 32bit stream object header in the current index.", null);
        }

        tmpIndex.addAndGet(length);

        StreamObject streamObject = ParseStreamObject(streamObjectHeader.get(), byteArray, tmpIndex);

        if (!streamObject.getClass().equals(clazz)) {
            String destClassName = "(null)";
            if (streamObjectTypeMapping.containsKey(streamObjectHeader.get().type)) {
                destClassName = streamObjectTypeMapping.get(streamObjectHeader.get().type).getName();
            }
            throw new StreamObjectParseErrorException(tmpIndex.get(), clazz.getName(),
                    String.format("Failed to get stream object as expect type %s, actual type is %s", clazz.getName(),
                            destClassName), null);
        }

        // Store the current index to the ref parameter index.
        index.set(tmpIndex.get());
        return (T) streamObject;
    }

    /**
     * Parse stream object from byte array.
     *
     * @param header    The instance of StreamObjectHeaderStart.
     * @param byteArray The byte array.
     * @param index     The position where to start.
     * @return The instance of StreamObject.
     */
    public static StreamObject ParseStreamObject(StreamObjectHeaderStart header, byte[] byteArray,
                                                 AtomicInteger index) {
        if (streamObjectTypeMapping.keySet().contains(header.type)) {
            Class headerTypeClass = streamObjectTypeMapping.get(header.type);
            StreamObject streamObject;
            try {
                streamObject = (StreamObject) headerTypeClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Could not instantiate class " + headerTypeClass, e);
            }

            int res = streamObject.DeserializeFromByteArray(header, byteArray, index.get());
            index.addAndGet(res);

            return streamObject;
        }

        int tmpIndex = index.get();
        tmpIndex -= header.headerType == StreamObjectHeaderStart.StreamObjectHeaderStart16bit ? 2 : 4;
        throw new StreamObjectParseErrorException(tmpIndex, "Unknown", String.format(
                "Failed to create the specified stream object instance, the type %s of stream object header in the current index is not defined",
                header.type.getIntVal()), null);
    }

    /**
     * Try to get current object, true will returned if success.
     *
     * @param byteArray    The byte array.
     * @param index        The position where to start.
     * @param streamObject The instance that want to get.
     * @return The result of whether get success.
     */

    public static <T extends StreamObject> boolean TryGetCurrent(byte[] byteArray, AtomicInteger index,
                                                                 AtomicReference<T> streamObject, Class<T> clazz) {
        AtomicInteger tmpIndex = new AtomicInteger(index.get());

        int length = 0;
        AtomicReference<StreamObjectHeaderStart> streamObjectHeader = new AtomicReference<>();
        if ((length = StreamObjectHeaderStart.TryParse(byteArray, tmpIndex.get(), streamObjectHeader)) == 0) {
            return false;
        }

        tmpIndex.addAndGet(length);
        if (streamObjectTypeMapping.containsKey(streamObjectHeader.get().type) &&
                streamObjectTypeMapping.get(streamObjectHeader.get().type).equals(clazz)) {
            streamObject.set((T) ParseStreamObject(streamObjectHeader.get(), byteArray, tmpIndex));
        } else {
            return false;
        }

        index.set(tmpIndex.get());
        return true;
    }

    /**
     * Serialize item to byte list.
     *
     * @return The byte list.
     */
    public List<Byte> SerializeToByteList() {
        List<Byte> byteList = new ArrayList<>();

        int lengthOfItems = this.SerializeItemsToByteList(byteList);

        AtomicReference<StreamObjectHeaderStart> header = new AtomicReference<>();
        if (this.streamObjectType.getIntVal() <= 0x3F && lengthOfItems <= 127) {
            header.set(new StreamObjectHeaderStart16bit(this.streamObjectType, lengthOfItems));
        } else {
            header.set(new StreamObjectHeaderStart32bit(this.streamObjectType, lengthOfItems));
        }

        byteList.addAll(0, header.get().SerializeToByteList());

        if (compoundTypes.contains(this.streamObjectType)) {
            if (this.streamObjectType.getIntVal() <= 0x3F) {
                byteList.addAll(new StreamObjectHeaderEnd8bit(this.streamObjectType.getIntVal()).SerializeToByteList());
            } else {
                byteList.addAll(
                        new StreamObjectHeaderEnd16bit(this.streamObjectType.getIntVal()).SerializeToByteList());
            }
        }

        return byteList;
    }

    /**
     * Used to return the length of this element.
     *
     * @param header     Then instance of StreamObjectHeaderStart.
     * @param byteArray  The byte list
     * @param startIndex The position where to start.
     * @return The element length
     */
    public int DeserializeFromByteArray(StreamObjectHeaderStart header, byte[] byteArray, int startIndex) {
        this.streamObjectType = header.type;
        this.lengthOfItems = header.length;

        if (header instanceof StreamObjectHeaderStart32bit) {
            if (header.length == 32767) {
                this.lengthOfItems = (int) ((StreamObjectHeaderStart32bit) header).largeLength.getDecodedValue();
            }
        }

        AtomicInteger index = new AtomicInteger(startIndex);
        this.streamObjectHeaderStart = header;
        this.DeserializeItemsFromByteArray(byteArray, index, this.lengthOfItems);

        if (compoundTypes.contains(this.streamObjectType)) {
            StreamObjectHeaderEnd end = null;
            BitReader bitReader = new BitReader(byteArray, index.get());
            int aField = bitReader.ReadInt32(2);
            if (aField == 0x1) {
                end = BasicObject.parse(byteArray, index, StreamObjectHeaderEnd8bit.class);
            }
            if (aField == 0x3) {
                end = BasicObject.parse(byteArray, index, StreamObjectHeaderEnd16bit.class);
            }

            if (end.type.getIntVal() != this.streamObjectType.getIntVal()) {
                throw new StreamObjectParseErrorException(index.get(), null,
                        "Unexpected the stream header end value " + this.streamObjectType.getIntVal(), null);
            }

            this.streamObjectHeaderEnd = end;
        }

//        // Capture all the type related requirements
//        if (SharedContext.Current.IsMsFsshttpRequirementsCaptured) {
//            new MsfsshttpbAdapterCapture().InvokeCaptureMethod(this.GetType(), this, SharedContext.Current.Site);
//        }

        return index.get() - startIndex;
    }

    /**
     * Serialize items to byte list.
     *
     * @param byteList The byte list need to serialized.
     * @return The length in bytes for additional data if the current stream object has, otherwise return 0.
     */
    protected abstract int SerializeItemsToByteList(List<Byte> byteList);

    /**
     * De-serialize items from byte array.
     *
     * @param byteArray     The byte array which contains response message.
     * @param currentIndex  The index special where to start.
     * @param lengthOfItems The length of items.
     */
    protected abstract void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                          int lengthOfItems);
}
