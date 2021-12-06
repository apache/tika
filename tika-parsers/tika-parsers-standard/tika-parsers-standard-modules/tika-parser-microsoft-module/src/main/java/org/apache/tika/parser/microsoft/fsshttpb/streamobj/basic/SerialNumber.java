package org.apache.tika.parser.microsoft.fsshttpb.streamobj.basic;

import java.util.List;
import java.util.UUID;

import org.apache.tika.parser.microsoft.fsshttpb.util.BitReader;
import org.apache.tika.parser.microsoft.fsshttpb.util.BitWriter;
import org.apache.tika.parser.microsoft.fsshttpb.util.GuidUtil;

public class SerialNumber extends BasicObject {
    /**
     * Initializes a new instance of the SerialNumber class with specified values.
     *
     * @param identifier Specify the Guid value of the serialNumber.
     * @param value      Specify the value of the serialNumber.
     */
    public SerialNumber(UUID identifier, long value) {
        this.guid = identifier;
        this.value = value;
    }

    /**
     * Initializes a new instance of the SerialNumber class, this is the copy constructor.
     *
     * @param sn Specify the serial number where copy from.
     */
    public SerialNumber(SerialNumber sn) {
        this.guid = sn.guid;
        this.value = sn.value;
    }

    /**
     * Initializes a new instance of the SerialNumber class, this is default contractor
     */
    public SerialNumber() {
    }

    /**
     * Gets or sets a value which indicate the SerialNumber type.
     */
    public int type;

    /**
     * Gets or sets a GUID that specifies the item.
     */
    public UUID guid;

    /**
     * Gets or sets an unsigned integer that specifies the value of the serial number.
     */
    public long value;

    /**
     * This method is used to convert the element of SerialNumber basic object into a byte List.
     *
     * @return Return the byte list which store the byte information of SerialNumber.
     */
    @Override
    public List<Byte> SerializeToByteList() {
        BitWriter bitWriter = null;
        if (this.guid.equals(GuidUtil.emptyGuid())) {
            bitWriter = new BitWriter(1);
            bitWriter.AppendUInit32(0, 8);
        } else {
            bitWriter = new BitWriter(25);
            bitWriter.AppendUInit32(128, 8);
            bitWriter.AppendGUID(this.guid);
            bitWriter.AppendUInt64(this.value, 64);
        }

        return bitWriter.getByteList();
    }

    /**
     * This method is used to deserialize the SerialNumber basic object from the specified byte array and start index.
     *
     * @param byteArray  Specify the byte array.
     * @param startIndex Specify the start index from the byte array.
     * @return Return the length in byte of the SerialNumber basic object.
     */
    @Override
    protected int DoDeserializeFromByteArray(byte[] byteArray, int startIndex) // return the length consumed
    {
        BitReader bitField = new BitReader(byteArray, startIndex);
        int type = bitField.ReadInt32(8);

        if (type == 0) {
            this.guid = GuidUtil.emptyGuid();
            this.type = 0;

            return 1;
        } else if (type == 128) {
            this.guid = bitField.ReadGuid();
            this.value = bitField.ReadUInt64(64);
            this.type = 128;
            return 25;
        } else {
            throw new RuntimeException(
                    "Failed to parse SerialNumber object, Expect the type value is either 0 or 128, but the actual value is " +
                            this.type);
        }
    }
}