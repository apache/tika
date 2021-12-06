package org.apache.tika.parser.microsoft.fsshttpb.streamobj;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.tika.parser.microsoft.fsshttpb.exception.DataElementParseErrorException;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.BasicObject;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.Compact64bitInt;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.DataElementType;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.ExGuid;
import org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic.SerialNumber;
import org.apache.tika.parser.microsoft.fsshttpb.util.SequenceNumberGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataElement extends StreamObject {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataElement.class);

    /**
     * Data Element Data Type Mapping
     */
    private static Map<DataElementType, Class> dataElementDataTypeMapping;

    /**
     *  Initializes static members of the DataElement class
     */
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

    /**
     * Initializes a new instance of the DataElement class.
     *
     * @param type data
     *             element type
     *             *
     * @param data Specifies
     *             the data
     *             of the
     *             element .
     */


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

    /**
     * Initializes a new instance of the DataElement class.
     */
    public DataElement() {
        super(StreamObjectTypeHeaderStart.DataElement);
    }

    /**
     * Gets or sets an extended GUID that specifies the data element.
     */
    public ExGuid dataElementExGuid;

    /**
     * Gets or sets a serial number that specifies the data element.
     */
    public SerialNumber serialNumber;

    /**
     * Gets or sets a compact unsigned 64-bit integer that specifies the value of the storage index data element type.
     */
    public DataElementType dataElementType;

    /**
     * Gets or sets a data element fragment.
     */
    public DataElementData data;

    /**
     * Used to get data.
     *
     * @return Data of
     * the element
     */
    public <T extends DataElementData> T GetData(Class<T> clazz) {
        if (this.data.getClass().equals(clazz)) {
            return (T) this.data;
        } else {
            throw new RuntimeException(
                    String.format("Unable to cast DataElementData to the type %s, its actual type is %s",
                            clazz.getName(), this.data.getClass().getName()));
        }
    }

    /**
     * Used to de-serialize the element.
     *
     * @param byteArray     A
     *                      Byte array
     * @param currentIndex  Start
     *                      position
     * @param lengthOfItems The
     *                      length of
     *                      the items
     */
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

    /**
     * Used to convert the element into a byte List.
     *
     * @param byteList A Byte list
     * @return The element length
     */
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