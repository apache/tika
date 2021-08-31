package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataElement extends StreamObject {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataElement.class);

    /// <summary>
    /// Data Element Data Type Mapping
    /// </summary>
    private static Map<DataElementType, Class> dataElementDataTypeMapping = null;

    /// <summary>
    /// Initializes static members of the DataElement class
    /// </summary>
    static {
        dataElementDataTypeMapping = new HashMap<>();
        for (DataElementType value : DataElementType.values()) {
            String className = DataElement.class.getPackage().getName() + "." + value.name();

            try {
                dataElementDataTypeMapping.put(value,
                        Class.forName(className));
            } catch (ClassNotFoundException e) {
                //      LOGGER.info("Could not find class {}", className);
            }
        }
    }

    /// <summary>
    /// Initializes a new instance of the DataElement class.
    /// </summary>
    /// <param name="type">data element type</param>
    /// <param name="data">Specifies the data of the element.</param>
    public DataElement(DataElementType type, DataElementData data) {
        super(StreamObjectTypeHeaderStart.DataElement);
        if (!dataElementDataTypeMapping.containsKey(type)) {
            throw new RuntimeException("Invalid argument type value" + type.getIntVal());
        }

        this.dataElementType = type;
        this.data = data;
        this.dataElementExGuid = new ExGuid(SequenceNumberGenerator.GetCurrentSerialNumber(), UUID.randomUUID());
        this.serialNumber = new SerialNumber(UUID.randomUUID(), SequenceNumberGenerator.GetCurrentSerialNumber());
    }

    /// <summary>
    /// Initializes a new instance of the DataElement class.
    /// </summary>
    public DataElement() {
        super(StreamObjectTypeHeaderStart.DataElement);
    }

    /// <summary>
    /// Gets or sets an extended GUID that specifies the data element.
    /// </summary>
    public ExGuid dataElementExGuid;

    /// <summary>
    /// Gets or sets a serial number that specifies the data element.
    /// </summary>
    public SerialNumber serialNumber;

    /// <summary>
    /// Gets or sets a compact unsigned 64-bit integer that specifies the value of the storage index data element type.
    /// </summary>
    public DataElementType dataElementType;

    /// <summary>
    /// Gets or sets a data element fragment.
    /// </summary>
    public DataElementData data;

    /// <summary>
    /// Used to get data.
    /// </summary>
    /// <typeparam name="T">Type of element</typeparam>
    /// <returns>Data of the element</returns>
    public <T extends DataElementData> T GetData(Class<T> clazz) {
        if (this.data.getClass().equals(clazz)) {
            return (T) this.data;
        } else {
            throw new RuntimeException(
                    String.format("Unable to cast DataElementData to the type %s, its actual type is %s",
                            clazz.getName(), this.data.getClass().getName()));
        }
    }

    /// <summary>
    /// Used to de-serialize the element.
    /// </summary>
    /// <param name="byteArray">A Byte array</param>
    /// <param name="currentIndex">Start position</param>
    /// <param name="lengthOfItems">The length of the items</param>
    @Override
    protected void DeserializeItemsFromByteArray(byte[] byteArray, AtomicInteger currentIndex, int lengthOfItems) {
        AtomicInteger index = new AtomicInteger(currentIndex.get());

        try {
            this.dataElementExGuid = BasicObject.parse(byteArray, index, ExGuid.class);
            this.serialNumber = BasicObject.parse(byteArray, index, SerialNumber.class);
            this.dataElementType = DataElementType.fromIntVal(
                    (int) BasicObject.parse(byteArray, index, Compact64bitInt.class).getDecodedValue());
        } catch (Exception e) {
            throw new DataElementParseErrorException(index.get(), e);
        }

        if (index.get() - currentIndex.get() != lengthOfItems) {
            throw new DataElementParseErrorException(currentIndex.get(),
                    "Failed to check the data element header length, whose value does not cover the dataElementExGUID, SerialNumber and DataElementType",
                    null);
        }

        if (dataElementDataTypeMapping.containsKey(this.dataElementType)) {
            try {
                this.data = (DataElementData) dataElementDataTypeMapping.get(this.dataElementType).newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                throw new RuntimeException("Could not instantiate a " + dataElementType, e);
            }

            try {
                index.addAndGet(this.data.DeserializeDataElementDataFromByteArray(byteArray, index.get()));
            } catch (Exception e) {
                throw new DataElementParseErrorException(index.get(), e);
            }
        } else {
            throw new DataElementParseErrorException(index.get(),
                    "Failed to create specific data element instance with the type " + this.dataElementType, null);
        }

        currentIndex.set(index.get());
    }

    /// <summary>
    /// Used to convert the element into a byte List.
    /// </summary>
    /// <param name="byteList">A Byte list</param>
    /// <returns>The element length</returns>
    @Override
    protected int SerializeItemsToByteList(List<Byte> byteList) {
        int startIndex = byteList.size();
        byteList.addAll(this.dataElementExGuid.SerializeToByteList());
        byteList.addAll(this.serialNumber.SerializeToByteList());
        byteList.addAll(new Compact64bitInt(this.dataElementType.getIntVal()).SerializeToByteList());

        int headerLength = byteList.size() - startIndex;
        byteList.addAll(this.data.SerializeToByteList());

        return headerLength;
    }
}