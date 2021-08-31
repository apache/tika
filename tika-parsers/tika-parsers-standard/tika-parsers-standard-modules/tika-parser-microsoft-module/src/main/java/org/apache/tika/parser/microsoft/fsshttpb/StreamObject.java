package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class StreamObject implements IFSSHTTPBSerializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamObject.class);

    /// <summary>
    /// Hash set contains the StreamObjectTypeHeaderStart type.
    /// </summary>
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

    /// <summary>
    /// The dictionary of StreamObjectTypeHeaderStart and type.
    /// </summary>
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

    /// <summary>
    /// Initializes a new instance of the StreamObject class.
    /// </summary>
    /// <param name="streamObjectType">The instance of StreamObjectTypeHeaderStart.</param>
    protected StreamObject(StreamObjectTypeHeaderStart streamObjectType) {
        this.streamObjectType = streamObjectType;
    }

    /// <summary>
    /// Gets the StreamObjectTypeHeaderStart
    /// </summary>
    public static Set<StreamObjectTypeHeaderStart> getCompoundTypes() {
        return compoundTypes;
    }

    /// <summary>
    /// Gets the StreamObjectTypeMapping
    /// </summary>
    public static Map<StreamObjectTypeHeaderStart, Class> getStreamObjectTypeMapping() {
        return streamObjectTypeMapping;
    }

    /// <summary>
    /// Gets the StreamObjectTypeHeaderStart.
    /// </summary>
    private StreamObjectTypeHeaderStart streamObjectType;

    /// <summary>
    /// Gets the length of items.
    /// </summary>
    private int lengthOfItems;

    /// <summary>
    /// Gets or sets the stream object header start.
    /// </summary>
    private StreamObjectHeaderStart streamObjectHeaderStart;

    /// <summary>
    /// Gets or sets the stream object header end.
    /// </summary>
    StreamObjectHeaderEnd streamObjectHeaderEnd;

    /// <summary>
    /// Get current stream object.
    /// </summary>
    /// <typeparam name="T">The type of target object.</typeparam>
    /// <param name="byteArray">The byte array which contains message.</param>
    /// <param name="index">The position where to start.</param>
    /// <returns>The current object instance.</returns>
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

    /// <summary>
    /// Parse stream object from byte array.
    /// </summary>
    /// <param name="header">The instance of StreamObjectHeaderStart.</param>
    /// <param name="byteArray">The byte array.</param>
    /// <param name="index">The position where to start.</param>
    /// <returns>The instance of StreamObject.</returns>
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

    /// <summary>
    /// Try to get current object, true will returned if success.
    /// </summary>
    /// <typeparam name="T">The type of target object.</typeparam>
    /// <param name="byteArray">The byte array.</param>
    /// <param name="index">The position where to start.</param>
    /// <param name="streamObject">The instance that want to get.</param>
    /// <returns>The result of whether get success.</returns>
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

    /// <summary>
    /// Serialize item to byte list.
    /// </summary>
    /// <returns>The byte list.</returns>
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

    /// <summary>
    /// Used to return the length of this element.
    /// </summary>
    /// <param name="header">Then instance of StreamObjectHeaderStart.</param>
    /// <param name="byteArray">The byte list</param>
    /// <param name="startIndex">The position where to start.</param>
    /// <returns>The element length</returns>
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

    /// <summary>
    /// Serialize items to byte list.
    /// </summary>
    /// <param name="byteList">The byte list need to serialized.</param>
    /// <returns>The length in bytes for additional data if the current stream object has, otherwise return 0.</returns>
    protected abstract int SerializeItemsToByteList(List<Byte> byteList);

    /// <summary>
    /// De-serialize items from byte array.
    /// </summary>
    /// <param name="byteArray">The byte array which contains response message.</param>
    /// <param name="currentIndex">The index special where to start.</param>
    /// <param name="lengthOfItems">The length of items.</param>
    protected abstract void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex,
                                                          int lengthOfItems);
}
